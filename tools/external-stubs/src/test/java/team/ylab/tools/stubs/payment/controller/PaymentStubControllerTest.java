package team.ylab.tools.stubs.payment.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import team.ylab.tools.stubs.payment.dto.PaymentAuthorizeRequest;
import team.ylab.tools.stubs.payment.dto.PaymentScenarioRequest;
import team.ylab.tools.stubs.payment.service.PaymentStubService;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentStubControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private PaymentStubService paymentStubService;

  @BeforeEach
  void setUp() {
    paymentStubService.resetScenarios();
  }

  @Test
  void shouldAuthorizePaymentSuccessfully() throws Exception {
    PaymentAuthorizeRequest request =
        PaymentAuthorizeRequest.builder()
            .bookingId("b-1001")
            .amount(BigDecimal.valueOf(1500.00))
            .currency("RUB")
            .cardNumber("4000000000000001")
            .build();

    mockMvc
        .perform(
            post("/api/stubs/payments/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bookingId").value("b-1001"))
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.transactionId").isNotEmpty());

    mockMvc
        .perform(get("/api/stubs/payments/history"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void shouldHandleCustomBookingScenario() throws Exception {
    PaymentScenarioRequest scenario =
        PaymentScenarioRequest.builder()
            .bookingId("b-nofunds")
            .status("INSUFFICIENT_FUNDS")
            .message("Not enough money on card balance")
            .build();

    mockMvc
        .perform(
            post("/api/stubs/payments/scenarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(scenario)))
        .andExpect(status().isOk());

    PaymentAuthorizeRequest request =
        PaymentAuthorizeRequest.builder()
            .bookingId("b-nofunds")
            .amount(BigDecimal.valueOf(50000.00))
            .build();

    mockMvc
        .perform(
            post("/api/stubs/payments/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INSUFFICIENT_FUNDS"))
        .andExpect(jsonPath("$.message").value("Not enough money on card balance"));
  }

  @Test
  void shouldHandleThreeDsRequiredScenario() throws Exception {
    PaymentScenarioRequest scenario =
        PaymentScenarioRequest.builder()
            .bookingId("b-3ds")
            .status("THREE_DS_REQUIRED")
            .threeDsRedirectUrl("https://bank.example.com/3ds-challenge")
            .build();

    mockMvc
        .perform(
            post("/api/stubs/payments/scenarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(scenario)))
        .andExpect(status().isOk());

    PaymentAuthorizeRequest request =
        PaymentAuthorizeRequest.builder()
            .bookingId("b-3ds")
            .amount(BigDecimal.valueOf(2000.00))
            .build();

    mockMvc
        .perform(
            post("/api/stubs/payments/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("THREE_DS_REQUIRED"))
        .andExpect(
            jsonPath("$.threeDsRedirectUrl").value("https://bank.example.com/3ds-challenge"));
  }

  @Test
  void shouldResetScenarios() throws Exception {
    mockMvc.perform(delete("/api/stubs/payments/scenarios")).andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/stubs/payments/scenarios"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.default.status").value("SUCCESS"));
  }
}
