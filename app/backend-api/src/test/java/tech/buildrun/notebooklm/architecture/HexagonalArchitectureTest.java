package tech.buildrun.notebooklm.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "tech.buildrun.notebooklm", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    private static final String ROOT = "tech.buildrun.notebooklm";

    @ArchTest
    static final ArchRule coreFreeOfIoFrameworks = noClasses().that().resideInAPackage(ROOT + ".core..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "jakarta.servlet..", "org.hibernate..",
                    "io.awspring..", "com.fasterxml.jackson..");

    @ArchTest
    static final ArchRule coreDoesNotDependOnAdaptersOrConfig = noClasses().that().resideInAPackage(ROOT + ".core..")
            .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".adapter..", ROOT + ".config..");

    @ArchTest
    static final ArchRule portsInDoNotDependOnUseCases = noClasses().that().resideInAPackage(ROOT + ".adapter.in..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".core.usecase..");

    @ArchTest
    static final ArchRule adaptersDoNotDependOnEachOther = noClasses().that().resideInAPackage(ROOT + ".adapter.in..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".adapter.out..");

    @ArchTest
    static final ArchRule useCasesNamedAndImplementPorts = classes().that().resideInAPackage(ROOT + ".core.usecase..")
            .should().haveSimpleNameEndingWith("UseCase").allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainHasNoPublicSetters = methods().that().areDeclaredInClassesThat()
            .resideInAPackage(ROOT + ".core.domain..").and().arePublic()
            .should().haveNameNotMatching("set[A-Z].*");
}
