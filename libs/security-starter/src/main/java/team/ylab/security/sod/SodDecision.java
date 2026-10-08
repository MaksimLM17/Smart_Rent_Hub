package team.ylab.security.sod;

import lombok.Value;

/** Result of SoD evaluation. */
@Value
public class SodDecision {
  boolean allowed;
  SodMode mode;
  String reason;
  boolean secondApproverRequired;

  public static SodDecision allowed(SodMode mode, String reason) {
    return new SodDecision(true, mode, reason, false);
  }

  public static SodDecision requiresSecondApprover(String reason) {
    return new SodDecision(false, SodMode.SECOND_APPROVER, reason, true);
  }

  public static SodDecision blocked(String reason) {
    return new SodDecision(false, SodMode.BLOCK, reason, false);
  }

  public static SodDecision logOnly(String reason) {
    return new SodDecision(true, SodMode.LOG_ONLY, reason, false);
  }
}
