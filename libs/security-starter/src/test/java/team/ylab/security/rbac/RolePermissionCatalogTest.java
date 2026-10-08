package team.ylab.security.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("RBAC Catalog Tests - All roles and permissions from PRD 3.2")
class RolePermissionCatalogTest {

  private RolePermissionRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new DefaultRolePermissionRegistry();
  }

  @Test
  @DisplayName("All 12 functional roles from PRD 3.2 are registered")
  void allTwelveFunctionalRolesAreRegistered() {
    for (SrhRole role : SrhRole.values()) {
      assertThat(registry.getAllRegisteredRoles()).contains(role.name());
    }
  }

  @ParameterizedTest
  @EnumSource(SrhRole.class)
  @DisplayName("Each functional role has non-empty permissions")
  void eachRoleHasNonEmptyPermissions(SrhRole role) {
    Set<String> perms = registry.getPermissionsForRole(role.name());
    assertThat(perms).isNotEmpty();
  }

  @Test
  @DisplayName("CUSTOMER role has customer permissions and cannot create SKU")
  void customerPermissions() {
    Set<String> perms = registry.getPermissionsForRole("CUSTOMER");
    assertThat(perms)
        .contains(
            SrhPermission.CATALOG_READ,
            SrhPermission.CART_MANAGE,
            SrhPermission.ORDER_CREATE,
            SrhPermission.ORDER_READ,
            SrhPermission.ORDER_CANCEL,
            SrhPermission.ORDER_EXTEND,
            SrhPermission.FILE_READ,
            SrhPermission.CLAIM_RESPOND);
    assertThat(registry.hasPermission("CUSTOMER", SrhPermission.SKU_CREATE)).isFalse();
    assertThat(registry.hasPermission("CUSTOMER", "sku:delete")).isFalse();
  }

  @Test
  @DisplayName("CATALOG_EDITOR role has sku:* and expanded sku:create")
  void catalogEditorPermissions() {
    Set<String> perms = registry.getPermissionsForRole("CATALOG_EDITOR");
    assertThat(perms)
        .contains(
            SrhPermission.SKU_ALL,
            SrhPermission.SKU_CREATE,
            SrhPermission.SKU_READ,
            SrhPermission.SKU_UPDATE,
            SrhPermission.SKU_DELETE,
            SrhPermission.UNIT_CREATE,
            SrhPermission.UNIT_UPDATE,
            SrhPermission.PHOTO_UPLOAD);
    assertThat(registry.hasPermission("CATALOG_EDITOR", SrhPermission.SKU_CREATE)).isTrue();
    assertThat(registry.hasPermission("CATALOG_EDITOR", SrhPermission.PHOTO_UPLOAD)).isTrue();
  }

  @Test
  @DisplayName("WAREHOUSE_OPERATOR role permissions")
  void warehouseOperatorPermissions() {
    Set<String> perms = registry.getPermissionsForRole("WAREHOUSE_OPERATOR");
    assertThat(perms)
        .contains(
            SrhPermission.PICKUP_TASK_READ,
            SrhPermission.PICKUP_TASK_PERFORM,
            SrhPermission.HANDOVER_PERFORM,
            SrhPermission.RETURN_ACCEPT,
            SrhPermission.INSPECTION_PERFORM,
            SrhPermission.DEFECT_RECORD,
            SrhPermission.UNIT_SCAN);
  }

  @Test
  @DisplayName("WAREHOUSE_SUPERVISOR role permissions")
  void warehouseSupervisorPermissions() {
    Set<String> perms = registry.getPermissionsForRole("WAREHOUSE_SUPERVISOR");
    assertThat(perms)
        .contains(
            SrhPermission.UNIT_ASSIGNMENT_OVERRIDE,
            SrhPermission.UNIT_STATUS_ADJUST,
            SrhPermission.ESCALATION_HANDLE,
            SrhPermission.CHECKLIST_MANAGE);
  }

  @Test
  @DisplayName("SERVICE_ENGINEER role permissions")
  void serviceEngineerPermissions() {
    Set<String> perms = registry.getPermissionsForRole("SERVICE_ENGINEER");
    assertThat(perms)
        .contains(
            SrhPermission.MAINTENANCE_VERDICT,
            SrhPermission.REPAIR_MANAGE,
            SrhPermission.DAMAGE_ASSESS,
            SrhPermission.WEAR_RESET,
            SrhPermission.UNIT_DECOMMISSION_PROPOSE);
  }

  @Test
  @DisplayName("RISK_MODERATOR role permissions")
  void riskModeratorPermissions() {
    Set<String> perms = registry.getPermissionsForRole("RISK_MODERATOR");
    assertThat(perms)
        .contains(
            SrhPermission.REVIEW_READ,
            SrhPermission.REVIEW_DECIDE,
            SrhPermission.CUSTOMER_RISK_READ,
            SrhPermission.BLACKLIST_PROPOSE);
  }

  @Test
  @DisplayName("SUPPORT_AGENT role permissions")
  void supportAgentPermissions() {
    Set<String> perms = registry.getPermissionsForRole("SUPPORT_AGENT");
    assertThat(perms)
        .contains(
            SrhPermission.ORDER_READ,
            SrhPermission.CUSTOMER_READ,
            SrhPermission.CLAIM_MANAGE,
            SrhPermission.CLAIM_RESOLVE,
            SrhPermission.REFUND_ISSUE,
            SrhPermission.NOTIFICATION_SEND_MANUAL);
  }

  @Test
  @DisplayName("FINANCE_OPERATOR role permissions")
  void financeOperatorPermissions() {
    Set<String> perms = registry.getPermissionsForRole("FINANCE_OPERATOR");
    assertThat(perms)
        .contains(
            SrhPermission.PAYMENT_READ,
            SrhPermission.CHARGE_MANUAL,
            SrhPermission.DEBT_MANAGE,
            SrhPermission.WRITEOFF_APPROVE,
            SrhPermission.FUND_LEDGER_READ,
            SrhPermission.FINANCE_REPORT_READ);
  }

  @Test
  @DisplayName("PRICING_MANAGER role permissions")
  void pricingManagerPermissions() {
    Set<String> perms = registry.getPermissionsForRole("PRICING_MANAGER");
    assertThat(perms)
        .contains(
            SrhPermission.TARIFF_MANAGE,
            SrhPermission.PRICING_CONFIG_MANAGE,
            SrhPermission.PRICING_REPORT_READ);
  }

  @Test
  @DisplayName("CONTENT_MANAGER role permissions")
  void contentManagerPermissions() {
    Set<String> perms = registry.getPermissionsForRole("CONTENT_MANAGER");
    assertThat(perms)
        .contains(SrhPermission.COMPAT_RULE_MANAGE, SrhPermission.SEARCH_DICTIONARY_MANAGE);
  }

  @Test
  @DisplayName("AUDITOR role permissions")
  void auditorPermissions() {
    Set<String> perms = registry.getPermissionsForRole("AUDITOR");
    assertThat(perms).contains(SrhPermission.AUDIT_READ, SrhPermission.REPORT_READ);
  }

  @Test
  @DisplayName("ADMIN role permissions")
  void adminPermissions() {
    Set<String> perms = registry.getPermissionsForRole("ADMIN");
    assertThat(perms)
        .contains(
            SrhPermission.USER_MANAGE,
            SrhPermission.ROLE_GRANT,
            SrhPermission.CONFIG_MANAGE,
            SrhPermission.USER_BLOCK,
            SrhPermission.USER_UNBLOCK,
            SrhPermission.BLACKLIST_APPROVE,
            SrhPermission.AUDIT_READ);
  }

  @Test
  @DisplayName("Composite role Warehouse-Universal inherits permissions of its constituent roles")
  void compositeRoleWarehouseUniversal() {
    Set<String> perms = registry.getPermissionsForRole("Warehouse-Universal");
    // Constituent: WAREHOUSE_OPERATOR, WAREHOUSE_SUPERVISOR, RISK_MODERATOR, CATALOG_EDITOR
    assertThat(perms)
        .contains(
            SrhPermission.PICKUP_TASK_PERFORM,
            SrhPermission.UNIT_ASSIGNMENT_OVERRIDE,
            SrhPermission.REVIEW_DECIDE,
            SrhPermission.SKU_CREATE);
  }
}
