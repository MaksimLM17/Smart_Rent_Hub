package team.ylab.tools.stubs.bankid.controller;

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
import team.ylab.tools.stubs.bankid.dto.BankIdScenarioRequest;
import team.ylab.tools.stubs.bankid.dto.BankIdVerificationRequest;
import team.ylab.tools.stubs.bankid.dto.BankIdVerificationResponse;
import team.ylab.tools.stubs.bankid.service.BankIdStubService;

@RestController
@RequestMapping("/api/stubs/bank-id")
@RequiredArgsConstructor
public class BankIdStubController {

  private final BankIdStubService bankIdStubService;

  @PostMapping("/verify")
  public ResponseEntity<BankIdVerificationResponse> verify(
      @Valid @RequestBody BankIdVerificationRequest request) {
    BankIdVerificationResponse response = bankIdStubService.verify(request);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/scenarios")
  @ResponseStatus(HttpStatus.OK)
  public Map<String, Object> configureScenario(@RequestBody BankIdScenarioRequest request) {
    bankIdStubService.configureScenario(request);
    return bankIdStubService.getScenarios();
  }

  @GetMapping("/scenarios")
  public Map<String, Object> getScenarios() {
    return bankIdStubService.getScenarios();
  }

  @DeleteMapping("/scenarios")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetScenarios() {
    bankIdStubService.resetScenarios();
  }

  @GetMapping("/history")
  public List<BankIdVerificationResponse> getHistory() {
    return bankIdStubService.getHistory();
  }
}
