package de.tum.cit.aet.artemis.authorization;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.lang.ArchRule;

import de.tum.cit.aet.artemis.architecture.AbstractArchitectureTest;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.core.security.annotations.ManualConfig;

class AuthorizationArchitectureTest extends AbstractArchitectureTest {

    private static final String ARTEMIS_PACKAGE = "de.tum.cit.aet.artemis.*";

    private static final String REST_BASE_PACKAGE = ARTEMIS_PACKAGE + ".web";

    private static final String REST_ADMIN_PACKAGE = REST_BASE_PACKAGE + ".admin";

    private static final String REST_OPEN_PACKAGE = REST_BASE_PACKAGE + ".open";

    @Test
    void testNoPreAuthorizeOnRestControllers() {
        ArchRule rule = noClasses().that().areAnnotatedWith(RestController.class).should().beAnnotatedWith(PreAuthorize.class).because(
                "All endpoints should be secured directly using the Artemis enforcement annotations on the method definition. Refer to the server guidelines in our documentation.");
        rule.check(productionClasses);
    }

    @Test
    void testNoPreAuthorizeOnRestEndpoints() {
        ArchRule rule = noMethods().that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class).should().beAnnotatedWith(PreAuthorize.class)
                .because("All endpoints should be secured using the Artemis enforcement annotations. Refer to the server guidelines in our documentation.");
        rule.check(productionClasses);
    }

    @Disabled // TODO: Enable this test once the restructuring is done
    @Test
    void testEnforceAdminAnnotations() {
        ArchRule rule = methods().that().areAnnotatedWith(EnforceAdmin.class).and().areNotAnnotatedWith(ManualConfig.class).should().beDeclaredInClassesThat()
                .resideInAPackage(REST_ADMIN_PACKAGE + "..");
        rule.check(productionClasses);
    }

    @Test
    void testEnforceAtLeastInstructorAnnotations() {
        ArchRule rule = methods().that().areAnnotatedWith(EnforceAtLeastInstructor.class).and().areNotAnnotatedWith(ManualConfig.class).should().beDeclaredInClassesThat()
                .resideInAPackage(REST_BASE_PACKAGE + "..").andShould().beDeclaredInClassesThat().resideOutsideOfPackages(REST_ADMIN_PACKAGE + "..", REST_OPEN_PACKAGE + "..");
        rule.check(productionClasses);
    }

    @Test
    void testEnforceAtLeastEditorAnnotations() {
        ArchRule rule = methods().that().areAnnotatedWith(EnforceAtLeastEditor.class).and().areNotAnnotatedWith(ManualConfig.class).should().beDeclaredInClassesThat()
                .resideInAnyPackage(REST_BASE_PACKAGE + "..").andShould().beDeclaredInClassesThat().resideOutsideOfPackages(REST_ADMIN_PACKAGE + "..", REST_OPEN_PACKAGE + "..");
        rule.check(productionClasses);
    }

    @Test
    void testEnforceAtLeastTutorAnnotations() {
        ArchRule rule = methods().that().areAnnotatedWith(EnforceAtLeastTutor.class).and().areNotAnnotatedWith(ManualConfig.class).should().beDeclaredInClassesThat()
                .resideInAPackage(REST_BASE_PACKAGE + "..").andShould().beDeclaredInClassesThat().resideOutsideOfPackages(REST_ADMIN_PACKAGE + "..", REST_OPEN_PACKAGE + "..");
        rule.check(productionClasses);
    }

    @Test
    void testEnforceAtLeastStudentAnnotations() {
        ArchRule rule = methods().that().areAnnotatedWith(EnforceAtLeastStudent.class).and().areNotAnnotatedWith(ManualConfig.class).should().beDeclaredInClassesThat()
                .resideInAPackage(REST_BASE_PACKAGE + "..").andShould().beDeclaredInClassesThat().resideOutsideOfPackages(REST_ADMIN_PACKAGE + "..", REST_OPEN_PACKAGE + "..");
        rule.check(productionClasses);
    }

    @Disabled // TODO: Enable this test once the restructuring is done
    @Test
    void testEnforceNothingAnnotations() {
        ArchRule rule = methods().that().areAnnotatedWith(EnforceNothing.class).and().areNotAnnotatedWith(ManualConfig.class).should().beDeclaredInClassesThat()
                .resideInAPackage(REST_OPEN_PACKAGE + "..");
        rule.check(productionClasses);
    }
}
