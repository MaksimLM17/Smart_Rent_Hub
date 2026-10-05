package team.ylab.tools.stubs.bankid.service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import team.ylab.tools.stubs.bankid.dto.BankIdScenarioRequest;
import team.ylab.tools.stubs.bankid.dto.BankIdVerificationRequest;
import team.ylab.tools.stubs.bankid.dto.BankIdVerificationResponse;

@Slf4j
@Service
public class BankIdStubService {

  private static final int MAX_HISTORY_SIZE = 500;

  private final Map<String, BankIdScenarioRequest> clientScenarios = new ConcurrentHashMap<>();
  private volatile BankIdScenarioRequest defaultScenario =
      BankIdScenarioRequest.builder()
          .status("VERIFIED")
          .delayMs(0)
          .reason("Identity verified successfully")
          .score(0.98)
          .build();

  private final Deque<BankIdVerificationResponse> history = new ArrayDeque<>();

  public BankIdVerificationResponse verify(BankIdVerificationRequest request) {
    BankIdScenarioRequest scenario =
        clientScenarios.getOrDefault(request.getClientId(), defaultScenario);

    if (scenario.getDelayMs() > 0) {
      try {
        log.info(
            "[BANK-ID-STUB] Simulating delay of {} ms for clientId: {}",
            scenario.getDelayMs(),
            request.getClientId());
        Thread.sleep(scenario.getDelayMs());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Delay interrupted", e);
      }
    }

    if ("TIMEOUT".equalsIgnoreCase(scenario.getStatus())) {
      log.warn("[BANK-ID-STUB] Simulating Gateway Timeout for clientId: {}", request.getClientId());
      throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Bank ID Provider Timed Out");
    }

    if ("ERROR".equalsIgnoreCase(scenario.getStatus())) {
      log.warn(
          "[BANK-ID-STUB] Simulating Internal Server Error for clientId: {}",
          request.getClientId());
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Bank ID Provider Internal Error");
    }

    BankIdVerificationResponse response =
        BankIdVerificationResponse.builder()
            .verificationId(UUID.randomUUID().toString())
            .clientId(request.getClientId())
            .status(scenario.getStatus().toUpperCase())
            .reason(
                scenario.getReason() != null
                    ? scenario.getReason()
                    : "Scenario: " + scenario.getStatus())
            .score(scenario.getScore())
            .verifiedAt(Instant.now())
            .build();

    synchronized (history) {
      if (history.size() >= MAX_HISTORY_SIZE) {
        history.removeFirst();
      }
      history.addLast(response);
    }

    log.info(
        "============================================================"
            + "\n[BANK-ID-STUB] VERIFICATION COMPLETED"
            + "\n  ClientId:   {}"
            + "\n  Status:     {}"
            + "\n  Reason:     {}"
            + "\n  Score:      {}"
            + "\n  Time:       {}"
            + "\n============================================================",
        response.getClientId(),
        response.getStatus(),
        response.getReason(),
        response.getScore(),
        response.getVerifiedAt());

    return response;
  }

  public void configureScenario(BankIdScenarioRequest request) {
    if (request.getClientId() != null && !request.getClientId().isBlank()) {
      clientScenarios.put(request.getClientId(), request);
      log.info(
          "[BANK-ID-STUB] Scenario configured for client {}: status={}, delay={}ms",
          request.getClientId(),
          request.getStatus(),
          request.getDelayMs());
    } else {
      defaultScenario = request;
      log.info(
          "[BANK-ID-STUB] Default scenario configured: status={}, delay={}ms",
          request.getStatus(),
          request.getDelayMs());
    }
  }

  public Map<String, Object> getScenarios() {
    Map<String, Object> map = new ConcurrentHashMap<>();
    map.put("default", defaultScenario);
    map.put("clients", clientScenarios);
    return map;
  }

  public void resetScenarios() {
    clientScenarios.clear();
    defaultScenario =
        BankIdScenarioRequest.builder()
            .status("VERIFIED")
            .delayMs(0)
            .reason("Identity verified successfully")
            .score(0.98)
            .build();
    log.info("[BANK-ID-STUB] Reset scenarios to default");
  }

  public List<BankIdVerificationResponse> getHistory() {
    synchronized (history) {
      return new ArrayList<>(history);
    }
  }
}
