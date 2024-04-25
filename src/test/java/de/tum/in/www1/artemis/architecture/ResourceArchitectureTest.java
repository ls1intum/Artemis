package de.tum.in.www1.artemis.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;

import de.tum.in.www1.artemis.web.rest.ogparser.LinkPreviewResource;

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
        ArchRule rule = methods().that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class).and().arePublic().should().haveRawReturnType(ResponseEntity.class)
                .orShould().haveRawReturnType(ModelAndView.class);
        // We exclude the LinkPreviewResource from this check, as it is a special case that requires the serialization of the response which is not possible with ResponseEntities
        JavaClasses classes = classesExcept(allClasses, LinkPreviewResource.class);
        rule.check(classes);
    }
}
