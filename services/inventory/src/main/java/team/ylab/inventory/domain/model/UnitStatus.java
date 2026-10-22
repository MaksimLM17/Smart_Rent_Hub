package team.ylab.inventory.domain.model;

/** Lifecycle status of a physical inventory unit (SDD v1.1 Appendix A.2). */
public enum UnitStatus {
  AVAILABLE,
  ASSIGNED,
  IN_USE,
  RETURNED_PENDING_INSPECTION,
  MAINTENANCE,
  IN_REPAIR,
  DECOMMISSIONED,
  LOST
}
