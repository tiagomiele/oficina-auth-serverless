package br.com.oficina.auth.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "br.com.oficina.auth",
    importOptions = ImportOption.DoNotIncludeTests.class)
class CleanArchitectureTest {

  @ArchTest
  static final ArchRule domain_is_independent =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..application..",
              "..adapter..",
              "..infrastructure..",
              "..handler..",
              "..notification..",
              "..observability..",
              "..forwarder..",
              "..config..",
              "com.amazonaws..",
              "software.amazon..",
              "java.sql..",
              "io.jsonwebtoken..",
              "com.fasterxml.jackson..",
              "com.newrelic..");

  @ArchTest
  static final ArchRule application_is_independent_from_external_layers =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..adapter..",
              "..infrastructure..",
              "..handler..",
              "..notification..",
              "..observability..",
              "..forwarder..",
              "..config..",
              "com.amazonaws..",
              "software.amazon..",
              "java.sql..",
              "io.jsonwebtoken..",
              "com.fasterxml.jackson..",
              "com.newrelic..");

  @ArchTest
  static final ArchRule input_adapters_are_not_dependencies_of_the_core =
      noClasses()
          .that()
          .resideInAnyPackage("..domain..", "..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..handler..", "..adapter..");
}
