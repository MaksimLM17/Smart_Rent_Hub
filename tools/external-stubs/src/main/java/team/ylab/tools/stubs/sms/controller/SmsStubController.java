package team.ylab.tools.stubs.sms.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team.ylab.tools.stubs.sms.dto.SmsMessageResponse;
import team.ylab.tools.stubs.sms.dto.SmsScenarioRequest;
import team.ylab.tools.stubs.sms.dto.SmsSendRequest;
import team.ylab.tools.stubs.sms.service.SmsStubService;

@RestController
@RequestMapping("/api/stubs/sms")
@RequiredArgsConstructor
public class SmsStubController {

  private final SmsStubService smsStubService;

  @PostMapping("/send")
  @ResponseStatus(HttpStatus.CREATED)
  public SmsMessageResponse sendSms(@Valid @RequestBody SmsSendRequest request) {
    return smsStubService.sendSms(request);
  }

  @GetMapping("/last")
  public ResponseEntity<SmsMessageResponse> getLastSms(
      @RequestParam(name = "phoneNumber", required = false) String phoneNumber) {
    return smsStubService
        .getLastSms(phoneNumber)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/history")
  public List<SmsMessageResponse> getHistory(
      @RequestParam(name = "phoneNumber", required = false) String phoneNumber) {
    return smsStubService.getHistory(phoneNumber);
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void clear() {
    smsStubService.clear();
  }

  @PostMapping("/scenario")
  public ResponseEntity<SmsScenarioRequest> setScenario(
      @RequestBody SmsScenarioRequest scenarioRequest) {
    smsStubService.setScenario(scenarioRequest);
    return ResponseEntity.ok(smsStubService.getScenario());
  }

  @GetMapping("/scenario")
  public SmsScenarioRequest getScenario() {
    return smsStubService.getScenario();
  }
}
