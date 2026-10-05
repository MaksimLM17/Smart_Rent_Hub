package team.ylab.spikes.s1.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import team.ylab.spikes.s1.entity.SpikeEntity;
import team.ylab.spikes.s1.repository.SpikeRepository;

@Slf4j
@Component("taskBDelegate")
@RequiredArgsConstructor
public class TaskBDelegate implements JavaDelegate {

  private final SpikeRepository repository;

  @Override
  public void execute(DelegateExecution execution) {
    String businessKey = execution.getProcessBusinessKey();
    Boolean simulateError = (Boolean) execution.getVariable("simulateError");
    log.info("Executing Task B for businessKey={}, simulateError={}", businessKey, simulateError);

    SpikeEntity entity =
        repository
            .findByBusinessKey(businessKey)
            .orElseThrow(
                () -> new RuntimeException("Entity not found for businessKey: " + businessKey));

    entity.setStatus("TASK_B_COMPLETED");
    repository.save(entity);

    if (Boolean.TRUE.equals(simulateError)) {
      throw new RuntimeException("Simulated error for atomicity test");
    }
  }
}
