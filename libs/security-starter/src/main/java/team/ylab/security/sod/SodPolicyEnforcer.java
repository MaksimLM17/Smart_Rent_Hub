package team.ylab.security.sod;

/** Enforces Separation of Duties (SoD) policies. */
public interface SodPolicyEnforcer {

  /** Evaluates whether a sensitive action is allowed, blocked, or requires a second approver. */
  SodDecision evaluate(SodRequest request);
}
