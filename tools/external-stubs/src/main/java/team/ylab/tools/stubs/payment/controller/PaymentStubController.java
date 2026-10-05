package team.ylab.tools.stubs.payment.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team.ylab.tools.stubs.payment.dto.PaymentAuthorizeRequest;
import team.ylab.tools.stubs.payment.dto.PaymentAuthorizeResponse;
import team.ylab.tools.stubs.payment.dto.PaymentScenarioRequest;
import team.ylab.tools.stubs.payment.service.PaymentStubService;

@RestController
@RequestMapping("/api/stubs/payments")
@RequiredArgsConstructor
public class PaymentStubController {

  private final PaymentStubService paymentStubService;

  @PostMapping("/authorize")
  public ResponseEntity<PaymentAuthorizeResponse> authorize(
      @Valid @RequestBody PaymentAuthorizeRequest request) {
    PaymentAuthorizeResponse response = paymentStubService.authorize(request);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/scenarios")
  @ResponseStatus(HttpStatus.OK)
  public Map<String, Object> configureScenario(@RequestBody PaymentScenarioRequest request) {
    paymentStubService.configureScenario(request);
    return paymentStubService.getScenarios();
  }

  @GetMapping("/scenarios")
  public Map<String, Object> getScenarios() {
    return paymentStubService.getScenarios();
  }

  @DeleteMapping("/scenarios")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetScenarios() {
    paymentStubService.resetScenarios();
  }

  @GetMapping("/history")
  public List<PaymentAuthorizeResponse> getHistory() {
    return paymentStubService.getHistory();
  }
}
