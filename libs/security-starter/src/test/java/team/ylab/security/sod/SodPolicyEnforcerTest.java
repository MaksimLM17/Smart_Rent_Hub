package team.ylab.security.sod;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SoD Policy Enforcer Tests (PRD 3.4)")
class SodPolicyEnforcerTest {

  private final DefaultSodPolicyEnforcer enforcer = new DefaultSodPolicyEnforcer(false);
  private final DefaultSodPolicyEnforcer singleOperatorEnforcer =
      new DefaultSodPolicyEnforcer(true);

  @Test
  @DisplayName("Rule 1: Operation on own order is always BLOCKED")
  void rule1SelfOrderOperationIsBlocked() {
    SodRequest req =
        SodRequest.builder()
            .userId("user-123")
            .resourceOwnerId("user-123")
            .action(SodAction.SELF_ORDER_OPERATION)
            .resourceId("order-999")
            .build();

    SodDecision decision = enforcer.evaluate(req);
    assertThat(decision.isAllowed()).isFalse();
    assertThat(decision.getMode()).isEqualTo(SodMode.BLOCK);
  }

  @Test
  @DisplayName("Rule 8: Granting role to self is always BLOCKED")
  void rule8SelfGrantRoleIsBlocked() {
    SodRequest req =
        SodRequest.builder()
            .userId("admin-1")
            .resourceOwnerId("admin-1")
            .action(SodAction.GRANT_ROLE_TO_SELF)
            .build();

    SodDecision decision = enforcer.evaluate(req);
    assertThat(decision.isAllowed()).isFalse();
    assertThat(decision.getMode()).isEqualTo(SodMode.BLOCK);
  }

  @Test
  @DisplayName("Rule 2: High claim resolution requires second approver")
  void rule2RequiresSecondApprover() {
    SodRequest reqWithoutApprover =
        SodRequest.builder()
            .userId("agent-1")
            .resourceOwnerId("customer-1")
            .action(SodAction.HIGH_CLAIM_RESOLUTION)
            .amount(BigDecimal.valueOf(25000))
            .build();

    SodDecision decision = enforcer.evaluate(reqWithoutApprover);
    assertThat(decision.isAllowed()).isFalse();
    assertThat(decision.isSecondApproverRequired()).isTrue();
    assertThat(decision.getMode()).isEqualTo(SodMode.SECOND_APPROVER);

    // Second approver cannot be the initiator
    SodRequest reqWithSelfApprover =
        SodRequest.builder()
            .userId("agent-1")
            .resourceOwnerId("customer-1")
            .secondApproverId("agent-1")
            .action(SodAction.HIGH_CLAIM_RESOLUTION)
            .amount(BigDecimal.valueOf(25000))
            .build();

    SodDecision selfDecision = enforcer.evaluate(reqWithSelfApprover);
    assertThat(selfDecision.isAllowed()).isFalse();
    assertThat(selfDecision.getMode()).isEqualTo(SodMode.BLOCK);

    // With distinct second approver -> allowed
    SodRequest validReq =
        SodRequest.builder()
            .userId("agent-1")
            .resourceOwnerId("customer-1")
            .secondApproverId("supervisor-2")
            .action(SodAction.HIGH_CLAIM_RESOLUTION)
            .amount(BigDecimal.valueOf(25000))
            .build();

    SodDecision validDecision = enforcer.evaluate(validReq);
    assertThat(validDecision.isAllowed()).isTrue();
    assertThat(validDecision.getMode()).isEqualTo(SodMode.SECOND_APPROVER);
  }

  @Test
  @DisplayName("Single operator mode fallbacks to LOG_ONLY for rules 2-7")
  void singleOperatorModeFallsBackToLogOnly() {
    SodRequest req =
        SodRequest.builder()
            .userId("operator-1")
            .resourceOwnerId("customer-2")
            .action(SodAction.HIGH_MANUAL_FINANCE)
            .amount(BigDecimal.valueOf(15000))
            .reason("Customer goodwill refund")
            .build();

    SodDecision decision = singleOperatorEnforcer.evaluate(req);
    assertThat(decision.isAllowed()).isTrue();
    assertThat(decision.getMode()).isEqualTo(SodMode.LOG_ONLY);
  }

  @Test
  @DisplayName("Rule 6: Unit status adjustment requires mandatory reason")
  void rule6RequiresReason() {
    SodRequest withoutReason =
        SodRequest.builder()
            .userId("op-1")
            .action(SodAction.UNIT_STATUS_ADJUST)
            .resourceId("unit-456")
            .build();

    SodDecision d1 = enforcer.evaluate(withoutReason);
    assertThat(d1.isAllowed()).isFalse();

    SodRequest withReason =
        SodRequest.builder()
            .userId("op-1")
            .action(SodAction.UNIT_STATUS_ADJUST)
            .resourceId("unit-456")
            .reason("Scratches on lens surface")
            .build();

    SodDecision d2 = enforcer.evaluate(withReason);
    assertThat(d2.isAllowed()).isTrue();
  }
}
