package de.tum.cit.aet.artemis.shared.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.and;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.base.DescribedPredicate.or;
import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.INTERFACES;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameContaining;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.core.domain.JavaCodeUnit.Predicates.constructor;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.ConditionEvent.createMessage;
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.have;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.is;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.members;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noCodeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.awaitility.Awaitility;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hazelcast.core.HazelcastInstance;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaEnumConstant;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.properties.HasAnnotations;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.authorization.AuthorizationTestService;
import de.tum.cit.aet.artemis.core.config.ApplicationConfiguration;
import de.tum.cit.aet.artemis.core.config.ConditionalMetricsExclusionConfiguration;
import de.tum.cit.aet.artemis.core.config.StaticResourcesConfiguration;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryResource;
import de.tum.cit.aet.artemis.shared.base.AbstractArtemisIntegrationTest;

/**
 * This class contains architecture tests that apply for the whole project.
 */
class ArchitectureTest extends AbstractArchitectureTest {

    private static final Logger log = LoggerFactory.getLogger(ArchitectureTest.class);

    @Test
    void testNoJUnit4() {
        ArchRule noJUnit4Imports = noClasses().should().dependOnClassesThat().resideInAPackage("org.junit");
        noJUnit4Imports.check(testClasses);
    }

    @Test
    void testNoGoogleImport() {
        ArchRule noGoogleDependencies = noClasses().should().dependOnClassesThat().resideInAnyPackage("com.google");
        noGoogleDependencies.check(allClasses);
    }

    @Test
    void testClassNameAndVisibility() {
        ArchRule classNames = methods().that().areAnnotatedWith(Test.class).should().beDeclaredInClassesThat().haveNameMatching(".*Test").orShould().beDeclaredInClassesThat()
                .areAnnotatedWith(Nested.class);
        ArchRule noPublicTestClasses = noClasses().that().haveNameMatching(".*Test").should().bePublic();
        ArchRule noPublicTests = noMethods().that().areAnnotatedWith(Test.class).or().areAnnotatedWith(ParameterizedTest.class).or().areAnnotatedWith(BeforeEach.class).or()
                .areAnnotatedWith(BeforeAll.class).or().areAnnotatedWith(AfterEach.class).or().areAnnotatedWith(AfterAll.class).should().bePublic();

        classNames.check(testClasses);
        noPublicTestClasses.check(testClasses.that(are(not(or(simpleNameContaining("Abstract"), INTERFACES)))));
        noPublicTests.check(testClasses);
    }

    @Test
    // TODO When upgrading to Spring Boot 4, we can remove this test.
    @SuppressWarnings("removal")
    void testNoMockBeanAndSpyBean() {
        ArchRule noMockBeanAndSpyBean = noFields().should().beAnnotatedWith(MockBean.class).orShould().beAnnotatedWith(SpyBean.class)
                .because("We use @MockitoBean or @MockitoSpyBean.");
        noMockBeanAndSpyBean.check(testClasses);

    }

    @Test
    void testCorrectStringUtils() {
        ArchRule stringUtils = noClasses().should()
                .dependOnClassesThat(have(simpleName("StringUtils")).and(not(resideInAnyPackage("org.apache.commons.lang3", "org.springframework.util"))));
        ArchRule randomStringUtils = noClasses().should().dependOnClassesThat(have(simpleName("RandomStringUtils")).and(not(resideInAPackage("org.apache.commons.lang3"))));

        stringUtils.check(allClasses);
        randomStringUtils.check(allClasses);
    }

    @Test
    void testNoJunitJupiterAssertions() {
        ArchRule noJunitJupiterAssertions = noClasses().should().dependOnClassesThat().haveNameMatching("org.junit.jupiter.api.Assertions");

        noJunitJupiterAssertions.check(testClasses);
    }

    @Test
    void testNoCollectorsToList() {
        ArchRule toListUsage = noClasses().should().callMethod(Collectors.class, "toList")
                .because("You should use .toList() or .collect(Collectors.toCollection(ArrayList::new)) instead");
        toListUsage.check(allClasses);
    }

    @Test
    void testNullnessAnnotations() {
        // Those are non null annotations for compile time checking. We want to avoid NullPointerExceptions by using those annotations.
        var nonNullPredicate = and(not(resideInPackageAnnotation("org.jspecify.annotations")), simpleNameAnnotation("NonNull"));
        var nullablePredicate = and(not(resideInPackageAnnotation("org.jspecify.annotations")), simpleNameAnnotation("Nullable"));
        // Those are validation annotations. They are used to validate input, e.g. REST request bodies.
        var notNullPredicate = and(not(resideInPackageAnnotation("jakarta.validation.constraints")), simpleNameAnnotation("NonNull"));
        // We want to avoid all other kinds of nullable annotations to ensure consistency.
        var nonnullPredicate = simpleNameAnnotation("Nonnull");

        Set<DescribedPredicate<? super JavaAnnotation<?>>> allPredicates = Set.of(notNullPredicate, nonNullPredicate, nonnullPredicate, nullablePredicate);

        for (var predicate : allPredicates) {
            ArchRule units = noCodeUnits().should().beAnnotatedWith(predicate);
            ArchRule parameters = methods().should(notHaveAnyParameterAnnotatedWith(predicate));

            units.check(allClasses);
            parameters.check(allClasses);
        }
    }

    @Test
    void testValidSimpMessageSendingOperationsUsage() {
        ArchRule usage = fields().that().haveRawType(SimpMessageSendingOperations.class.getTypeName()).should().bePrivate().andShould()
                .beDeclaredIn(WebsocketMessagingService.class)
                .because("Classes should only use WebsocketMessagingService as a Facade and not SimpMessageSendingOperations directly");
        usage.check(productionClasses);
    }

    @Test
    void testFileWriteUsage() {
        ArchRule usage = noClasses().should()
                .callMethodWhere(target(owner(assignableTo(Files.class))).and(target(nameMatching("copy")).or(target(nameMatching("move"))).or(target(nameMatching("write.*")))))
                .because("Files.copy does not create directories if they do not exist. Use Apache FileUtils instead.");
        usage.check(allClasses);
    }

    @Test
    void testLogging() {
        GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.check(allClasses);

        // We currently need to access standard streams in readTestReports() to use the SurefireReportParser
        // The ParallelConsoleAppender is used to print test logs to the console (necessary due to parallel test execution)
        var classes = allClasses.that(not(simpleName("ProgrammingExerciseTemplateIntegrationTest").or(simpleName("ParallelConsoleAppender"))));
        GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(classes);
    }

    @Test
    void testCorrectLoggerFields() {
        var naming = fields().that().haveRawType(Logger.class).should().haveName("log");
        var modifiers = fields().that().haveRawType(Logger.class).should().bePrivate().andShould().beFinal().andShould().beStatic();

        // Interfaces can only contain public attributes
        // The RepositoryResource inherits its logger
        var modifierExclusions = allClasses.that(are(not(INTERFACES)).and(not(type(RepositoryResource.class))));

        naming.check(allClasses);
        modifiers.check(modifierExclusions);
    }

    @Test
    void testJSONImplementations() {
        // Note: we should only use Jackson. There are rare cases where gson is still used
        noClasses().should().dependOnClassesThat(
                have(simpleName("JsonObject").or(simpleName("JSONObject"))).and(not(resideInAPackage("com.google.gson"))).and(not(resideInAPackage("com.fasterxml.jackson.core"))))
                .check(allClasses);
        noClasses().should().dependOnClassesThat(
                have(simpleName("JsonArray").or(simpleName("JSONArray"))).and(not(resideInAPackage("com.google.gson"))).and(not(resideInAPackage("com.fasterxml.jackson.core"))))
                .check(allClasses);
        noClasses().should().dependOnClassesThat(
                have(simpleName("JsonParser").or(simpleName("JSONParser"))).and(not(resideInAPackage("com.google.gson"))).and(not(resideInAPackage("com.fasterxml.jackson.core"))))
                .check(allClasses);
    }

    @Test
    void testGsonExclusion() {
        // TODO: Replace all uses of gson with Jackson and check that gson is not used any more
        var gsonUsageRule = noClasses().should().accessClassesThat().resideInAnyPackage("com.google.gson..").because("we use an alternative JSON parsing library.");
        var result = gsonUsageRule.evaluate(allClasses);
        log.info("Current number of Gson usages: {}", result.getFailureReport().getDetails().size());
        // TODO: reduce the following number to 0
        assertThat(result.getFailureReport().getDetails()).hasSizeLessThanOrEqualTo(664);
    }

    /**
     * Checks that no class directly calls Git.commit(), but instead uses GitService.commit()
     * This is necessary to ensure that committing is identical for all setups, with and without commit signing
     */
    @Test
    void testNoDirectGitCommitCalls() {
        ArchRule usage = noClasses().should().callMethod(Git.class, "commit").because("You should use GitService.commit() instead");
        var classesWithoutGitService = allClasses.that(not(assignableTo(GitService.class)));
        usage.check(classesWithoutGitService);
    }

    @Test
    void testNoHazelcastUsageInConstructors() {
        var notUseHazelcastInConstructor = methods().that().areDeclaredIn(HazelcastInstance.class).should().onlyBeCalled().byCodeUnitsThat(is(not(constructor())))
                .because("Calling Hazelcast during Application startup might be slow since the Network gets used. Use @PostConstruct-methods instead.");
        notUseHazelcastInConstructor.check(allClassesWithHazelcast);
    }

    @Test
    void ensureSpringComponentsAreProfileOrConditionalAnnotated() {
        ArchRule rule = classes().that().areAnnotatedWith(Controller.class).or().areAnnotatedWith(RestController.class).or().areAnnotatedWith(Repository.class).or()
                .areAnnotatedWith(Service.class).or().areAnnotatedWith(Component.class).or().areAnnotatedWith(Configuration.class).and()
                .doNotBelongToAnyOf(ApplicationConfiguration.class, ConditionalMetricsExclusionConfiguration.class).should(beProfileOrConditionalAnnotated())
                .because("we want to be able to exclude these classes from application startup by specifying profiles");

        rule.check(productionClasses);
    }

    @Test
    void testJsonIncludeNonEmpty() {
        members().that().areAnnotatedWith(JsonInclude.class).should(useJsonIncludeNonEmpty()).check(allClasses);
        classes().that().areAnnotatedWith(JsonInclude.class).should(useJsonIncludeNonEmpty()).check(allClasses);
    }

    private <T extends HasAnnotations<T>> ArchCondition<T> useJsonIncludeNonEmpty() {
        return new ArchCondition<>("Use @JsonInclude(JsonInclude.Include.NON_EMPTY)") {

            @Override
            public void check(T item, ConditionEvents events) {
                var annotation = findJavaAnnotation(item, JsonInclude.class);
                var valueProperty = annotation.tryGetExplicitlyDeclaredProperty("value");
                if (valueProperty.isEmpty()) {
                    // @JsonInclude() is ok since it allows explicitly including properties
                    return;
                }
                JavaEnumConstant value = (JavaEnumConstant) valueProperty.get();
                if (!value.name().equals("NON_EMPTY")) {
                    events.add(violated(item, item + " should be annotated with @JsonInclude(JsonInclude.Include.NON_EMPTY)"));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> beProfileOrConditionalAnnotated() {
        return new ArchCondition<>("be annotated with @Profile") {

            @Override
            public void check(JavaClass item, ConditionEvents events) {
                boolean hasProfileAnnotation = item.isAnnotatedWith(Profile.class);
                boolean hasConditionalAnnotation = item.isAnnotatedWith(Conditional.class);
                boolean hasConditionalOnExpression = item.isAnnotatedWith(ConditionalOnExpression.class);
                boolean hasConditionalOnProperty = item.isAnnotatedWith(ConditionalOnProperty.class);
                if (!(hasProfileAnnotation || hasConditionalAnnotation || hasConditionalOnExpression || hasConditionalOnProperty)) {
                    String message = String.format("Class %s is neither annotated with @Profile, @Conditional, @ConditionalOnExpression or @ConditionalOnProperty",
                            item.getFullName());
                    events.add(SimpleConditionEvent.violated(item, message));
                }
            }
        };
    }

    @Test
    void testNoRestControllersImported() {
        final var exceptions = new String[] { "AccountResourceIntegrationTest", "AndroidAppSiteAssociationResourceTest", "AppleAppSiteAssociationResourceTest",
                "AbstractModuleResourceArchitectureTest", "CommunicationResourceArchitectureTest", "PlagiarismApiArchitectureTest", "LtiApiArchitectureTest",
                "IrisTutorSuggestionIntegrationTest", "HyperionCodeGenerationResourceTest" };
        final var classes = classesExcept(allClasses, exceptions);
        classes().should(IMPORT_RESTCONTROLLER).check(classes);
    }

    private static final ArchCondition<JavaClass> IMPORT_RESTCONTROLLER = new ArchCondition<>("not import RestController") {

        @Override
        public void check(JavaClass item, ConditionEvents events) {
            item.getDirectDependenciesFromSelf().stream().map(Dependency::getTargetClass).filter(targetClass -> targetClass.isAnnotatedWith(RestController.class))
                    .filter(targetClass -> item.getEnclosingClass().map(c -> !c.equals(targetClass)).orElse(true))
                    .forEach(targetClass -> events.add(violated(item, "%s imports the RestController %s".formatted(item.getName(), targetClass.getName()))));
        }
    };

    @Test
    void shouldNotUserAutowiredAnnotation() {
        ArchRule rule = noFields().should().beAnnotatedWith(Autowired.class).because("fields should not rely on field injection via @Autowired");
        final var exceptions = new Class[] { StaticResourcesConfiguration.class };
        JavaClasses classes = classesExcept(productionClasses, exceptions);
        rule.check(classes);
    }

    @Test
    void hasMatchingAuthorizationTestClassBeCorrectlyImplemented() throws NoSuchMethodException {
        // Prepare the method that the authorization test should call to be identified as such
        Method allCheckMethod = AuthorizationTestService.class.getMethod("testAllEndpoints", Map.class);
        Method condCheckMethod = AuthorizationTestService.class.getMethod("testConditionalEndpoints", Map.class);
        String identifyingPackage = "authorization";

        ArchRule rule = classes().that(beDirectSubclassOf(AbstractArtemisIntegrationTest.class))
                .should(haveMatchingTestClassCallingAMethod(identifyingPackage, Set.of(allCheckMethod, condCheckMethod)))
                .because("every test environment should have a corresponding authorization test covering the endpoints of this environment.");
        rule.check(testClasses);
    }

    private DescribedPredicate<JavaClass> beDirectSubclassOf(Class<?> clazz) {
        return new DescribedPredicate<>("be implemented in direct subclass of " + clazz.getSimpleName()) {

            @Override
            public boolean test(JavaClass javaClass) {
                var superClasses = javaClass.getAllRawSuperclasses();
                if (superClasses.isEmpty()) {
                    // Tested class has no superclass
                    return false;
                }
                return superClasses.getFirst().getFullName().equals(clazz.getName());
            }
        };
    }

    private ArchCondition<JavaClass> haveMatchingTestClassCallingAMethod(String identifyingPackage, Set<Method> signatureMethods) {
        return new ArchCondition<>("have matching authorization test class") {

            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (!hasMatchingTestClassCallingMethod(item, identifyingPackage, signatureMethods)) {
                    events.add(violated(item, item.getFullName() + " does not have a matching test class in an \"" + identifyingPackage + "\" package "
                            + "containing a test method that calls any given signature methods"));
                }
            }
        };
    }

    private boolean hasMatchingTestClassCallingMethod(JavaClass javaClass, String identifyingPackage, Set<Method> signatureMethods) {
        var subclasses = javaClass.getSubclasses();
        // Check all subclasses of the given abstract test class to search for an authorization test class
        for (JavaClass subclass : subclasses) {
            // The test class es expected to reside inside an identifying package. We could match the full path, but this is more flexible.
            if (!subclass.getPackageName().contains(identifyingPackage)) {
                continue;
            }
            var methods = subclass.getMethods();
            // Search for a test method that calls a signature method
            for (JavaMethod method : methods) {
                if (!method.isAnnotatedWith(Test.class) && !method.getRawReturnType().reflect().equals(Void.class)) {
                    // Is not a test method
                    continue;
                }
                if (method.getMethodCallsFromSelf().stream()
                        .anyMatch(call -> signatureMethods.stream().anyMatch(checkMethod -> call.getTargetOwner().getFullName().equals(checkMethod.getDeclaringClass().getName())
                                && call.getTarget().getName().equals(checkMethod.getName())))) {
                    // Calls one of the signature methods
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void ensureSpringComponentsAreLazyAnnotated() {
        ArchRule rule = classes().that().areAnnotatedWith(Controller.class).or().areAnnotatedWith(RestController.class).or().areAnnotatedWith(Repository.class).or()
                .areAnnotatedWith(Service.class).or().areAnnotatedWith(Component.class).or().areAnnotatedWith(Configuration.class).should().beAnnotatedWith(Lazy.class)
                .because("All Spring components should be lazy-loaded to improve startup time");

        rule.check(allClasses);
    }

    @Test
    void testAsyncTestShouldWait() {
        ArchRule rule = methods().that(areInIntegrationTests()).and(callAnAsyncMethod()).should(callAWaitMethod()).because("tests should wait for async effects");
        rule.check(testClasses);
    }

    private static DescribedPredicate<JavaMethod> callAnAsyncMethod() {
        return new DescribedPredicate<>("call a method annotated with async") {

            @Override
            public boolean test(JavaMethod javaMethod) {
                var asyncCalls = javaMethod.getMethodCallsFromSelf().stream().filter(call -> call.getTarget().isAnnotatedWith(Async.class));

                var firstVerifyLineNumberOptional = javaMethod.getMethodCallsFromSelf().stream()
                        .filter(call -> call.getTarget().getName().equals("verify") && call.getTargetOwner().getFullName().equals("org.mockito.Mockito"))
                        .mapToInt(JavaAccess::getLineNumber).min();

                if (firstVerifyLineNumberOptional.isEmpty()) {
                    return asyncCalls.findAny().isPresent();
                }

                // method calls on and after a verify() line are usually not calls on the actual object
                var firstVerifyLineNumber = firstVerifyLineNumberOptional.getAsInt();
                return asyncCalls.anyMatch(call -> call.getLineNumber() < firstVerifyLineNumber);
            }
        };
    }

    private static DescribedPredicate<JavaMethod> areInIntegrationTests() {

        return new DescribedPredicate<>("are in integration tests") {

            @Override
            public boolean test(JavaMethod javaMethod) {
                return javaMethod.getOwner().isAssignableTo(AbstractArtemisIntegrationTest.class);
            }
        };
    }

    private static ArchCondition<JavaMethod> callAWaitMethod() {
        var isWaiting = callMethod(Mockito.class, "timeout").or(callMethod(Mockito.class, "after")).or(callMethod(Awaitility.class, "await"));

        return new ArchCondition<>("call a wait method") {

            @Override
            public void check(JavaMethod item, ConditionEvents events) {
                boolean doesNotWait = item.getMethodCallsFromSelf().stream().noneMatch(isWaiting);
                if (doesNotWait) {
                    events.add(violated(item, createMessage(item, "does not call a wait method")));
                }
            }
        };
    }

    private static DescribedPredicate<JavaCall<?>> callMethod(Class<?> owner, String methodName) {
        return JavaCall.Predicates.target(owner(type(owner))).and(JavaCall.Predicates.target(name(methodName)));
    }

    @Test
    void testUsageOfSchedulingClasses() {
        // Classes that are not itself part of the scheduling profile
        // should use classes with scheduling profile annotation only in an optional context.
        // We check this using constructors (constructor injection) since otherwise usages of the optional itself would be detected
        constructors().that().areDeclaredInClassesThat(and(annotatedWith(Profile.class), not(classWithSchedulingProfile()))).should(correctlyUseSchedulingParameters())
                .check(productionClasses);
    }

    private DescribedPredicate<JavaClass> classWithSchedulingProfile() {
        return new DescribedPredicate<>("have scheduling profile") {

            @Override
            public boolean test(JavaClass javaClass) {
                var profiles = getProfiles(javaClass);
                for (String profile : profiles) {
                    if (profile.contains("scheduling") && !profile.contains("!scheduling")) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    private String[] getProfiles(JavaClass javaClass) {
        if (!javaClass.isAnnotatedWith(Profile.class)) {
            return new String[0];
        }
        Profile profile = javaClass.getAnnotationOfType(Profile.class);
        return profile.value();
    }

    private ArchCondition<JavaConstructor> correctlyUseSchedulingParameters() {
        return new ArchCondition<>("correctly wrap scheduling dependencies in optionals") {

            @Override
            public void check(JavaConstructor item, ConditionEvents events) {
                var parameters = item.getParameters();
                for (var parameter : parameters) {
                    if (classWithSchedulingProfile().test(parameter.getRawType())) {
                        events.add(violated(parameter,
                                String.format("Class %s uses class %s without wrapping it with Optionals.", parameter.getOwner().getFullName(), parameter.getType().getName())));
                    }
                }
            }
        };
    }

    @Test
    void ensureOnlyLectureClassIsUpdatingUnitOrder() {
        ArchRule rule = methods().that().haveName("setLectureUnitOrder").and().areDeclaredIn(LectureUnit.class).should(onlyBeCalledBy(Lecture.class))
                .because("Only Lecture class should manage the order of lecture units");

        rule.check(allClasses);
    }

    private ArchCondition<JavaMethod> onlyBeCalledBy(Class<?> allowedCaller) {
        return new ArchCondition<>("only be called by " + allowedCaller.getSimpleName()) {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                Set<JavaMethodCall> calls = method.getCallsOfSelf();
                for (JavaMethodCall call : calls) {
                    JavaClass caller = call.getOriginOwner();
                    if (!caller.isAssignableTo(allowedCaller)) {
                        events.add(violated(call,
                                String.format("%s calls %s, but only %s should call this method", caller.getName(), method.getFullName(), allowedCaller.getSimpleName())));
                    }
                }
            }
        };
    }
}
