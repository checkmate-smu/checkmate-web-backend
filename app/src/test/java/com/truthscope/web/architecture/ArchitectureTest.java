package com.truthscope.web.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** 아키텍처 규칙 자동 검증 — 레이어 의존성, 네이밍, 금지 패턴 */
@AnalyzeClasses(
    packages = "com.truthscope.web",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  // ── 레이어 의존성 ──

  @ArchTest
  static final ArchRule layerDependencies =
      layeredArchitecture()
          .consideringAllDependencies()
          .optionalLayer("Controller")
          .definedBy("..controller..")
          .optionalLayer("Service")
          .definedBy("..service..")
          .optionalLayer("Repository")
          .definedBy("..repository..")
          .optionalLayer("Entity")
          .definedBy("..entity..")
          .optionalLayer("DTO")
          .definedBy("..dto..")
          .optionalLayer("Converter")
          .definedBy("..converter..")
          .optionalLayer("Config")
          .definedBy("..config..")
          .optionalLayer("Exception")
          .definedBy("..exception..")
          .whereLayer("Controller")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Service")
          .mayOnlyBeAccessedByLayers("Controller")
          .whereLayer("Repository")
          .mayOnlyBeAccessedByLayers("Service");

  // ── 네이밍 규칙 (패키지가 비어있으면 통과) ──

  @ArchTest
  static final ArchRule controllerNaming =
      classes()
          .that()
          .resideInAPackage("..controller..")
          .should()
          .haveSimpleNameEndingWith("Controller")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule serviceNaming =
      classes()
          .that()
          .resideInAPackage("..service..")
          .and()
          .arePublic()
          .and()
          .areTopLevelClasses()
          .should()
          .haveSimpleNameEndingWith("Service")
          .allowEmptyShould(true)
          .because(
              "Service stereotype 네이밍은 public top-level 클래스에만 적용. inner record/handler/private utility는 구현 세부 사항으로 exempt.");

  @ArchTest
  static final ArchRule repositoryNaming =
      classes()
          .that()
          .resideInAPackage("..repository..")
          .should()
          .haveSimpleNameEndingWith("Repository")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule converterNaming =
      classes()
          .that()
          .resideInAPackage("..converter..")
          .should()
          .haveSimpleNameEndingWith("Converter")
          .allowEmptyShould(true);

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

  // ── DDD invariant: entity는 setter 금지 (always-valid 모델) ──

  @ArchTest
  static final ArchRule entityShouldNotExposeSetters =
      noMethods()
          .that()
          .areDeclaredInClassesThat()
          .resideInAPackage("com.truthscope.web.entity")
          .should()
          .haveNameStartingWith("set")
          .because(
              "Entity 변경은 비즈니스 메서드로만 허용 (DDD always-valid 모델). enum 패키지는 별도(..entity.enums..)이므로 영향 없음.");

  // ── core 모듈 격리 (ADR-006 D1: OSS 단독 배포 가능성 강제) ──

  @ArchTest
  static final ArchRule corePackagesShouldNotDependOnAppLayer =
      noClasses()
          .that()
          .resideInAnyPackage(
              "..entity..", "..dto..", "..converter..", "..exception..", "..adapter..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..controller..",
              "..service..",
              "..repository..",
              "..config..",
              "..security..",
              "..html..")
          .allowEmptyShould(true)
          .because(
              "ADR-006: core 모듈(entity/dto/converter/exception/adapter)은 app 모듈(controller/service/repository/config/security/html)에 의존 금지. core가 OSS jar로 단독 배포 가능해야 함.");

  // ── DTO record 강제 (CONVENTIONS: 요청 DTO는 record 타입) ──

  @ArchTest
  static final ArchRule requestDtoShouldBeRecord =
      classes()
          .that()
          .resideInAPackage("..dto.request..")
          .should()
          .beRecords()
          .allowEmptyShould(true)
          .because(
              "CONVENTIONS: 요청 DTO는 record 타입 (Java 17+) — immutable + 자동 equals/hashCode/toString.");
}
