package pl.hadacz.coupons;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The hexagonal layering as executable rules: dependencies point inwards,
/// and the core does not know about frameworks or adapters.
class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("pl.hadacz.coupons");

    @Test
    @DisplayName("domain depends on nothing but the JDK (and JSpecify annotations)")
    void domainIsFrameworkFree() {
        noClasses().that().resideInAPackage("..coupon.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta..", "org.hibernate..",
                        "..coupon.application..", "..coupon.adapter..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("use cases depend on the domain and ports, not on frameworks or adapters")
    void applicationIsFrameworkFree() {
        noClasses().that().resideInAPackage("..coupon.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta..", "org.hibernate..", "..coupon.adapter..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("adapters do not depend on each other")
    void adaptersAreIndependent() {
        slices().matching("..coupon.adapter.(*)..")
                .should().notDependOnEachOther()
                .check(CLASSES);
    }
}
