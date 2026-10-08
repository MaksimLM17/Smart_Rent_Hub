package team.ylab.security.rbac;

/**
 * Well-known permission constants from PRD v3.2 Section 3.2. Permissions follow the
 * "resource:action" format.
 */
public final class SrhPermission {

  private SrhPermission() {}

  // Customer
  public static final String CATALOG_READ = "catalog:read";
  public static final String CART_MANAGE = "cart:manage";
  public static final String ORDER_CREATE = "order:create";
  public static final String ORDER_READ = "order:read";
  public static final String ORDER_CANCEL = "order:cancel";
  public static final String ORDER_EXTEND = "order:extend";
  public static final String FILE_READ = "file:read";
  public static final String CLAIM_RESPOND = "claim:respond";

  // Catalog Editor
  public static final String SKU_ALL = "sku:*";
  public static final String SKU_CREATE = "sku:create";
  public static final String SKU_READ = "sku:read";
  public static final String SKU_UPDATE = "sku:update";
  public static final String SKU_DELETE = "sku:delete";
  public static final String UNIT_CREATE = "unit:create";
  public static final String UNIT_UPDATE = "unit:update";
  public static final String KIT_ALL = "kit:*";
  public static final String KIT_CREATE = "kit:create";
  public static final String KIT_READ = "kit:read";
  public static final String KIT_UPDATE = "kit:update";
  public static final String KIT_DELETE = "kit:delete";
  public static final String CONSUMABLE_ALL = "consumable:*";
  public static final String CONSUMABLE_CREATE = "consumable:create";
  public static final String CONSUMABLE_READ = "consumable:read";
  public static final String CONSUMABLE_UPDATE = "consumable:update";
  public static final String CONSUMABLE_DELETE = "consumable:delete";
  public static final String PHOTO_UPLOAD = "photo:upload";

  // Warehouse Operator
  public static final String PICKUP_TASK_READ = "pickup-task:read";
  public static final String PICKUP_TASK_PERFORM = "pickup-task:perform";
  public static final String HANDOVER_PERFORM = "handover:perform";
  public static final String RETURN_ACCEPT = "return:accept";
  public static final String INSPECTION_PERFORM = "inspection:perform";
  public static final String DEFECT_RECORD = "defect:record";
  public static final String UNIT_SCAN = "unit:scan";

  // Warehouse Supervisor
  public static final String UNIT_ASSIGNMENT_OVERRIDE = "unit-assignment:override";
  public static final String UNIT_STATUS_ADJUST = "unit-status:adjust";
  public static final String ESCALATION_HANDLE = "escalation:handle";
  public static final String CHECKLIST_MANAGE = "checklist:manage";

  // Service Engineer
  public static final String MAINTENANCE_VERDICT = "maintenance:verdict";
  public static final String REPAIR_MANAGE = "repair:manage";
  public static final String DAMAGE_ASSESS = "damage:assess";
  public static final String WEAR_RESET = "wear:reset";
  public static final String UNIT_DECOMMISSION_PROPOSE = "unit:decommission:propose";

  // Risk Moderator
  public static final String REVIEW_READ = "review:read";
  public static final String REVIEW_DECIDE = "review:decide";
  public static final String CUSTOMER_RISK_READ = "customer-risk:read";
  public static final String BLACKLIST_PROPOSE = "blacklist:propose";

  // Support Agent
  public static final String CUSTOMER_READ = "customer:read";
  public static final String CLAIM_MANAGE = "claim:manage";
  public static final String CLAIM_RESOLVE = "claim:resolve";
  public static final String REFUND_ISSUE = "refund:issue";
  public static final String NOTIFICATION_SEND_MANUAL = "notification:send-manual";

  // Finance Operator
  public static final String PAYMENT_READ = "payment:read";
  public static final String CHARGE_MANUAL = "charge:manual";
  public static final String DEBT_MANAGE = "debt:manage";
  public static final String WRITEOFF_APPROVE = "writeoff:approve";
  public static final String FUND_LEDGER_READ = "fund-ledger:read";
  public static final String FINANCE_REPORT_READ = "finance-report:read";

  // Pricing Manager
  public static final String TARIFF_MANAGE = "tariff:manage";
  public static final String PRICING_CONFIG_MANAGE = "pricing-config:manage";
  public static final String PRICING_REPORT_READ = "pricing-report:read";

  // Content Manager
  public static final String COMPAT_RULE_MANAGE = "compat-rule:manage";
  public static final String SEARCH_DICTIONARY_MANAGE = "search-dictionary:manage";

  // Auditor
  public static final String AUDIT_READ = "audit:read";
  public static final String REPORT_READ = "report:read";

  // Admin
  public static final String USER_MANAGE = "user:manage";
  public static final String ROLE_GRANT = "role:grant";
  public static final String CONFIG_MANAGE = "config:manage";
  public static final String USER_BLOCK = "user:block";
  public static final String USER_UNBLOCK = "user:unblock";
  public static final String BLACKLIST_APPROVE = "blacklist:approve";
}
