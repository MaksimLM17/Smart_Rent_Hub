package team.ylab.security.sod;

/** Sensitive actions defined in PRD v3.2 Section 3.4 that require SoD enforcement. */
public enum SodAction {
  /**
   * Rule 1: Moderation, inspection, or claim resolution on employee's own order. Mode: Always
   * BLOCK.
   */
  SELF_ORDER_OPERATION,

  /**
   * Rule 2: Claim resolution above threshold (default 20,000 RUB). Mode: SECOND_APPROVER (approver
   * != inspector).
   */
  HIGH_CLAIM_RESOLUTION,

  /**
   * Rule 3: Manual refund, penalty forgiveness, manual charge above threshold (default 10,000 RUB).
   * Mode: SECOND_APPROVER.
   */
  HIGH_MANUAL_FINANCE,

  /** Rule 4: Decommissioning unit or moving to LOST. Mode: SECOND_APPROVER. */
  DECOMMISSION_OR_LOST,

  /** Rule 5: Changing tariffs and pricing parameters. Mode: Only PRICING_MANAGER. */
  TARIFF_CHANGE,

  /** Rule 6: Manual unit status adjustment. Mode: Mandatory reason, audit logged. */
  UNIT_STATUS_ADJUST,

  /** Rule 7: Unblocking customer from blacklist. Mode: ADMIN + SUPPORT_AGENT approval. */
  BLACKLIST_UNBLOCK,

  /** Rule 8: Granting role to oneself. Mode: Always BLOCK (requires another ADMIN). */
  GRANT_ROLE_TO_SELF
}
