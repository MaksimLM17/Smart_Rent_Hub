package team.ylab.security.rbac;

import java.util.Optional;

/** Functional roles defined in PRD v3.2 Section 3.2. */
public enum SrhRole {
  CUSTOMER("Клиент сервиса аренды"),
  CATALOG_EDITOR("Редактор каталога товаров и оборудования"),
  WAREHOUSE_OPERATOR("Оператор склада (выдача, прием, первичная инспекция)"),
  WAREHOUSE_SUPERVISOR("Старший смены склада (ручное назначение, эскалации)"),
  SERVICE_ENGINEER("Сервисный инженер (ТО, ремонт, оценка повреждений)"),
  RISK_MODERATOR("Модератор рисков (ручная проверка заказов, чёрный список)"),
  SUPPORT_AGENT("Оператор службы поддержки"),
  FINANCE_OPERATOR("Финансовый специалист"),
  PRICING_MANAGER("Менеджер по тарифам и ценообразованию"),
  CONTENT_MANAGER("Контент-менеджер (правила совместимости)"),
  AUDITOR("Аудитор (доступ только для чтения отчётов)"),
  ADMIN("Администратор платформы");

  private final String description;

  SrhRole(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public static Optional<SrhRole> fromString(String roleName) {
    if (roleName == null || roleName.isBlank()) {
      return Optional.empty();
    }
    String normalized = roleName.trim();
    if (normalized.startsWith("ROLE_")) {
      normalized = normalized.substring(5);
    }
    for (SrhRole role : values()) {
      if (role.name().equalsIgnoreCase(normalized)) {
        return Optional.of(role);
      }
    }
    return Optional.empty();
  }
}
