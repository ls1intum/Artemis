package de.tum.cit.aet.artemis.shared.architecture.module;

import static com.tngtech.archunit.lang.ConditionEvent.createMessage;
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import java.lang.annotation.Annotation;
import java.util.Locale;
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

import de.tum.cit.aet.artemis.athena.web.AthenaResource;
import de.tum.cit.aet.artemis.communication.web.LinkPreviewResource;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;

public abstract class AbstractModuleResourceArchitectureTest extends AbstractArchitectureTest implements ModuleArchitectureTest {

    private static final Pattern KEBAB_CASE_PATH_PATTERN = Pattern.compile("^(\\.?[a-z0-9]+(-[a-z0-9]+)*|\\{[^}]+})(/(([a-z0-9]+(-[a-z0-9]+)*|\\{[^}]+})))*(\\.json|/\\*)?$");

    /** A path-variable segment that names an entity id, e.g. {@code {courseId}} or {@code {exerciseId:\\d+}}. */
    private static final Pattern ENTITY_ID_VARIABLE = Pattern.compile("\\{([a-zA-Z]+Id)(?::.*)?}");

    /**
     * Canonical REST paths that currently violate {@link #restPathVariablesMustBePairedWithTheirCollection()}.
     * This is a shrinking baseline: NEW violations are forbidden, and every entry here should be removed as
     * the path is migrated to {@code api/<module>/<plural-collection>/{<collection-singular>Id}} (keeping the
     * old path as a deprecated alias). Do NOT add entries without a follow-up plan.
     * TODO: drive this set to empty.
     */
    private static final Set<String> PATH_VARIABLE_COLLECTION_BASELINE = Set.of(
            // communication
            "api/communication/courses/{courseId}/one-to-one-chats/{userId}",
            // core (file serving — the collection segment doubles as a key in FilePathConverter's external-URI <-> filesystem
            // mapping, in StaticResourcesConfiguration resource handlers, and in stored attachment links / embedded markdown.
            // Pluralizing the route alone would desync it from server-generated and persisted URIs; needs a coordinated data migration.)
            "api/core/files/attachments/attachment-unit/{attachmentVideoUnitId}/*", "api/core/files/attachments/attachment-unit/{attachmentVideoUnitId}/slide/{slideNumber}",
            "api/core/files/attachments/attachment-unit/{attachmentVideoUnitId}/student/*", "api/core/files/attachments/lecture/{lectureId}/merge-pdf",
            "api/core/files/attachments/lecture/{lectureId}/{attachmentName}", "api/core/files/course/icons/{courseId}/*",
            "api/core/files/courses/{courseId}/attachment-units/{attachmentVideoUnitId}", "api/core/files/drag-and-drop/backgrounds/{questionId}/*",
            "api/core/files/exam-user/signatures/{examUserId}/*", "api/core/files/exam-user/{examUserId}/*", "api/core/files/user/profile-pictures/{userId}/*",
            // tutorialgroup (collection "tutorial-free-periods" mismatches its id {tutorialGroupFreePeriodId}: rename the
            // collection to "tutorial-group-free-periods" or the id to {tutorialFreePeriodId} — needs an API naming decision)
            "api/tutorialgroup/courses/{courseId}/tutorial-groups-configurations/{tutorialGroupsConfigurationId}/tutorial-free-periods/{tutorialGroupFreePeriodId}");

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
        // We exclude the AthenaResource as it has endpoints that return void to forward requests from deprecated to the new endpoints
        JavaClasses classes = classesExcept(allClasses, LinkPreviewResource.class, AthenaResource.class);
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

    /**
     * Enforces the REST path convention documented in
     * <a href="https://docs.artemis.tum.de/developer/guidelines/rest-api">the REST API guidelines</a>:
     * a path that identifies an entity must pair the id with its (plural) collection, i.e.
     * {@code api/<module>/<collection>/{<collection-singular>Id}} (for example
     * {@code api/course/courses/{courseId}} or {@code api/exam/courses/{courseId}/exams/{examId}}).
     * <p>
     * Concretely, every path variable whose name ends with {@code Id} must be immediately preceded by a
     * literal collection segment whose pluralized singular matches the id (exactly, or as a subtype
     * suffix so that {@code programming-exercises/{exerciseId}} is accepted). A floating id directly
     * after the module ({@code api/notification/{courseId}}) or after another variable is forbidden;
     * use a query parameter instead when the entity is only a filter and not a sub-resource.
     * <p>
     * Existing deviations are captured in {@link #PATH_VARIABLE_COLLECTION_BASELINE} so the rule fails
     * for any NEW violation. When a baselined path is fixed, remove it from the set; the goal is to
     * shrink the baseline to zero.
     */
    @Test
    void restPathVariablesMustBePairedWithTheirCollection() {
        for (var annotation : annotationClasses) {
            methods().should(pairEntityIdsWithTheirCollection(annotation)).check(productionClasses);
        }
    }

    private ArchCondition<JavaMethod> pairEntityIdsWithTheirCollection(Class<? extends Annotation> annotationClass) {
        return new ArchCondition<>("pair every {entityId} path variable with its plural collection") {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                String methodPath = firstMappingValue(method, annotationClass);
                if (methodPath == null) {
                    return;
                }
                String classPrefix = firstMappingValue(method.getOwner(), RequestMapping.class);
                String canonicalPath = ((classPrefix == null ? "" : classPrefix) + "/" + methodPath).replaceAll("/{2,}", "/").replaceAll("(^/|/$)", "");
                String[] segments = canonicalPath.split("/");
                for (int i = 0; i < segments.length; i++) {
                    var matcher = ENTITY_ID_VARIABLE.matcher(segments[i]);
                    if (!matcher.matches()) {
                        continue;
                    }
                    String idName = matcher.group(1);
                    String expectedCollection = pluralize(camelToKebab(idName.substring(0, idName.length() - 2)));
                    String previous = i > 0 ? segments[i - 1] : "";
                    boolean pairedWithCollection = !previous.isEmpty() && !previous.startsWith("{")
                            && (previous.equals(expectedCollection) || previous.endsWith(expectedCollection));
                    if (!pairedWithCollection && !PATH_VARIABLE_COLLECTION_BASELINE.contains(canonicalPath)) {
                        events.add(violated(method,
                                String.format(
                                        "REST path \"%s\" uses the entity id {%s} without its collection: it must be preceded by the plural collection \"%s\" (e.g. .../%s/{%s}). "
                                                + "Use api/<module>/<plural-collection>/{<collection-singular>Id}, or a query parameter when the entity is only a filter. "
                                                + "See https://docs.artemis.tum.de/developer/guidelines/rest-api",
                                        canonicalPath, idName, expectedCollection, expectedCollection, idName)));
                        return;
                    }
                }
            }
        };
    }

    private String firstMappingValue(HasAnnotations<?> item, Class<? extends Annotation> annotationClass) {
        var annotation = item.getAnnotations().stream().filter(candidate -> ((JavaClass) candidate.getType()).getSimpleName().equals(annotationClass.getSimpleName())).findFirst();
        if (annotation.isEmpty()) {
            return null;
        }
        var value = annotation.get().tryGetExplicitlyDeclaredProperty("value");
        if (value.isEmpty()) {
            return null;
        }
        String[] values = (String[]) value.get();
        return values.length > 0 ? values[0] : null;
    }

    private static String camelToKebab(String camelCase) {
        return camelCase.replaceAll("(?<!^)(?=[A-Z])", "-").toLowerCase(Locale.ROOT);
    }

    private static String pluralize(String kebabSingular) {
        if (kebabSingular.matches(".*[^aeiou]y")) {
            return kebabSingular.substring(0, kebabSingular.length() - 1) + "ies";
        }
        if (kebabSingular.matches(".*(s|x|z|ch|sh)")) {
            return kebabSingular + "es";
        }
        return kebabSingular + "s";
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
                    else if (getIgnoredModulePathPrefixResources().stream().noneMatch(clazz -> clazz.isAssignableFrom(javaClass.reflect()))) {
                        String moduleName = getModulePackage().substring(getModulePackage().lastIndexOf(".") + 1);
                        if (!value.startsWith("api/" + moduleName)) {
                            conditionEvents.add(violated(javaClass, createMessage(javaClass, "The @RequestMapping path value should start with api/" + moduleName)));
                        }
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
        // In the dedicated admin module every class is admin-related by definition, so the legacy
        // "@EnforceAdmin classes must live in a *.admin subpackage" rule does not apply there.
        boolean isAdminModule = getModulePackage().endsWith(".admin");
        ArchRule annotationToNameRule = classesOfThisModuleThat().areAnnotatedWith(EnforceAdmin.class).should().haveSimpleNameStartingWith("Admin")
                .andShould(new ArchCondition<>("Have package name ending with .admin") {

                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (!isAdminModule && !item.getPackage().getName().endsWith(".admin")) {
                            events.add(violated(item, "Classes annotated with @EnforceAdmin should be in an admin subpackage."));
                        }
                    }
                });
        annotationToNameRule.allowEmptyShould(true).check(productionClasses);

        ArchRule nameToAnnotationRule = classesOfThisModuleThat().haveSimpleNameStartingWith("Admin").should().beAnnotatedWith(EnforceAdmin.class);
        nameToAnnotationRule.allowEmptyShould(true).check(productionClasses);
    }

    @Test
    void testNoOverrideOfEnforceAdmin() {
        ArchRule rule = methodsOfThisModuleThat().areDeclaredInClassesThat().areAnnotatedWith(EnforceAdmin.class).should().notBeAnnotatedWith(EnforceAdmin.class).andShould()
                .notBeMetaAnnotatedWith(PreAuthorize.class);
        rule.allowEmptyShould(true).check(productionClasses);
    }

    protected Set<Class<?>> getIgnoredModulePathPrefixResources() {
        return Set.of();
    }
}
