package team.ylab.security.sod;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/** Request describing a sensitive action subject to Separation of Duties (SoD) evaluation. */
@Value
@Builder
public class SodRequest {
  String userId;
  SodAction action;
  String resourceId;
  String resourceOwnerId;
  String secondApproverId;
  BigDecimal amount;
  String reason;
}
