package de.tum.cit.aet.artemis.core.security.annotations;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class EnforceSuperAdminTest {

    @Test
    void testAnnotationIsPresent() {
        // Verify that the annotation class exists and can be loaded
        assertThat(EnforceSuperAdmin.class).isNotNull();
        assertThat(EnforceSuperAdmin.class.isAnnotation()).isTrue();
    }

    @Test
    void testAnnotationHasCorrectRetentionPolicy() {
        // Verify that the annotation has RUNTIME retention so it's available via reflection
        var retention = EnforceSuperAdmin.class.getAnnotation(java.lang.annotation.Retention.class);
        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
    }

    @Test
    void testAnnotationHasCorrectTargets() {
        // Verify that the annotation can be applied to both methods and types
        var target = EnforceSuperAdmin.class.getAnnotation(java.lang.annotation.Target.class);
        assertThat(target).isNotNull();
        assertThat(target.value()).containsExactlyInAnyOrder(ElementType.METHOD, ElementType.TYPE);
    }

    @Test
    void testAnnotationHasPreAuthorizeMetaAnnotation() {
        // Verify that the annotation is meta-annotated with @PreAuthorize
        PreAuthorize preAuthorize = EnforceSuperAdmin.class.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).isNotNull();
    }

    @Test
    void testPreAuthorizeExpressionIsCorrect() {
        // Verify that the PreAuthorize expression contains the correct security checks
        PreAuthorize preAuthorize = EnforceSuperAdmin.class.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).isNotNull();

        String expression = preAuthorize.value();
        assertThat(expression).isNotNull();
        assertThat(expression).contains("hasRole('SUPER_ADMIN')");
        assertThat(expression).contains("@passkeyAuthenticationService.isAuthenticatedWithSuperAdminApprovedPasskey()");
        assertThat(expression).contains(" and ");
    }

    @Test
    void testAnnotationCanBeAppliedToMethod() throws NoSuchMethodException {
        // Verify that the annotation can be successfully applied to a method
        Method method = TestClass.class.getMethod("testMethod");
        EnforceSuperAdmin annotation = method.getAnnotation(EnforceSuperAdmin.class);
        assertThat(annotation).isNotNull();
    }

    @Test
    void testAnnotationCanBeAppliedToClass() {
        // Verify that the annotation can be successfully applied to a class
        EnforceSuperAdmin annotation = TestClassWithAnnotation.class.getAnnotation(EnforceSuperAdmin.class);
        assertThat(annotation).isNotNull();
    }

    @Test
    void testAnnotationInheritsPreAuthorizeOnMethod() throws NoSuchMethodException {
        // Verify that when applied to a method, the PreAuthorize meta-annotation is accessible
        Method method = TestClass.class.getMethod("testMethod");
        Annotation[] annotations = method.getAnnotations();

        boolean hasEnforceSuperAdmin = false;
        for (Annotation annotation : annotations) {
            if (annotation instanceof EnforceSuperAdmin) {
                hasEnforceSuperAdmin = true;
                break;
            }
        }

        assertThat(hasEnforceSuperAdmin).isTrue();
    }

    @Test
    void testAnnotationInheritsPreAuthorizeOnClass() {
        // Verify that when applied to a class, the PreAuthorize meta-annotation is accessible
        Annotation[] annotations = TestClassWithAnnotation.class.getAnnotations();

        boolean hasEnforceSuperAdmin = false;
        for (Annotation annotation : annotations) {
            if (annotation instanceof EnforceSuperAdmin) {
                hasEnforceSuperAdmin = true;
                break;
            }
        }

        assertThat(hasEnforceSuperAdmin).isTrue();
    }

    // Test helper classes
    static class TestClass {

        @EnforceSuperAdmin
        public void testMethod() {
            // Method for testing annotation application
        }
    }

    @EnforceSuperAdmin
    static class TestClassWithAnnotation {
        // Class for testing annotation application
    }
}
