package team.ylab.tools.stubs.bankid.controller;

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
import team.ylab.tools.stubs.bankid.dto.BankIdScenarioRequest;
import team.ylab.tools.stubs.bankid.dto.BankIdVerificationRequest;
import team.ylab.tools.stubs.bankid.service.BankIdStubService;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class BankIdStubControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private BankIdStubService bankIdStubService;

  @BeforeEach
  void setUp() {
    bankIdStubService.resetScenarios();
  }

  @Test
  void shouldVerifySuccessfullyByDefault() throws Exception {
    BankIdVerificationRequest request =
        BankIdVerificationRequest.builder()
            .clientId("client-123")
            .passportNumber("4510 123456")
            .phoneNumber("+79991234567")
            .fullName("Иванов Иван Иванович")
            .build();

    mockMvc
        .perform(
            post("/api/stubs/bank-id/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clientId").value("client-123"))
        .andExpect(jsonPath("$.status").value("VERIFIED"))
        .andExpect(jsonPath("$.verificationId").isNotEmpty());
  }

  @Test
  void shouldApplyCustomClientScenario() throws Exception {
    BankIdScenarioRequest scenario =
        BankIdScenarioRequest.builder()
            .clientId("client-bad")
            .status("REJECTED")
            .reason("Passport is expired or blacklisted")
            .score(0.1)
            .build();

    mockMvc
        .perform(
            post("/api/stubs/bank-id/scenarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(scenario)))
        .andExpect(status().isOk());

    BankIdVerificationRequest request =
        BankIdVerificationRequest.builder()
            .clientId("client-bad")
            .passportNumber("0000 000000")
            .build();

    mockMvc
        .perform(
            post("/api/stubs/bank-id/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clientId").value("client-bad"))
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.reason").value("Passport is expired or blacklisted"))
        .andExpect(jsonPath("$.score").value(0.1));
  }

  @Test
  void shouldHandleTimeoutScenario() throws Exception {
    BankIdScenarioRequest scenario =
        BankIdScenarioRequest.builder().clientId("client-timeout").status("TIMEOUT").build();

    bankIdStubService.configureScenario(scenario);

    BankIdVerificationRequest request =
        BankIdVerificationRequest.builder().clientId("client-timeout").build();

    mockMvc
        .perform(
            post("/api/stubs/bank-id/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isGatewayTimeout());
  }

  @Test
  void shouldResetScenarios() throws Exception {
    mockMvc.perform(delete("/api/stubs/bank-id/scenarios")).andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/stubs/bank-id/scenarios"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.default.status").value("VERIFIED"));
  }
}
