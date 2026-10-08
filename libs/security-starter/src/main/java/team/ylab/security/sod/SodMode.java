package team.ylab.security.sod;

/** Modes of Separation of Duties (SoD) enforcement from PRD v3.2 Section 3.4. */
public enum SodMode {
  /** Action is strictly prohibited. */
  BLOCK,

  /** Action requires confirmation by another user with the appropriate authority. */
  SECOND_APPROVER,

  /** Action is permitted immediately with audit logging and warning (used when team is small). */
  LOG_ONLY
}
