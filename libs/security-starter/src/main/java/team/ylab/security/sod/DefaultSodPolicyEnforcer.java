package team.ylab.security.sod;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * Default implementation of {@link SodPolicyEnforcer}. Implements SoD rules from PRD v3.2 Section
 * 3.4.
 */
@Slf4j
public class DefaultSodPolicyEnforcer implements SodPolicyEnforcer {

  private final boolean singleOperatorMode;

  public DefaultSodPolicyEnforcer() {
    this(false);
  }

  public DefaultSodPolicyEnforcer(boolean singleOperatorMode) {
    this.singleOperatorMode = singleOperatorMode;
  }

  @Override
  public SodDecision evaluate(SodRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(request.getAction(), "action must not be null");

    String userId = request.getUserId();
    String ownerId = request.getResourceOwnerId();
    String approverId = request.getSecondApproverId();

    // Rule 1: Moderation, inspection, claim on own order is ALWAYS BLOCK
    if (request.getAction() == SodAction.SELF_ORDER_OPERATION) {
      if (userId != null && userId.equals(ownerId)) {
        log.warn(
            "SoD violation: User {} attempted self-order operation on resource {}. BLOCKED.",
            userId,
            request.getResourceId());
        return SodDecision.blocked("Self-order operation is strictly forbidden (Rule 1).");
      }
    }

    // Rule 8: Granting role to self is ALWAYS BLOCK
    if (request.getAction() == SodAction.GRANT_ROLE_TO_SELF) {
      if (userId != null && userId.equals(ownerId)) {
        log.warn("SoD violation: User {} attempted to grant role to self. BLOCKED.", userId);
        return SodDecision.blocked("Self-granting roles is strictly forbidden (Rule 8).");
      }
    }

    // Single operator mode fallback to LOG_ONLY for rules 2-7
    if (singleOperatorMode) {
      log.info(
          "SoD [LOG_ONLY] Single operator mode active. User {} executing {} on resource {}. Reason: {}",
          userId,
          request.getAction(),
          request.getResourceId(),
          request.getReason());
      return SodDecision.logOnly("Single operator mode: action logged with audit trail.");
    }

    // Rules requiring second approver:
    if (request.getAction() == SodAction.HIGH_CLAIM_RESOLUTION
        || request.getAction() == SodAction.HIGH_MANUAL_FINANCE
        || request.getAction() == SodAction.DECOMMISSION_OR_LOST
        || request.getAction() == SodAction.BLACKLIST_UNBLOCK) {

      if (approverId == null || approverId.isBlank()) {
        log.info("SoD: Action {} requires second approver.", request.getAction());
        return SodDecision.requiresSecondApprover("Action requires second approver.");
      }

      if (approverId.equals(userId)) {
        log.warn("SoD violation: Second approver cannot be the initiator ({}).", userId);
        return SodDecision.blocked("Second approver must not be the initiator.");
      }

      log.info(
          "SoD: Action {} initiated by {} approved by {}.",
          request.getAction(),
          userId,
          approverId);
      return SodDecision.allowed(
          SodMode.SECOND_APPROVER, "Action approved by second user: " + approverId);
    }

    // Rule 6: Manual unit status adjustment requires reason
    if (request.getAction() == SodAction.UNIT_STATUS_ADJUST) {
      if (request.getReason() == null || request.getReason().isBlank()) {
        return SodDecision.blocked("Unit status adjustment requires a non-empty reason.");
      }
      log.info(
          "SoD: Unit status adjustment by {} on {}. Reason: {}",
          userId,
          request.getResourceId(),
          request.getReason());
      return SodDecision.allowed(SodMode.LOG_ONLY, "Unit status adjusted with audit log.");
    }

    return SodDecision.allowed(SodMode.LOG_ONLY, "Action allowed.");
  }
}
