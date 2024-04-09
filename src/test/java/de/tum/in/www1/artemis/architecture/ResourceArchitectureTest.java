package de.tum.in.www1.artemis.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

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

    @Test
    void allPublicMethodsShouldReturnResponseEntity() {
        // REST controller methods should return ResponseEntity ("normal" endpoints) or ModelAndView (for redirects)
        methods().that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class).and().arePublic().should().haveRawReturnType(ResponseEntity.class).orShould()
                .haveRawReturnType(ModelAndView.class).check(allClasses);
    }
}
