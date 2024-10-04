package de.tum.cit.aet.artemis.shared.architecture.module;

import static com.tngtech.archunit.lang.ConditionEvent.createMessage;
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import com.tngtech.archunit.base.HasDescription;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.properties.HasAnnotations;
import com.tngtech.archunit.core.domain.properties.HasSourceCodeLocation;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;

import de.tum.cit.aet.artemis.communication.web.LinkPreviewResource;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;

public abstract class AbstractModuleResourceArchitectureTest extends AbstractArchitectureTest implements ModuleArchitectureTest {

    private static final Pattern KEBAB_CASE_PATH_PATTERN = Pattern.compile("^(\\.?[a-z0-9]+(-[a-z0-9]+)*|\\{[^}]+})(/(([a-z0-9]+(-[a-z0-9]+)*|\\{[^}]+})))*(\\.json|/\\*)?$");

    @Test
    void shouldBeNamedResource() {
        ArchRule rule = classesOfThisModuleThat().areAnnotatedWith(RestController.class).should().haveSimpleNameEndingWith("Resource")
                .because("resources should have a name ending with 'Resource'.");

        // allow empty should since some modules do not have any REST controllers
        rule.allowEmptyShould(true).check(productionClasses);
    }

    @Test
    void shouldBeInResourcePackage() {
        ArchRule rule = classesOfThisModuleThat().areAnnotatedWith(RestController.class).should().resideInAPackage("..web..").because("resources should be in the package 'web'.");
        // allow empty should since some modules do not have any REST controllers
        rule.allowEmptyShould(true).check(productionClasses);
    }

    @Test
    void allPublicMethodsShouldReturnResponseEntity() {
        // TODO: We want to move away from ModelAndView. Once all occurrences are removed, this test needs to be adjusted.
        // REST controller methods should return ResponseEntity ("normal" endpoints) or ModelAndView (for redirects)
        ArchRule rule = methodsOfThisModuleThat().areDeclaredInClassesThat().areAnnotatedWith(RestController.class).and().arePublic().should()
                .haveRawReturnType(ResponseEntity.class).orShould().haveRawReturnType(ModelAndView.class);
        // We exclude the LinkPreviewResource from this check, as it is a special case that requires the serialization of the response which is not possible with ResponseEntities
        JavaClasses classes = classesExcept(allClasses, LinkPreviewResource.class);
        // allow empty should since some modules do not have any REST controllers
        rule.allowEmptyShould(true).check(classes);
    }

    private static final Set<Class<? extends Annotation>> annotationClasses = Set.of(GetMapping.class, PatchMapping.class, PostMapping.class, PutMapping.class, DeleteMapping.class,
            RequestMapping.class);

    @Test
    void shouldCorrectlyUseRequestMappingAnnotations() {
        // allow empty should since some modules do not have any REST controllers
        classesOfThisModuleThat().areAnnotatedWith(RequestMapping.class).should(haveCorrectRequestMappingPathForClasses()).allowEmptyShould(true).check(productionClasses);
        for (var annotation : annotationClasses) {
            methods().that().areAnnotatedWith(annotation).should(haveCorrectRequestMappingPathForMethods(annotation)).allowEmptyShould(true).check(productionClasses);
        }
    }

    @Test
    void testUseKebabCaseForRestEndpoints() {
        for (var annotation : annotationClasses) {
            methods().should(useKebabCaseForRestAnnotations(annotation)).check(productionClasses);
        }
    }

    protected ArchCondition<JavaMethod> useKebabCaseForRestAnnotations(Class<?> annotationClass) {
        return new ArchCondition<>("use kebab case for rest mapping annotations") {

            @Override
            public void check(JavaMethod item, ConditionEvents events) {
                var restMappingAnnotation = item.getAnnotations().stream()
                        .filter(annotation -> ((JavaClass) annotation.getType()).getSimpleName().equals(annotationClass.getSimpleName())).findFirst();

                if (restMappingAnnotation.isPresent()) {
                    String restURL = ((String[]) restMappingAnnotation.get().tryGetExplicitlyDeclaredProperty("value").get())[0];
                    if (!KEBAB_CASE_PATH_PATTERN.matcher(restURL).matches()) {
                        events.add(violated(item, String.format("\"%s\" violates rule to only use kebab case for REST annotations in %s", restURL, item.getFullName())));
                    }
                }
            }
        };
    }

    private ArchCondition<JavaClass> haveCorrectRequestMappingPathForClasses() {
        return new ArchCondition<>("correctly use @RequestMapping") {

            @Override
            public void check(JavaClass javaClass, ConditionEvents conditionEvents) {
                testRequestAnnotation(javaClass, RequestMapping.class, conditionEvents, value -> {
                    if (value.startsWith("/")) {
                        conditionEvents.add(violated(javaClass, createMessage(javaClass, "The @RequestMapping path value should not start with /")));
                    }
                    if (!value.endsWith("/")) {
                        conditionEvents.add(violated(javaClass, createMessage(javaClass, "The @RequestMapping path value should always end with /")));
                    }
                });
            }
        };
    }

    private ArchCondition<JavaMethod> haveCorrectRequestMappingPathForMethods(Class<?> annotationClass) {
        return new ArchCondition<>("correctly use @RequestMapping") {

            @Override
            public void check(JavaMethod javaMethod, ConditionEvents conditionEvents) {
                testRequestAnnotation(javaMethod, annotationClass, conditionEvents, value -> {
                    if (value.startsWith("/")) {
                        conditionEvents.add(violated(javaMethod, createMessage(javaMethod, "The @RequestMapping path value should not start with /")));
                    }
                    if (value.endsWith("/")) {
                        conditionEvents.add(violated(javaMethod, createMessage(javaMethod, "The @RequestMapping path value should not end with /")));
                    }
                });
            }
        };
    }

    private <T extends HasAnnotations<T> & HasDescription & HasSourceCodeLocation> void testRequestAnnotation(T item, Class<?> annotationClass, ConditionEvents conditionEvents,
            Consumer<String> tester) {
        var annotation = findJavaAnnotation(item, annotationClass);
        var valueProperty = annotation.tryGetExplicitlyDeclaredProperty("value");
        if (valueProperty.isEmpty()) {
            conditionEvents.add(violated(item, createMessage(item, "RequestMapping should declare a path value.")));
            return;
        }
        String[] values = ((String[]) valueProperty.get());
        for (String value : values) {
            tester.accept(value);
        }
    }

    @Test
    void testClassWithEnforceAdminInCorrectlyNamed() {
        ArchRule annotationToNameRule = classesOfThisModuleThat().areAnnotatedWith(EnforceAdmin.class).should().haveSimpleNameStartingWith("Admin")
                .andShould(new ArchCondition<>("Have " + "package name ending with .admin") {

                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (!item.getPackage().getName().endsWith(".admin")) {
                            events.add(violated(item, "Classes annotated with @EnforceAdmin should be in an admin subpackage."));
                        }
                    }
                });
        annotationToNameRule.check(productionClasses);

        ArchRule nameToAnnotationRule = classesOfThisModuleThat().haveSimpleNameStartingWith("Admin").should().beAnnotatedWith(EnforceAdmin.class);
        nameToAnnotationRule.check(productionClasses);
    }

    @Test
    void testNoOverrideOfEnforceAdmin() {
        ArchRule rule = methodsOfThisModuleThat().areDeclaredInClassesThat().areAnnotatedWith(EnforceAdmin.class).should().notBeAnnotatedWith(EnforceAdmin.class).andShould()
                .notBeMetaAnnotatedWith(PreAuthorize.class);
        rule.check(productionClasses);
    }
}
