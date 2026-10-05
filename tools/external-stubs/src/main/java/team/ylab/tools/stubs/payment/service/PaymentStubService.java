package team.ylab.tools.stubs.payment.service;

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
import team.ylab.tools.stubs.payment.dto.PaymentAuthorizeRequest;
import team.ylab.tools.stubs.payment.dto.PaymentAuthorizeResponse;
import team.ylab.tools.stubs.payment.dto.PaymentScenarioRequest;

@Slf4j
@Service
public class PaymentStubService {

  private static final int MAX_HISTORY_SIZE = 500;

  private final Map<String, PaymentScenarioRequest> bookingScenarios = new ConcurrentHashMap<>();
  private volatile PaymentScenarioRequest defaultScenario =
      PaymentScenarioRequest.builder()
          .status("SUCCESS")
          .delayMs(0)
          .message("Payment authorized successfully")
          .build();

  private final Deque<PaymentAuthorizeResponse> history = new ArrayDeque<>();

  public PaymentAuthorizeResponse authorize(PaymentAuthorizeRequest request) {
    PaymentScenarioRequest scenario =
        bookingScenarios.getOrDefault(request.getBookingId(), defaultScenario);

    if (scenario.getDelayMs() > 0) {
      try {
        log.info(
            "[PAYMENT-STUB] Simulating delay of {} ms for bookingId: {}",
            scenario.getDelayMs(),
            request.getBookingId());
        Thread.sleep(scenario.getDelayMs());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Delay interrupted", e);
      }
    }

    if ("TIMEOUT".equalsIgnoreCase(scenario.getStatus())) {
      log.warn(
          "[PAYMENT-STUB] Simulating Gateway Timeout for bookingId: {}", request.getBookingId());
      throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Payment Gateway Timed Out");
    }

    String paymentId =
        request.getPaymentId() != null ? request.getPaymentId() : UUID.randomUUID().toString();

    PaymentAuthorizeResponse response =
        PaymentAuthorizeResponse.builder()
            .paymentId(paymentId)
            .bookingId(request.getBookingId())
            .transactionId("txn_" + UUID.randomUUID().toString().substring(0, 8))
            .status(scenario.getStatus().toUpperCase())
            .threeDsRedirectUrl(
                "THREE_DS_REQUIRED".equalsIgnoreCase(scenario.getStatus())
                    ? (scenario.getThreeDsRedirectUrl() != null
                        ? scenario.getThreeDsRedirectUrl()
                        : "https://pay.example.com/3ds/" + paymentId)
                    : null)
            .message(
                scenario.getMessage() != null
                    ? scenario.getMessage()
                    : "Status: " + scenario.getStatus())
            .timestamp(Instant.now())
            .build();

    synchronized (history) {
      if (history.size() >= MAX_HISTORY_SIZE) {
        history.removeFirst();
      }
      history.addLast(response);
    }

    log.info(
        "============================================================"
            + "\n[PAYMENT-STUB] PAYMENT AUTHORIZATION"
            + "\n  PaymentId:     {}"
            + "\n  BookingId:     {}"
            + "\n  Amount:        {} {}"
            + "\n  Status:        {}"
            + "\n  TransactionId: {}"
            + "\n  Message:       {}"
            + "\n============================================================",
        response.getPaymentId(),
        response.getBookingId(),
        request.getAmount(),
        request.getCurrency(),
        response.getStatus(),
        response.getTransactionId(),
        response.getMessage());

    return response;
  }

  public void configureScenario(PaymentScenarioRequest request) {
    if (request.getBookingId() != null && !request.getBookingId().isBlank()) {
      bookingScenarios.put(request.getBookingId(), request);
      log.info(
          "[PAYMENT-STUB] Scenario configured for booking {}: status={}, delay={}ms",
          request.getBookingId(),
          request.getStatus(),
          request.getDelayMs());
    } else {
      defaultScenario = request;
      log.info(
          "[PAYMENT-STUB] Default scenario configured: status={}, delay={}ms",
          request.getStatus(),
          request.getDelayMs());
    }
  }

  public Map<String, Object> getScenarios() {
    Map<String, Object> map = new ConcurrentHashMap<>();
    map.put("default", defaultScenario);
    map.put("bookings", bookingScenarios);
    return map;
  }

  public void resetScenarios() {
    bookingScenarios.clear();
    defaultScenario =
        PaymentScenarioRequest.builder()
            .status("SUCCESS")
            .delayMs(0)
            .message("Payment authorized successfully")
            .build();
    log.info("[PAYMENT-STUB] Reset scenarios to default");
  }

  public List<PaymentAuthorizeResponse> getHistory() {
    synchronized (history) {
      return new ArrayList<>(history);
    }
  }
}
