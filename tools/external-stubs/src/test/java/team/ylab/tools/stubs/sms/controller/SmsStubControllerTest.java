package team.ylab.tools.stubs.sms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import team.ylab.tools.stubs.sms.dto.SmsScenarioRequest;
import team.ylab.tools.stubs.sms.dto.SmsSendRequest;
import team.ylab.tools.stubs.sms.service.SmsStubService;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class SmsStubControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private SmsStubService smsStubService;

  @BeforeEach
  void setUp() {
    smsStubService.clear();
    smsStubService.setScenario(SmsScenarioRequest.builder().scenario("SUCCESS").delayMs(0).build());
  }

  @Test
  void shouldSendAndRetrieveSms() throws Exception {
    SmsSendRequest sendRequest =
        SmsSendRequest.builder()
            .phoneNumber("+79991112233")
            .message("Your verification code is 849201")
            .build();

    mockMvc
        .perform(
            post("/api/stubs/sms/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sendRequest)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.phoneNumber").value("+79991112233"))
        .andExpect(jsonPath("$.code").value("849201"))
        .andExpect(jsonPath("$.status").value("SENT"));

    mockMvc
        .perform(get("/api/stubs/sms/last").param("phoneNumber", "+79991112233"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("849201"));

    mockMvc
        .perform(get("/api/stubs/sms/history").param("phoneNumber", "+79991112233"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void shouldReturn404WhenNoSmsFound() throws Exception {
    mockMvc
        .perform(get("/api/stubs/sms/last").param("phoneNumber", "+79990000000"))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldClearSmsHistory() throws Exception {
    SmsSendRequest sendRequest =
        SmsSendRequest.builder()
            .phoneNumber("+79991112233")
            .message("Code 123456")
            .code("123456")
            .build();

    mockMvc
        .perform(
            post("/api/stubs/sms/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sendRequest)))
        .andExpect(status().isCreated());

    mockMvc.perform(delete("/api/stubs/sms")).andExpect(status().isNoContent());

    assertThat(smsStubService.getHistory(null)).isEmpty();
  }

  @Test
  void shouldSimulateFailureScenario() throws Exception {
    SmsScenarioRequest scenario =
        SmsScenarioRequest.builder()
            .scenario("FAILURE")
            .failureStatusCode(503)
            .errorMessage("SMS Gateway unavailable")
            .build();

    mockMvc
        .perform(
            post("/api/stubs/sms/scenario")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(scenario)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scenario").value("FAILURE"));

    SmsSendRequest sendRequest =
        SmsSendRequest.builder().phoneNumber("+79991112233").message("Code 123456").build();

    mockMvc
        .perform(
            post("/api/stubs/sms/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sendRequest)))
        .andExpect(status().isServiceUnavailable());
  }
}
