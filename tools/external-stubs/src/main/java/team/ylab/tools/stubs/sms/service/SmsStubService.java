package team.ylab.tools.stubs.sms.service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import team.ylab.tools.stubs.sms.dto.SmsMessageResponse;
import team.ylab.tools.stubs.sms.dto.SmsScenarioRequest;
import team.ylab.tools.stubs.sms.dto.SmsSendRequest;

@Slf4j
@Service
public class SmsStubService {

  private static final Pattern CODE_PATTERN = Pattern.compile("\\b(\\d{4,6})\\b");
  private static final int MAX_HISTORY_SIZE = 500;

  private final Deque<SmsMessageResponse> history = new ArrayDeque<>();
  private volatile SmsScenarioRequest activeScenario =
      SmsScenarioRequest.builder().scenario("SUCCESS").delayMs(0).build();

  public synchronized SmsMessageResponse sendSms(SmsSendRequest request) {
    applyScenario();

    String extractedCode = request.getCode();
    if (extractedCode == null && request.getMessage() != null) {
      Matcher matcher = CODE_PATTERN.matcher(request.getMessage());
      if (matcher.find()) {
        extractedCode = matcher.group(1);
      }
    }

    SmsMessageResponse response =
        SmsMessageResponse.builder()
            .messageId(UUID.randomUUID().toString())
            .phoneNumber(request.getPhoneNumber())
            .message(request.getMessage())
            .code(extractedCode)
            .sentAt(Instant.now())
            .status("SENT")
            .build();

    if (history.size() >= MAX_HISTORY_SIZE) {
      history.removeFirst();
    }
    history.addLast(response);

    log.info(
        "============================================================"
            + "\n[SMS-GATEWAY-STUB] OUTGOING SMS"
            + "\n  To:      {}"
            + "\n  Code:    {}"
            + "\n  Message: {}"
            + "\n  Time:    {}"
            + "\n============================================================",
        response.getPhoneNumber(),
        response.getCode() != null ? response.getCode() : "(none)",
        response.getMessage() != null ? response.getMessage() : "(none)",
        response.getSentAt());

    return response;
  }

  public synchronized Optional<SmsMessageResponse> getLastSms(String phoneNumber) {
    List<SmsMessageResponse> list = new ArrayList<>(history);
    for (int i = list.size() - 1; i >= 0; i--) {
      SmsMessageResponse msg = list.get(i);
      if (phoneNumber == null
          || phoneNumber.isBlank()
          || phoneNumber.equals(msg.getPhoneNumber())) {
        return Optional.of(msg);
      }
    }
    return Optional.empty();
  }

  public synchronized List<SmsMessageResponse> getHistory(String phoneNumber) {
    List<SmsMessageResponse> result = new ArrayList<>();
    List<SmsMessageResponse> list = new ArrayList<>(history);
    for (int i = list.size() - 1; i >= 0; i--) {
      SmsMessageResponse msg = list.get(i);
      if (phoneNumber == null
          || phoneNumber.isBlank()
          || phoneNumber.equals(msg.getPhoneNumber())) {
        result.add(msg);
      }
    }
    return result;
  }

  public synchronized void clear() {
    history.clear();
    log.info("[SMS-GATEWAY-STUB] SMS history cleared");
  }

  public void setScenario(SmsScenarioRequest scenarioRequest) {
    this.activeScenario = scenarioRequest;
    log.info("[SMS-GATEWAY-STUB] Scenario updated: {}", scenarioRequest);
  }

  public SmsScenarioRequest getScenario() {
    return activeScenario;
  }

  private void applyScenario() {
    SmsScenarioRequest current = this.activeScenario;
    if (current == null) {
      return;
    }

    if (current.getDelayMs() > 0) {
      try {
        log.info("[SMS-GATEWAY-STUB] Simulating delay of {} ms", current.getDelayMs());
        Thread.sleep(current.getDelayMs());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Delay interrupted", e);
      }
    }

    if ("FAILURE".equalsIgnoreCase(current.getScenario())) {
      int statusCode = current.getFailureStatusCode() > 0 ? current.getFailureStatusCode() : 500;
      String message =
          current.getErrorMessage() != null
              ? current.getErrorMessage()
              : "Simulated SMS Gateway failure";
      log.warn("[SMS-GATEWAY-STUB] Simulating failure with status {}: {}", statusCode, message);
      throw new ResponseStatusException(HttpStatus.valueOf(statusCode), message);
    }
  }
}
