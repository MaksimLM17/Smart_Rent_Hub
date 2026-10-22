package team.ylab.inventory.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architectural tests verifying Hexagonal (Ports & Adapters) boundaries in Inventory Service:
 *
 * <ul>
 *   <li>Domain: pure business rules, zero framework dependencies
 *   <li>Application: use case orchestration and ports, isolated from web and persistence
 *   <li>Api: controllers and DTOs, interacts only with application layer
 *   <li>Infra: adapters and technical concerns, implements ports without api dependencies
 * </ul>
 */
@AnalyzeClasses(
    packages = "team.ylab.inventory",
    importOptions = {ImportOption.DoNotIncludeTests.class})
public class HexagonalArchitectureTest {

  @ArchTest
  static final ArchRule hexagonal_architecture_layers =
      layeredArchitecture()
          .consideringAllDependencies()
          .layer("Domain")
          .definedBy("..inventory.domain..")
          .layer("Application")
          .definedBy("..inventory.app..")
          .layer("Api")
          .definedBy("..inventory.api..")
          .layer("Infrastructure")
          .definedBy("..inventory.infra..")
          .whereLayer("Domain")
          .mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Api")
          .whereLayer("Application")
          .mayOnlyBeAccessedByLayers("Api", "Infrastructure")
          .whereLayer("Api")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Infrastructure")
          .mayNotBeAccessedByAnyLayer();

  @ArchTest
  static final ArchRule domain_should_not_depend_on_outer_layers =
      noClasses()
          .that()
          .resideInAPackage("..inventory.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..inventory.app..", "..inventory.api..", "..inventory.infra..")
          .because("Domain must be self-contained and free from outer layers");

  @ArchTest
  static final ArchRule domain_should_not_depend_on_frameworks =
      noClasses()
          .that()
          .resideInAPackage("..inventory.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..", "jakarta.persistence..")
          .because("Domain rules must be pure Java and agnostic to frameworks");

  @ArchTest
  static final ArchRule application_should_not_depend_on_api_or_infra =
      noClasses()
          .that()
          .resideInAPackage("..inventory.app..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..inventory.api..", "..inventory.infra..")
          .because(
              "Application layer orchestrates domain and ports without direct adapter coupling");

  @ArchTest
  static final ArchRule application_should_not_depend_on_web_or_persistence_frameworks =
      noClasses()
          .that()
          .resideInAPackage("..inventory.app..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework.web..", "jakarta.persistence..")
          .because(
              "Application layer must not be coupled to HTTP web or JPA persistence mechanisms");

  @ArchTest
  static final ArchRule api_should_not_depend_on_infrastructure =
      noClasses()
          .that()
          .resideInAPackage("..inventory.api..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..inventory.infra..")
          .because(
              "API controllers must interact via application use case ports, not infra adapters");
}
