package com.checkmate.web.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** 아키텍처 규칙 자동 검증 — 레이어 의존성, 네이밍, 금지 패턴 */
@AnalyzeClasses(
    packages = "com.checkmate.web",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  // ── 레이어 의존성 ──

  @ArchTest
  static final ArchRule layerDependencies =
      layeredArchitecture()
          .consideringAllDependencies()
          .layer("Controller")
          .definedBy("..controller..")
          .layer("Service")
          .definedBy("..service..")
          .layer("Repository")
          .definedBy("..repository..")
          .layer("Entity")
          .definedBy("..entity..")
          .layer("DTO")
          .definedBy("..dto..")
          .layer("Converter")
          .definedBy("..converter..")
          .layer("Config")
          .definedBy("..config..")
          .layer("Exception")
          .definedBy("..exception..")
          .whereLayer("Controller")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Service")
          .mayOnlyBeAccessedByLayers("Controller")
          .whereLayer("Repository")
          .mayOnlyBeAccessedByLayers("Service");

  // ── 네이밍 규칙 ──

  @ArchTest
  static final ArchRule controllerNaming =
      classes()
          .that()
          .resideInAPackage("..controller..")
          .should()
          .haveSimpleNameEndingWith("Controller");

  @ArchTest
  static final ArchRule serviceNaming =
      classes().that().resideInAPackage("..service..").should().haveSimpleNameEndingWith("Service");

  @ArchTest
  static final ArchRule repositoryNaming =
      classes()
          .that()
          .resideInAPackage("..repository..")
          .should()
          .haveSimpleNameEndingWith("Repository");

  @ArchTest
  static final ArchRule converterNaming =
      classes()
          .that()
          .resideInAPackage("..converter..")
          .should()
          .haveSimpleNameEndingWith("Converter");

  // ── 금지 패턴 ──

  @ArchTest
  static final ArchRule controllerShouldNotAccessRepository =
      noClasses()
          .that()
          .resideInAPackage("..controller..")
          .should()
          .accessClassesThat()
          .resideInAPackage("..repository..");

  @ArchTest
  static final ArchRule entityShouldNotDependOnOtherLayers =
      noClasses()
          .that()
          .resideInAPackage("..entity..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..controller..", "..service..", "..repository..", "..dto..");
}
