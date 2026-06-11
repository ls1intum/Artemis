package de.tum.cit.aet.artemis.core.authorization;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import java.lang.annotation.Annotation;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.core.security.annotations.Internal;
import de.tum.cit.aet.artemis.core.security.annotations.ManualConfig;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceRoleInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceRoleInExercise;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLecture.EnforceRoleInLecture;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit.EnforceRoleInLectureUnit;
import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;

class AuthorizationArchitectureTest extends AbstractArchitectureTest {

    private static final String ARTEMIS_PACKAGE = "de.tum.cit.aet.artemis.*";

    private static final String REST_BASE_PACKAGE = ARTEMIS_PACKAGE + ".web";

    private static final String REST_ADMIN_PACKAGE = REST_BASE_PACKAGE + ".admin";

    private static final String REST_OPEN_PACKAGE = REST_BASE_PACKAGE + ".open";

    private static final String REST_INTERNAL_PACKAGE = REST_BASE_PACKAGE + ".internal";

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
        rule.allowEmptyShould(true).check(productionClasses);
    }

    @Test
    void testEnforceInternalAnnotations() {
        ArchRule rule = methods().that().areAnnotatedWith(Internal.class).and().areNotAnnotatedWith(ManualConfig.class).should().beDeclaredInClassesThat()
                .resideInAPackage(REST_INTERNAL_PACKAGE + "..");
        rule.allowEmptyShould(true).check(productionClasses);
    }

    @Test
    void testEnforceAtLeastInstructorAnnotations() {
        ArchRule rule = methods().that().areAnnotatedWith(EnforceAtLeastInstructor.class).and().areNotAnnotatedWith(ManualConfig.class).should().beDeclaredInClassesThat()
                .resideInAPackage(REST_BASE_PACKAGE + "..").andShould().beDeclaredInClassesThat()
                .resideOutsideOfPackages(REST_ADMIN_PACKAGE + "..", REST_OPEN_PACKAGE + "..", REST_INTERNAL_PACKAGE + "..");
        rule.check(productionClasses);
    }

    @Test
    void testEnforceAtLeastEditorAnnotations() {
        ArchRule rule = methods().that().areAnnotatedWith(EnforceAtLeastEditor.class).and().areNotAnnotatedWith(ManualConfig.class).should().beDeclaredInClassesThat()
                .resideInAnyPackage(REST_BASE_PACKAGE + "..").andShould().beDeclaredInClassesThat()
                .resideOutsideOfPackages(REST_ADMIN_PACKAGE + "..", REST_OPEN_PACKAGE + "..", REST_INTERNAL_PACKAGE + "..");
        rule.check(productionClasses);
    }

    @Test
    void testEnforceAtLeastTutorAnnotations() {
        ArchRule rule = methods().that().areAnnotatedWith(EnforceAtLeastTutor.class).and().areNotAnnotatedWith(ManualConfig.class).should().beDeclaredInClassesThat()
                .resideInAPackage(REST_BASE_PACKAGE + "..").andShould().beDeclaredInClassesThat()
                .resideOutsideOfPackages(REST_ADMIN_PACKAGE + "..", REST_OPEN_PACKAGE + "..", REST_INTERNAL_PACKAGE + "..");
        rule.check(productionClasses);
    }

    @Test
    void testEnforceAtLeastStudentAnnotations() {
        ArchRule rule = methods().that().areAnnotatedWith(EnforceAtLeastStudent.class).and().areNotAnnotatedWith(ManualConfig.class).should().beDeclaredInClassesThat()
                .resideInAPackage(REST_BASE_PACKAGE + "..").andShould().beDeclaredInClassesThat()
                .resideOutsideOfPackages(REST_ADMIN_PACKAGE + "..", REST_OPEN_PACKAGE + "..", REST_INTERNAL_PACKAGE + "..");
        rule.check(productionClasses);
    }

    @Test
    void testEnforceNothingAnnotations() {
        ArchRule rule = methods().that().areAnnotatedWith(EnforceNothing.class).and().areNotAnnotatedWith(ManualConfig.class).should().beDeclaredInClassesThat()
                .resideInAPackage(REST_OPEN_PACKAGE + "..");
        rule.check(productionClasses);
    }

    // Includes RequestMapping so that a method-level @RequestMapping(method = ...) is also detected as an endpoint and
    // must be authorized — otherwise it would bypass this rule (and, in modules without a *ResourceArchitectureTest
    // subclass, the module-level shortcut-annotation rule does not run either).
    private static final Set<Class<? extends Annotation>> MAPPING_ANNOTATIONS = Set.of(GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class,
            DeleteMapping.class, RequestMapping.class);

    /**
     * REST endpoints that intentionally perform authorization WITHOUT an Artemis annotation (a shared-secret token, an
     * API key, an in-body API-key check, or a programmatic role check), keyed as the fully qualified
     * {@code com.example.SomeResource#methodName}. The fully qualified class name disambiguates controllers that share
     * a simple name across packages, so an entry can never accidentally exempt a different endpoint.
     * <p>
     * {@link #everyRestEndpointMustBeAuthorized()} fails for any NEW unannotated endpoint, so this set can only shrink.
     * The clean way to remove an entry is to add the right annotation to the endpoint: {@code @EnforceNothing} for a
     * genuinely public endpoint (which must then move into a {@code ..web.open..} package), or {@code @ManualConfig}
     * when authorization is handled in the method body.
     */
    private static final Set<String> UNAUTHENTICATED_ENDPOINT_BASELINE = Set.of("de.tum.cit.aet.artemis.calendar.web.LegacyCalendarResource#getLegacyCalendarEventSubscriptionFile",
            "de.tum.cit.aet.artemis.calendar.web.CalendarResource#getCalendarEventSubscriptionFile", "de.tum.cit.aet.artemis.core.web.SharingSupportResource#getConfig",
            "de.tum.cit.aet.artemis.core.web.SharingSupportResource#isSharingEnabled",
            "de.tum.cit.aet.artemis.iris.web.IrisTutorSuggestionSessionResource#getCurrentSessionOrCreateIfNotExists",
            "de.tum.cit.aet.artemis.iris.web.IrisTutorSuggestionSessionResource#createSessionForPost",
            "de.tum.cit.aet.artemis.programming.web.ExerciseSharingResource#exportExerciseToSharing");

    /**
     * Every REST endpoint must declare an Artemis authorization annotation so authorization can never be silently
     * forgotten on a new endpoint. The existing {@code testEnforce*Annotations} rules are directional ("IF annotated
     * with X THEN it must live in package P") and do not catch an endpoint that carries NO authorization annotation at
     * all — this rule closes that gap.
     * <p>
     * The check is class- and meta-annotation aware: it accepts method-level enforcement (all {@code @EnforceAtLeast*}
     * gates and the composed {@code @EnforceAtLeast*In{Course,Exercise,Lecture,LectureUnit}} variants meta-carry
     * {@code @PreAuthorize}), class-level enforcement (the admin module's class-level {@code @EnforceAdmin} —
     * complementing {@code AbstractModuleResourceArchitectureTest#testNoOverrideOfEnforceAdmin}), and the {@code @Internal}
     * / {@code @ManualConfig} markers. The few endpoints that authorize without an annotation are listed in
     * {@link #UNAUTHENTICATED_ENDPOINT_BASELINE}.
     */
    @Test
    void everyRestEndpointMustBeAuthorized() {
        ArchRule rule = methods().that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class).should(beSecuredByAnAuthorizationAnnotation())
                .because("every REST endpoint must declare an Artemis authorization annotation (or be covered by class-level enforcement) so authorization cannot be forgotten");
        rule.check(productionClasses);
    }

    private ArchCondition<JavaMethod> beSecuredByAnAuthorizationAnnotation() {
        return new ArchCondition<>("be secured by an Artemis authorization annotation") {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean isEndpoint = MAPPING_ANNOTATIONS.stream().anyMatch(method::isAnnotatedWith);
                if (!isEndpoint || isAuthorized(method)) {
                    return;
                }
                String key = method.getOwner().getFullName() + "#" + method.getName();
                if (UNAUTHENTICATED_ENDPOINT_BASELINE.contains(key)) {
                    return;
                }
                events.add(violated(method, method.getFullName()
                        + " is a REST endpoint without an authorization annotation. Add @EnforceAtLeastStudent/Tutor/Editor/Instructor (optionally the In{Course,Exercise,Lecture,LectureUnit} variant), @EnforceAdmin, @EnforceNothing (genuinely public), or @ManualConfig (authorization handled in the method body)."));
            }
        };
    }

    private static boolean isAuthorized(JavaMethod method) {
        JavaClass owner = method.getOwner();
        // The @EnforceAtLeast* gates, @EnforceAdmin/@EnforceSuperAdmin, @EnforceNothing, @Internal and the composed
        // @EnforceAtLeast*In{Course,Exercise,Lecture,LectureUnit} annotations all meta-carry @PreAuthorize; class-level
        // enforcement (e.g. the admin module's class-level @EnforceAdmin) is covered by checking the declaring class.
        return method.isMetaAnnotatedWith(PreAuthorize.class) || owner.isMetaAnnotatedWith(PreAuthorize.class) || method.isAnnotatedWith(ManualConfig.class)
                || method.isAnnotatedWith(Internal.class) || owner.isAnnotatedWith(Internal.class) || method.isMetaAnnotatedWith(EnforceRoleInCourse.class)
                || method.isMetaAnnotatedWith(EnforceRoleInExercise.class) || method.isMetaAnnotatedWith(EnforceRoleInLecture.class)
                || method.isMetaAnnotatedWith(EnforceRoleInLectureUnit.class);
    }
}
