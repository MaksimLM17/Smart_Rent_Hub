package team.ylab.tools.stubs.bankid.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankIdScenarioRequest {

  /**
   * If clientId is specified, scenario applies only to this client. If null, sets global default.
   */
  private String clientId;

  @Builder.Default
  private String status = "VERIFIED"; // VERIFIED, REJECTED, PENDING, TIMEOUT, ERROR

  @Builder.Default private long delayMs = 0;

  private String reason;

  @Builder.Default private Double score = 0.95;
}
