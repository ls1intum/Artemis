package de.tum.in.www1.artemis.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.lang.ArchRule;

class ResourceArchitectureTest extends AbstractArchitectureTest {

    @Test
    void shouldBeNamedResource() {
        ArchRule rule = classes().that().areAnnotatedWith(RestController.class).should().haveSimpleNameEndingWith("Resource")
                .because("resources should have a name ending with 'Resource'.");
        rule.check(productionClasses);
    }

    @Test
    void shouldBeInResourcePackage() {
        ArchRule rule = classes().that().areAnnotatedWith(RestController.class).should().resideInAPackage("..rest..").because("resources should be in the package 'rest'.");
        rule.check(productionClasses);
    }

    // TODO: enable this test once the existing endpoints are migrated (Follow-up PR)
    @Disabled
    @Test
    void allPublicMethodsShouldReturnResponseEntity() {
        methods().that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class).and().arePublic().should().haveRawReturnType(ResponseEntity.class).check(allClasses);
    }
}
