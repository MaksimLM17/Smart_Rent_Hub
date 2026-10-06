package team.ylab.tools.stubs.payment.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAuthorizeResponse {

  private String paymentId;
  private String bookingId;
  private String transactionId;
  private String status; // SUCCESS, INSUFFICIENT_FUNDS, THREE_DS_REQUIRED, FAILED
  private String threeDsRedirectUrl;
  private String message;
  private Instant timestamp;
}
