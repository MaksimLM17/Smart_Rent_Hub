package team.ylab.tools.stubs.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentScenarioRequest {

  /** If bookingId is set, scenario applies only to this booking. If null, sets global default. */
  private String bookingId;

  @Builder.Default
  private String status =
      "SUCCESS"; // SUCCESS, INSUFFICIENT_FUNDS, THREE_DS_REQUIRED, TIMEOUT, FAILED

  @Builder.Default private long delayMs = 0;

  private String message;

  private String threeDsRedirectUrl;
}
