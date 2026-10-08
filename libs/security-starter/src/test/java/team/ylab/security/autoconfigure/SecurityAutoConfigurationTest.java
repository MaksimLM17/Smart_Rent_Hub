package team.ylab.security.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import team.ylab.security.context.SecurityUserContext;
import team.ylab.security.jwt.KeycloakJwtAuthenticationConverter;
import team.ylab.security.rbac.RolePermissionRegistry;
import team.ylab.security.sod.SodPolicyEnforcer;

@DisplayName("SecurityAutoConfiguration Tests")
class SecurityAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class));

  @Test
  @DisplayName("Default configuration registers all core security beans")
  void defaultConfigurationRegistersBeans() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(RolePermissionRegistry.class);
          assertThat(context).hasSingleBean(KeycloakJwtAuthenticationConverter.class);
          assertThat(context).hasSingleBean(SecurityUserContext.class);
          assertThat(context).hasSingleBean(SodPolicyEnforcer.class);
        });
  }

  @Test
  @DisplayName("Setting srh.security.enabled=false disables auto-configuration")
  void disabledConfiguration() {
    contextRunner
        .withPropertyValues("srh.security.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(RolePermissionRegistry.class);
              assertThat(context).doesNotHaveBean(KeycloakJwtAuthenticationConverter.class);
              assertThat(context).doesNotHaveBean(SecurityUserContext.class);
              assertThat(context).doesNotHaveBean(SodPolicyEnforcer.class);
            });
  }
}
