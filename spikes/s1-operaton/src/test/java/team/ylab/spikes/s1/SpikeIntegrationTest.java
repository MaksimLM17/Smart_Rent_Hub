package team.ylab.spikes.s1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.operaton.bpm.engine.ManagementService;
import org.operaton.bpm.engine.MismatchingMessageCorrelationException;
import org.operaton.bpm.engine.OptimisticLockingException;
import org.operaton.bpm.engine.RepositoryService;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.runtime.EventSubscription;
import org.operaton.bpm.engine.runtime.Job;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import team.ylab.spikes.s1.entity.SpikeEntity;
import team.ylab.spikes.s1.repository.SpikeRepository;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SpikeIntegrationTest {

  private static final Logger log = LoggerFactory.getLogger(SpikeIntegrationTest.class);

  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    postgres.start();
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    try (Connection conn =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE SCHEMA IF NOT EXISTS bpm;");
    } catch (SQLException e) {
      throw new RuntimeException("Failed to initialize bpm schema", e);
    }

    registry.add("spring.datasource.url", () -> postgres.getJdbcUrl() + "&currentSchema=bpm");
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
  }

  @Autowired private RuntimeService runtimeService;

  @Autowired private ManagementService managementService;

  @Autowired private RepositoryService repositoryService;

  @Autowired private SpikeRepository repository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void ensureProcessDeployed() {
    if (repositoryService
            .createProcessDefinitionQuery()
            .processDefinitionKey("checkout-mini")
            .count()
        == 0) {
      repositoryService
          .createDeployment()
          .name("checkout-mini-deployment")
          .addClasspathResource("processes/checkout-mini.bpmn")
          .deploy();
    }
  }

  @Test
  @Order(1)
  @DisplayName("Verify startup time and memory footprint")
  void verifyStartupMetrics() {
    long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
    Runtime runtime = Runtime.getRuntime();
    long totalMemory = runtime.totalMemory() / (1024 * 1024);
    long freeMemory = runtime.freeMemory() / (1024 * 1024);
    long usedMemory = totalMemory - freeMemory;

    log.info("=== SPIKE S-1 STARTUP & MEMORY METRICS ===");
    log.info("JVM Uptime: {} ms", uptime);
    log.info("Total Heap Memory: {} MB", totalMemory);
    log.info("Used Heap Memory: {} MB", usedMemory);
    log.info("Free Heap Memory: {} MB", freeMemory);
    log.info("==========================================");

    assertThat(uptime).isGreaterThan(0);
    assertThat(usedMemory).isGreaterThan(0);
  }

  @Test
  @Order(2)
  @DisplayName("Verify PostgreSQL schema 'bpm' contains application and Operaton tables")
  void verifyBpmSchemaTables() {
    List<String> tables =
        jdbcTemplate.query(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'bpm'",
            (rs, rowNum) -> rs.getString("table_name").toLowerCase());

    log.info("Tables found in 'bpm' schema: {}", tables);

    assertThat(tables).contains("spike_entity");
    assertThat(tables).anyMatch(t -> t.startsWith("act_ru_"));
    assertThat(tables).anyMatch(t -> t.startsWith("act_ge_"));
    assertThat(tables).anyMatch(t -> t.startsWith("act_re_"));
  }

  @Test
  @Order(3)
  @DisplayName("Verify (a) job executor executes asyncBefore on Service Task A")
  void verifyAsyncBeforeExecution() {
    String businessKey = "async-test-key-1";

    ProcessInstance pi = runtimeService.startProcessInstanceByKey("checkout-mini", businessKey);
    assertThat(pi).isNotNull();

    // With asyncBefore="true", a job should be created for Service Task A
    Job job = managementService.createJobQuery().processInstanceId(pi.getId()).singleResult();

    assertThat(job).isNotNull();

    // Before job executes, entity should not yet exist
    Optional<SpikeEntity> entityBefore = repository.findByBusinessKey(businessKey);
    assertThat(entityBefore).isEmpty();

    // Execute the job explicitly via managementService
    managementService.executeJob(job.getId());

    // After job execution, entity should be saved as TASK_A_COMPLETED
    Optional<SpikeEntity> entityAfter = repository.findByBusinessKey(businessKey);
    assertThat(entityAfter).isPresent();
    assertThat(entityAfter.get().getStatus()).isEqualTo("TASK_A_COMPLETED");

    // Process token should now be waiting at Event-Based Gateway (message subscription active)
    EventSubscription msgSub =
        runtimeService
            .createEventSubscriptionQuery()
            .processInstanceId(pi.getId())
            .eventName("msg_x")
            .singleResult();
    assertThat(msgSub).isNotNull();
  }

  @Test
  @Order(4)
  @DisplayName(
      "Verify (b) atomicity: Task B failure rolls back JPA and Operaton token remains at wait state")
  void verifyAtomicityRollback() {
    String businessKey = "atomicity-test-key-1";

    ProcessInstance pi = runtimeService.startProcessInstanceByKey("checkout-mini", businessKey);
    Job job = managementService.createJobQuery().processInstanceId(pi.getId()).singleResult();
    assertThat(job).isNotNull();
    managementService.executeJob(job.getId());

    // Verify entity is in TASK_A_COMPLETED
    SpikeEntity entity = repository.findByBusinessKey(businessKey).orElseThrow();
    assertThat(entity.getStatus()).isEqualTo("TASK_A_COMPLETED");

    // Trigger message correlation with simulateError = true
    assertThrows(
        RuntimeException.class,
        () -> {
          runtimeService
              .createMessageCorrelation("msg_x")
              .processInstanceBusinessKey(businessKey)
              .setVariable("simulateError", true)
              .correlate();
        });

    // Verify JPA transaction was rolled back: status is STILL TASK_A_COMPLETED
    SpikeEntity entityAfterRollback = repository.findByBusinessKey(businessKey).orElseThrow();
    assertThat(entityAfterRollback.getStatus()).isEqualTo("TASK_A_COMPLETED");

    // Verify Operaton transaction was rolled back: process instance is still waiting at Gateway
    EventSubscription msgSub =
        runtimeService
            .createEventSubscriptionQuery()
            .processInstanceId(pi.getId())
            .eventName("msg_x")
            .singleResult();
    assertThat(msgSub).isNotNull();

    // Now correlate successfully with simulateError = false
    runtimeService
        .createMessageCorrelation("msg_x")
        .processInstanceBusinessKey(businessKey)
        .setVariable("simulateError", false)
        .correlate();

    // Entity status should now be updated to TASK_B_COMPLETED
    SpikeEntity entityCompleted = repository.findByBusinessKey(businessKey).orElseThrow();
    assertThat(entityCompleted.getStatus()).isEqualTo("TASK_B_COMPLETED");

    // Process instance should have completed
    ProcessInstance piEnded =
        runtimeService.createProcessInstanceQuery().processInstanceId(pi.getId()).singleResult();
    assertThat(piEnded).isNull();
  }

  @Test
  @Order(5)
  @DisplayName("Verify (c) message correlation by businessKey")
  void verifyMessageCorrelationByBusinessKey() {
    String bk1 = "corr-key-1";
    String bk2 = "corr-key-2";

    ProcessInstance pi1 = runtimeService.startProcessInstanceByKey("checkout-mini", bk1);
    Job job1 = managementService.createJobQuery().processInstanceId(pi1.getId()).singleResult();
    managementService.executeJob(job1.getId());

    ProcessInstance pi2 = runtimeService.startProcessInstanceByKey("checkout-mini", bk2);
    Job job2 = managementService.createJobQuery().processInstanceId(pi2.getId()).singleResult();
    managementService.executeJob(job2.getId());

    // Correlate message only to bk1
    runtimeService
        .createMessageCorrelation("msg_x")
        .processInstanceBusinessKey(bk1)
        .setVariable("simulateError", false)
        .correlate();

    // bk1 entity is TASK_B_COMPLETED, process ended
    SpikeEntity entity1 = repository.findByBusinessKey(bk1).orElseThrow();
    assertThat(entity1.getStatus()).isEqualTo("TASK_B_COMPLETED");
    assertThat(
            runtimeService
                .createProcessInstanceQuery()
                .processInstanceId(pi1.getId())
                .singleResult())
        .isNull();

    // bk2 entity is still TASK_A_COMPLETED, process still active
    SpikeEntity entity2 = repository.findByBusinessKey(bk2).orElseThrow();
    assertThat(entity2.getStatus()).isEqualTo("TASK_A_COMPLETED");
    assertThat(
            runtimeService
                .createProcessInstanceQuery()
                .processInstanceId(pi2.getId())
                .singleResult())
        .isNotNull();

    // Clean up bk2
    runtimeService
        .createMessageCorrelation("msg_x")
        .processInstanceBusinessKey(bk2)
        .setVariable("simulateError", false)
        .correlate();
  }

  @Test
  @Order(6)
  @DisplayName("Verify (d) race condition handling on message correlation")
  void verifyRaceConditionHandling() throws InterruptedException {
    String businessKey = "race-key-1";

    ProcessInstance pi = runtimeService.startProcessInstanceByKey("checkout-mini", businessKey);
    Job job = managementService.createJobQuery().processInstanceId(pi.getId()).singleResult();
    managementService.executeJob(job.getId());

    int threadCount = 4;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger exceptionCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
      executor.submit(
          () -> {
            try {
              startLatch.await();
              runtimeService
                  .createMessageCorrelation("msg_x")
                  .processInstanceBusinessKey(businessKey)
                  .setVariable("simulateError", false)
                  .correlate();
              successCount.incrementAndGet();
            } catch (MismatchingMessageCorrelationException | OptimisticLockingException e) {
              exceptionCount.incrementAndGet();
            } catch (Exception e) {
              log.warn("Other exception during concurrent correlation: {}", e.getMessage());
              exceptionCount.incrementAndGet();
            } finally {
              doneLatch.countDown();
            }
          });
    }

    // Fire all threads simultaneously
    startLatch.countDown();
    boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
    executor.shutdown();

    assertThat(finished).isTrue();
    // Exactly one should succeed, others should receive mismatching or optimistic locking exception
    assertThat(successCount.get()).isEqualTo(1);
    assertThat(exceptionCount.get()).isEqualTo(threadCount - 1);

    // Subsequent message correlation must also throw MismatchingMessageCorrelationException
    assertThrows(
        MismatchingMessageCorrelationException.class,
        () -> {
          runtimeService
              .createMessageCorrelation("msg_x")
              .processInstanceBusinessKey(businessKey)
              .correlate();
        });
  }

  @Test
  @Order(7)
  @DisplayName("Verify (e) timer execution via event-based gateway")
  void verifyTimerExecution() {
    String businessKey = "timer-test-key-1";

    ProcessInstance pi = runtimeService.startProcessInstanceByKey("checkout-mini", businessKey);
    Job jobA = managementService.createJobQuery().processInstanceId(pi.getId()).singleResult();
    managementService.executeJob(jobA.getId());

    // Process is at Gateway. There should be a timer job
    Job timerJob =
        managementService.createJobQuery().processInstanceId(pi.getId()).timers().singleResult();

    assertThat(timerJob).isNotNull();

    // Execute the timer job (simulating timer expiration)
    managementService.executeJob(timerJob.getId());

    // Process proceeds through Task B
    SpikeEntity entity = repository.findByBusinessKey(businessKey).orElseThrow();
    assertThat(entity.getStatus()).isEqualTo("TASK_B_COMPLETED");

    // Process instance should be completed
    ProcessInstance piEnded =
        runtimeService.createProcessInstanceQuery().processInstanceId(pi.getId()).singleResult();
    assertThat(piEnded).isNull();
  }
}
