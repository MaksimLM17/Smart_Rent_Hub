package team.ylab.tools.stubs.bankid.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankIdVerificationResponse {

  private String verificationId;
  private String clientId;
  private String status; // VERIFIED, REJECTED, PENDING, ERROR
  private String reason;
  private Double score;
  private Instant verifiedAt;
}
