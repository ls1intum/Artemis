package de.tum.cit.aet.artemis.shared.architecture.module;

import static com.tngtech.archunit.lang.ConditionEvent.createMessage;
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import java.lang.annotation.Annotation;
import java.util.Collection;
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
import org.springframework.web.bind.annotation.RequestBody;
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
import de.tum.cit.aet.artemis.core.security.annotations.ManualConfig;
import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;

public abstract class AbstractModuleResourceArchitectureTest extends AbstractArchitectureTest implements ModuleArchitectureTest {

    private static final Pattern KEBAB_CASE_PATH_PATTERN = Pattern.compile("^(\\.?[a-z0-9]+(-[a-z0-9]+)*|\\{[^}]+})(/(([a-z0-9]+(-[a-z0-9]+)*|\\{[^}]+})))*(\\.json|/\\*)?$");

    /** A path-variable segment that names an entity id, e.g. {@code {courseId}} or {@code {exerciseId:\\d+}}. */
    private static final Pattern ENTITY_ID_VARIABLE = Pattern.compile("\\{([a-zA-Z]+Id)(?::.*)?}");

    /**
     * REST paths exempted from {@link #restPathVariablesMustBePairedWithTheirCollection()}.
     * <p>
     * This set is now empty: every REST path follows {@code api/<module>/<plural-collection>/{<collection-singular>Id}} (or uses a query parameter where the id is only a filter).
     * The rule still enforces the convention for all controllers, so any NEW violation fails the build. Only add an entry here together with a documented follow-up plan to migrate
     * it, keeping the old path as a deprecated alias.
     */
    private static final Set<String> PATH_VARIABLE_COLLECTION_BASELINE = Set.of();

    /**
     * REST endpoints exempted from {@link #restEndpointsShouldNotReturnModelAndView()}, keyed as the fully qualified
     * {@code com.example.SomeResource#methodName} (the fully qualified class name disambiguates controllers that share
     * a simple name across packages).
     * <p>
     * This set is now <strong>empty</strong>: no REST endpoint returns {@link ModelAndView} anymore, so the type is
     * fully forbidden and any new usage fails the build. Only add an entry here together with a documented plan to
     * migrate it back to a {@link ResponseEntity}.
     */
    private static final Set<String> MODEL_AND_VIEW_BASELINE = Set.of();

    /** Method-name prefixes that contradict a {@code @GetMapping}: a GET handler must read, not mutate. */
    private static final Set<String> GET_FORBIDDEN_NAME_PREFIXES = Set.of("create", "add", "update", "delete", "remove", "save", "register", "import", "upload", "put", "patch");

    /** Method-name prefixes that contradict a {@code @DeleteMapping}: a DELETE handler must delete, not read or create. */
    private static final Set<String> DELETE_FORBIDDEN_NAME_PREFIXES = Set.of("get", "list", "find", "fetch", "retrieve", "create", "add");

    /**
     * DELETE endpoints exempted from {@link #deleteEndpointsShouldNotDeclareRequestBody()}, keyed as the fully
     * qualified {@code com.example.SomeResource#methodName} (the fully qualified class name disambiguates controllers
     * that share a simple name across packages).
     * <p>
     * {@code PushNotificationResource#unregister} accepts the token/device type as query parameters, but still also
     * accepts a (deprecated) request body for backwards compatibility with older mobile clients that have not yet
     * migrated. Remove this entry — and the {@code @RequestBody} parameter — once those clients are updated. Only add
     * a new entry together with a documented plan to move the payload off the request body.
     */
    private static final Set<String> DELETE_REQUEST_BODY_BASELINE = Set.of("de.tum.cit.aet.artemis.notification.web.PushNotificationResource#unregister");

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
        // REST controller methods must return ResponseEntity. ModelAndView is forbidden (see restEndpointsShouldNotReturnModelAndView).
        ArchRule rule = methodsOfThisModuleThat().areDeclaredInClassesThat().areAnnotatedWith(RestController.class).and().arePublic().should()
                .haveRawReturnType(ResponseEntity.class);
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
                                ("REST path \"%s\" uses the entity id {%s} without its collection: it must be preceded by the plural collection \"%s\" (e.g. .../%s/{%s}). "
                                        + "Use api/<module>/<plural-collection>/{<collection-singular>Id}, or a query parameter when the entity is only a filter. "
                                        + "See https://docs.artemis.tum.de/developer/guidelines/rest-api")
                                        .formatted(canonicalPath, idName, expectedCollection, expectedCollection, idName)));
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
                        events.add(violated(item, "\"%s\" violates rule to only use kebab case for REST annotations in %s".formatted(restURL, item.getFullName())));
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

    /**
     * REST endpoints must express the HTTP verb with the dedicated shortcut annotations
     * ({@code @GetMapping}/{@code @PostMapping}/{@code @PutMapping}/{@code @PatchMapping}/{@code @DeleteMapping}).
     * {@code @RequestMapping} is reserved for the class-level path prefix and must not annotate an endpoint method: a
     * method-level {@code @RequestMapping} hides the HTTP verb from a quick read of the handler and may bind several
     * verbs to one method, which the verb-specific rules ({@link #endpointMethodNamesShouldMatchTheirHttpVerb()} and
     * the GET/DELETE request-body rules — these key off the concrete shortcut annotation) cannot reason about.
     */
    @Test
    void endpointsShouldUseHttpMethodShortcutAnnotations() {
        methodsOfThisModuleThat().areDeclaredInClassesThat().areAnnotatedWith(RestController.class).should().notBeAnnotatedWith(RequestMapping.class)
                .because("endpoints must use @GetMapping/@PostMapping/@PutMapping/@PatchMapping/@DeleteMapping; @RequestMapping is only allowed as the class-level path prefix")
                .allowEmptyShould(true).check(productionClasses);
    }

    /**
     * Endpoint methods must be {@code public}. A package-private or protected mapping method would silently escape the
     * other Resource rules (several only inspect public methods) and is almost always a mistake.
     */
    @Test
    void endpointsMustBePublic() {
        for (var annotation : annotationClasses) {
            methodsOfThisModuleThat().areDeclaredInClassesThat().areAnnotatedWith(RestController.class).and().areAnnotatedWith(annotation).should().bePublic()
                    .allowEmptyShould(true).check(productionClasses);
        }
    }

    /**
     * GET endpoints must not declare a {@code @RequestBody}: per RFC 9110 a GET request body has no defined semantics
     * and is dropped by many proxies and clients. Pass data via path or query parameters instead.
     */
    @Test
    void getEndpointsShouldNotDeclareRequestBody() {
        methodsOfThisModuleThat().areDeclaredInClassesThat().areAnnotatedWith(RestController.class).and().areAnnotatedWith(GetMapping.class)
                .should(notDeclareRequestBody(Set.of(), "a GET request must not carry a body (RFC 9110); use path or query parameters instead")).allowEmptyShould(true)
                .check(productionClasses);
    }

    /**
     * DELETE endpoints should not declare a {@code @RequestBody}: a DELETE body is discouraged and ignored by many
     * intermediaries. Pass identifiers via the path or query parameters. The one current exception
     * ({@code PushNotificationResource#unregister}, which keeps an optional deprecated body for older mobile clients) is
     * baselined, so the rule fails for any NEW DELETE-with-body endpoint.
     */
    @Test
    void deleteEndpointsShouldNotDeclareRequestBody() {
        methodsOfThisModuleThat().areDeclaredInClassesThat().areAnnotatedWith(RestController.class).and().areAnnotatedWith(DeleteMapping.class)
                .should(notDeclareRequestBody(DELETE_REQUEST_BODY_BASELINE, "a DELETE request should not carry a body; pass identifiers via the path or query parameters"))
                .allowEmptyShould(true).check(productionClasses);
    }

    private ArchCondition<JavaMethod> notDeclareRequestBody(Set<String> baseline, String rationale) {
        return new ArchCondition<>("not declare a @RequestBody parameter") {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (baseline.contains(method.getOwner().getFullName() + "#" + method.getName())) {
                    return;
                }
                // Use ArchUnit's parameter-annotation model (no reflection / class loading), consistent with the rest of the suite.
                boolean declaresRequestBody = method.getParameterAnnotations().stream().flatMap(Collection::stream)
                        .anyMatch(annotation -> annotation.getRawType().isEquivalentTo(RequestBody.class));
                if (declaresRequestBody) {
                    events.add(violated(method, method.getFullName() + " declares a @RequestBody; " + rationale + "."));
                }
            }
        };
    }

    /**
     * {@code @ManualConfig} is the authorization escape hatch (it tells the authorization architecture tests that the
     * endpoint secures itself in its body). It must therefore only ever sit on an actual REST endpoint method, so it
     * cannot be used to silence the authorization rules on a non-endpoint.
     */
    @Test
    void manualConfigMustBeDeclaredOnEndpointMethods() {
        methodsOfThisModuleThat().areDeclaredInClassesThat().areAnnotatedWith(RestController.class).and().areAnnotatedWith(ManualConfig.class).should(beAMappingMethod())
                .allowEmptyShould(true).check(productionClasses);
    }

    private ArchCondition<JavaMethod> beAMappingMethod() {
        return new ArchCondition<>("be a REST endpoint (carry an HTTP-method mapping annotation)") {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean isEndpoint = annotationClasses.stream().anyMatch(method::isAnnotatedWith);
                if (!isEndpoint) {
                    events.add(violated(method, method.getFullName()
                            + " is annotated with @ManualConfig but is not a REST endpoint. @ManualConfig is the authorization escape hatch and must only annotate mapping methods."));
                }
            }
        };
    }

    /**
     * The handler method name must be consistent with its HTTP verb: a {@code @GetMapping} method must not be named
     * like a mutation ({@code create*}/{@code update*}/{@code delete*}/...), and a {@code @DeleteMapping} method must
     * not be named like a read or a create ({@code get*}/{@code find*}/{@code create*}/...). This catches endpoints
     * accidentally wired to the wrong HTTP verb.
     */
    @Test
    void endpointMethodNamesShouldMatchTheirHttpVerb() {
        methodsOfThisModuleThat().areDeclaredInClassesThat().areAnnotatedWith(RestController.class).should(haveNameConsistentWithHttpVerb()).allowEmptyShould(true)
                .check(productionClasses);
    }

    private ArchCondition<JavaMethod> haveNameConsistentWithHttpVerb() {
        return new ArchCondition<>("have a method name consistent with the HTTP verb") {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                String name = method.getName();
                if (method.isAnnotatedWith(GetMapping.class) && startsWithVerbPrefix(name, GET_FORBIDDEN_NAME_PREFIXES)) {
                    events.add(violated(method, method.getFullName() + " is a @GetMapping but its name '" + name
                            + "' suggests a mutation. A GET handler must read data — rename it (get/list/find/...) or use the correct HTTP verb."));
                }
                if (method.isAnnotatedWith(DeleteMapping.class) && startsWithVerbPrefix(name, DELETE_FORBIDDEN_NAME_PREFIXES)) {
                    events.add(violated(method, method.getFullName() + " is a @DeleteMapping but its name '" + name
                            + "' suggests a read or create. A DELETE handler must delete — rename it (delete/remove/...) or use the correct HTTP verb."));
                }
            }
        };
    }

    /**
     * Returns {@code true} if {@code methodName} starts with one of the {@code prefixes} at a camelCase word boundary
     * (so {@code addressBook} does not match the prefix {@code add}, but {@code addUser} and {@code add} do).
     */
    private static boolean startsWithVerbPrefix(String methodName, Set<String> prefixes) {
        return prefixes.stream()
                .anyMatch(prefix -> methodName.startsWith(prefix) && (methodName.length() == prefix.length() || Character.isUpperCase(methodName.charAt(prefix.length()))));
    }

    /**
     * REST endpoints must return {@link ResponseEntity}, never {@link ModelAndView}. No endpoint returns
     * {@link ModelAndView} anymore, so {@link #MODEL_AND_VIEW_BASELINE} is empty and the type is fully forbidden: any
     * new {@code ModelAndView} return fails the build.
     */
    @Test
    void restEndpointsShouldNotReturnModelAndView() {
        methodsOfThisModuleThat().areDeclaredInClassesThat().areAnnotatedWith(RestController.class).should(notReturnModelAndView()).allowEmptyShould(true).check(productionClasses);
    }

    private ArchCondition<JavaMethod> notReturnModelAndView() {
        return new ArchCondition<>("not return ModelAndView") {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (method.getRawReturnType().isEquivalentTo(ModelAndView.class)) {
                    String key = method.getOwner().getFullName() + "#" + method.getName();
                    if (!MODEL_AND_VIEW_BASELINE.contains(key)) {
                        events.add(violated(method, method.getFullName()
                                + " returns ModelAndView; REST endpoints must return ResponseEntity. ModelAndView is being phased out (see MODEL_AND_VIEW_BASELINE)."));
                    }
                }
            }
        };
    }

    protected Set<Class<?>> getIgnoredModulePathPrefixResources() {
        return Set.of();
    }
}
