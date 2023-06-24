package de.tum.in.www1.artemis.authorization;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.security.annotations.*;

/**
 * Contains the one automatic test covering all rest endpoints for authorization tests.
 */
class AuthorizationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AuthorizationTestService authorizationTestService;

    private static final String ARTEMIS_PACKAGE = "de.tum.in.www1.artemis";

    private static final String REST_BASE_PACKAGE = ARTEMIS_PACKAGE + ".web.rest";

    private static final String REST_ADMIN_PACKAGE = REST_BASE_PACKAGE + ".admin";

    private static final String REST_OPEN_PACKAGE = REST_BASE_PACKAGE + ".open";

    private static JavaClasses productionClasses;

    @BeforeAll
    static void loadClasses() {
        productionClasses = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages(ARTEMIS_PACKAGE);
    }

    @Test
    void testEndpoints() throws InvocationTargetException, IllegalAccessException {
        var requestMappingHandlerMapping = applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        Map<RequestMappingInfo, HandlerMethod> endpointMap = requestMappingHandlerMapping.getHandlerMethods();
        // Filter out endpoints that should not be tested.
        endpointMap = endpointMap.entrySet().stream().filter(entry -> authorizationTestService.validEndpointToTest(entry.getValue(), false))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        authorizationTestService.testEndpoints(endpointMap);
    }

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

    @Test
    void testEnforceNothingAnnotations() {
        ArchRule rule = methods().that().areAnnotatedWith(EnforceNothing.class).and().areNotAnnotatedWith(ManualConfig.class).should().beDeclaredInClassesThat()
                .resideInAPackage(REST_OPEN_PACKAGE + "..");
        rule.check(productionClasses);
    }
}
