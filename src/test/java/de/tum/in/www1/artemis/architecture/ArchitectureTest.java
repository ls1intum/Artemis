package de.tum.in.www1.artemis.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.and;
import static com.tngtech.archunit.base.DescribedPredicate.equalTo;
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
import static com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.core.domain.properties.HasType.Predicates.rawType;
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.have;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.is;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noCodeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.Assertions;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.slf4j.Logger;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import com.hazelcast.core.HazelcastInstance;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;

import de.tum.in.www1.artemis.AbstractArchitectureTest;
import de.tum.in.www1.artemis.config.ApplicationConfiguration;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInCourse.EnforceRoleInCourse;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInExercise.EnforceRoleInExercise;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.web.rest.repository.RepositoryResource;

class ArchitectureTest extends AbstractArchitectureTest {

    @Test
    void testNoJUnit4() {
        ArchRule noJUnit4Imports = noClasses().should().dependOnClassesThat().resideInAPackage("org.junit");
        ArchRule noPublicTests = noMethods().that().areAnnotatedWith(Test.class).or().areAnnotatedWith(ParameterizedTest.class).or().areAnnotatedWith(BeforeEach.class).or()
                .areAnnotatedWith(BeforeAll.class).or().areAnnotatedWith(AfterEach.class).or().areAnnotatedWith(AfterAll.class).should().bePublic();
        ArchRule classNames = methods().that().areAnnotatedWith(Test.class).should().beDeclaredInClassesThat().haveNameMatching(".*Test");
        ArchRule noPublicTestClasses = noClasses().that().haveNameMatching(".*Test").should().bePublic();

        noJUnit4Imports.check(testClasses);
        noPublicTests.check(testClasses);
        classNames.check(testClasses);
        noPublicTestClasses.check(testClasses.that(are(not(simpleNameContaining("Abstract")))));
    }

    @Test
    void testNoWrongServiceImports() {
        ArchRule rule = noClasses().should().dependOnClassesThat().resideInAnyPackage("org.jvnet.hk2.annotations")
                .because("this is the wrong service class, use org.springframework.stereotype.Service.");
        rule.check(allClasses);
    }

    @Test
    void testCorrectServiceAnnotation() {

        classes().that().resideInAPackage("de.tum.in.www1.artemis.service..").and().haveSimpleNameEndingWith("Service").and().areNotInterfaces().and().doNotHaveModifier(ABSTRACT)
                .should().beAnnotatedWith(org.springframework.stereotype.Service.class)
                .because("services should be consistently managed by Spring's dependency injection container.").check(allClasses);

        classes().that().haveSimpleNameEndingWith("Service").should().notBeAnnotatedWith(Component.class).check(allClasses);
        classes().that().haveSimpleNameEndingWith("Service").should().notBeAnnotatedWith(RestController.class).check(allClasses);

        classes().that().areAnnotatedWith(Service.class).should().haveSimpleNameEndingWith("Service").check(allClasses);
        classes().that().areAnnotatedWith(Service.class).should().notBeAnnotatedWith(Component.class).check(allClasses);
        classes().that().areAnnotatedWith(Service.class).should().notBeAnnotatedWith(RestController.class).check(allClasses);
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
    void testNoEntityGraphsOnQueries() {
        ArchRule noEntityGraphsOnQueries = noMethods().that().areAnnotatedWith(Query.class).and().areDeclaredInClassesThat().areInterfaces().and().areDeclaredInClassesThat()
                .areAnnotatedWith(Repository.class).should().beAnnotatedWith(EntityGraph.class)
                .because("Spring Boot 3 ignores EntityGraphs on JPQL queries. You need to integrate a JOIN FETCH into the query.");
        noEntityGraphsOnQueries.check(productionClasses);
    }

    @Test
    void testNoCollectorsToList() {
        ArchRule toListUsage = noClasses().should().callMethod(Collectors.class, "toList")
                .because("You should use .toList() or .collect(Collectors.toCollection(ArrayList::new)) instead");
        toListUsage.check(allClasses);
    }

    @Test
    void testNullnessAnnotations() {
        var notNullPredicate = and(not(resideInPackageAnnotation("javax.validation.constraints")), simpleNameAnnotation("NotNull"));
        var nonNullPredicate = simpleNameAnnotation("NonNull");
        var nullablePredicate = and(not(resideInPackageAnnotation("javax.annotation")), simpleNameAnnotation("Nullable"));

        Set<DescribedPredicate<? super JavaAnnotation<?>>> allPredicates = Set.of(notNullPredicate, nonNullPredicate, nullablePredicate);

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
        ArchRule jsonObject = noClasses().should().dependOnClassesThat(have(simpleName("JsonObject").or(simpleName("JSONObject"))).and(not(resideInAPackage("com.google.gson"))));

        ArchRule jsonArray = noClasses().should().dependOnClassesThat(have(simpleName("JsonArray").or(simpleName("JSONArray"))).and(not(resideInAPackage("com.google.gson"))));

        ArchRule jsonParser = noClasses().should().dependOnClassesThat(have(simpleName("JsonParser").or(simpleName("JSONParser"))).and(not(resideInAPackage("com.google.gson"))));

        jsonObject.check(allClasses);
        jsonArray.check(allClasses);
        jsonParser.check(allClasses);
    }

    @Test
    void testRepositoryParamAnnotation() {
        var useParamInQueries = methods().that().areAnnotatedWith(Query.class).should(haveAllParametersAnnotatedWithUnless(rawType(Param.class), type(Pageable.class)));
        var notUseParamOutsideQueries = methods().that().areNotAnnotatedWith(Query.class).should(notHaveAnyParameterAnnotatedWith(rawType(Param.class)));
        useParamInQueries.check(productionClasses);
        notUseParamOutsideQueries.check(productionClasses);
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
        // CacheHandler and QuizCache are exceptions because these classes are not created during startup
        var exceptions = or(declaredClassSimpleName("QuizCache"), declaredClassSimpleName("CacheHandler"));
        var notUseHazelcastInConstructor = methods().that().areDeclaredIn(HazelcastInstance.class).should().onlyBeCalled().byCodeUnitsThat(is(not(constructor()).or(exceptions)))
                .because("Calling Hazelcast during Application startup might be slow since the Network gets used. Use @PostConstruct-methods instead.");
        notUseHazelcastInConstructor.check(allClasses);
    }

    @Test
    void ensureSpringComponentsAreProfileAnnotated() {
        ArchRule rule = classes().that().areAnnotatedWith(Controller.class).or().areAnnotatedWith(RestController.class).or().areAnnotatedWith(Repository.class).or()
                .areAnnotatedWith(Service.class).or().areAnnotatedWith(Component.class).or().areAnnotatedWith(Configuration.class).and()
                .doNotBelongToAnyOf(ApplicationConfiguration.class).should(beProfileAnnotated())
                .because("we want to be able to exclude these classes from application startup by specifying profiles");

        rule.check(productionClasses);
    }

    @Test
    void testJPQLStyle() {
        var queryRule = methods().that().areAnnotatedWith(Query.class).should(useUpperCaseSQLStyle()).because("@Query content should follow the style guide");
        queryRule.check(allClasses);
    }

    @Test
    void testEnforceRoleInCourseEndpointHasCourseIdParameter() {
        ArchCondition<JavaMethod> haveParameterWithAnnotation = new ArchCondition<>("have a parameter with EnforceRoleInCourse annotation") {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                // Get annotation
                var enforceRoleInCourseAnnotation = getAnnotation(EnforceRoleInCourse.class, method);
                var courseIdFieldName = enforceRoleInCourseAnnotation.resourceIdFieldName();
                if (!hasParameterWithName(method, courseIdFieldName)) {
                    events.add(violated(method, String.format("Method %s does not have a parameter named %s", method.getFullName(), courseIdFieldName)));
                }
            }
        };

        var enforceRoleInCourse = methods().that().areAnnotatedWith(EnforceRoleInCourse.class).or().areMetaAnnotatedWith(EnforceRoleInCourse.class).or().areDeclaredInClassesThat()
                .areAnnotatedWith(EnforceRoleInCourse.class).and().areDeclaredInClassesThat().areNotAnnotations().should(haveParameterWithAnnotation);

        enforceRoleInCourse.check(productionClasses);
    }

    @Test
    void testEnforceRoleInExerciseEndpointHasExerciseIdParameter() {
        ArchCondition<JavaMethod> haveParameterWithAnnotation = new ArchCondition<>("have a parameter with EnforceRoleInExercise annotation") {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                // Get annotation
                var enforceRoleInExerciseAnnotation = getAnnotation(EnforceRoleInExercise.class, method);
                var exerciseIdFieldName = enforceRoleInExerciseAnnotation.resourceIdFieldName();
                if (!hasParameterWithName(method, exerciseIdFieldName)) {
                    events.add(violated(method, String.format("Method %s does not have a parameter named %s", method.getFullName(), exerciseIdFieldName)));
                }
            }
        };

        var enforceRoleInExercise = methods().that().areAnnotatedWith(EnforceRoleInExercise.class).or().areMetaAnnotatedWith(EnforceRoleInExercise.class).or()
                .areDeclaredInClassesThat().areAnnotatedWith(EnforceRoleInExercise.class).and().areDeclaredInClassesThat().areNotAnnotations().should(haveParameterWithAnnotation);

        enforceRoleInExercise.check(productionClasses);
    }

    private boolean hasParameterWithName(JavaMethod method, String paramName) {
        try {
            var owner = method.getOwner();
            var javaClass = Class.forName(owner.getFullName());
            var javaMethod = javaClass.getMethod(method.getName(), method.getRawParameterTypes().stream().map(this::getClassForName).toArray(Class[]::new));
            return Arrays.stream(javaMethod.getParameters()).anyMatch(parameter -> parameter.getName().equals(paramName));
        }
        catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private Class<?> getClassForName(JavaClass paramClass) {
        try {
            return ClassUtils.getClass(paramClass.getName());
        }
        catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private <T extends Annotation> T getAnnotation(Class<T> clazz, JavaMethod javaMethod) {
        final var method = javaMethod.reflect();
        T annotation = method.getAnnotation(clazz);
        if (annotation != null) {
            return annotation;
        }
        for (Annotation a : method.getDeclaredAnnotations()) {
            annotation = a.annotationType().getAnnotation(clazz);
            if (annotation != null) {
                return annotation;
            }
        }

        annotation = method.getDeclaringClass().getAnnotation(clazz);
        if (annotation != null) {
            return annotation;
        }
        for (Annotation a : method.getDeclaringClass().getDeclaredAnnotations()) {
            annotation = a.annotationType().getAnnotation(clazz);
            if (annotation != null) {
                return annotation;
            }
        }

        return null;
    }

    @Test
    void testTransactional() {
        var classesPredicated = and(INTERFACES, annotatedWith(Repository.class));
        var transactionalRule = methods().that().areAnnotatedWith(simpleNameAnnotation("Transactional")).should().beDeclaredInClassesThat(classesPredicated);

        // TODO: In the future we should reduce this number and eventually replace it by transactionalRule.check(allClasses)
        // The following methods currently violate this rule:
        // Method <de.tum.in.www1.artemis.service.LectureImportService.importLecture(Lecture, Course)>
        // Method <de.tum.in.www1.artemis.service.exam.StudentExamService.generateMissingStudentExams(Exam)>
        // Method <de.tum.in.www1.artemis.service.exam.StudentExamService.generateStudentExams(Exam)>
        // Method <de.tum.in.www1.artemis.service.programming.ProgrammingExerciseImportBasicService.importProgrammingExerciseBasis(ProgrammingExercise, ProgrammingExercise)>
        // Method <de.tum.in.www1.artemis.service.tutorialgroups.TutorialGroupsConfigurationService.onTimeZoneUpdate(Course)>
        var result = transactionalRule.evaluate(allClasses);
        Assertions.assertThat(result.getFailureReport().getDetails()).hasSize(5);
    }

    // Custom Predicates for JavaAnnotations since ArchUnit only defines them for classes

    private DescribedPredicate<? super JavaAnnotation<?>> simpleNameAnnotation(String name) {
        return equalTo(name).as("Annotation with simple name " + name).onResultOf(annotation -> annotation.getRawType().getSimpleName());
    }

    private DescribedPredicate<? super JavaAnnotation<?>> resideInPackageAnnotation(String packageName) {
        return equalTo(packageName).as("Annotation in package " + packageName).onResultOf(annotation -> annotation.getRawType().getPackageName());
    }

    private DescribedPredicate<? super JavaCodeUnit> declaredClassSimpleName(String name) {
        return equalTo(name).as("Declared in class with simple name " + name).onResultOf(unit -> unit.getOwner().getSimpleName());
    }

    private ArchCondition<JavaMethod> notHaveAnyParameterAnnotatedWith(DescribedPredicate<? super JavaAnnotation<?>> annotationPredicate) {
        return new ArchCondition<>("not have parameters annotated with " + annotationPredicate.getDescription()) {

            @Override
            public void check(JavaMethod item, ConditionEvents events) {
                boolean satisfied = item.getParameterAnnotations().stream().flatMap(Collection::stream).noneMatch(annotationPredicate);
                if (!satisfied) {
                    events.add(violated(item, String.format("Method %s has parameter violating %s", item.getFullName(), annotationPredicate.getDescription())));
                }
            }
        };
    }

    private ArchCondition<JavaMethod> haveAllParametersAnnotatedWithUnless(DescribedPredicate<? super JavaAnnotation<?>> annotationPredicate,
            DescribedPredicate<JavaClass> exception) {
        return new ArchCondition<>("have all parameters annotated with " + annotationPredicate.getDescription()) {

            @Override
            public void check(JavaMethod item, ConditionEvents events) {
                boolean satisfied = item.getParameters().stream()
                        // Ignore annotations of the Pageable parameter
                        .filter(javaParameter -> !exception.test(javaParameter.getRawType())).map(JavaParameter::getAnnotations)
                        // Else, one of the annotations should match the given predicate
                        // This allows parameters with multiple annotations (e.g. @NonNull @Param)
                        .allMatch(annotations -> annotations.stream().anyMatch(annotationPredicate));
                if (!satisfied) {
                    events.add(violated(item, String.format("Method %s has parameter violating %s", item.getFullName(), annotationPredicate.getDescription())));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> beProfileAnnotated() {
        return new ArchCondition<>("be annotated with @Profile") {

            @Override
            public void check(JavaClass item, ConditionEvents events) {
                boolean hasProfileAnnotation = item.isAnnotatedWith(Profile.class);
                if (!hasProfileAnnotation) {
                    String message = String.format("Class %s is not annotated with @Profile", item.getFullName());
                    events.add(SimpleConditionEvent.violated(item, message));
                }
            }
        };
    }

    // See https://openjpa.apache.org/builds/1.2.3/apache-openjpa/docs/jpa_langref.html#jpa_langref_from_identifiers
    private static final Set<String> SQL_KEYWORDS = Set.of("SELECT", "UPDATE", "SET", "DELETE", "DISTINCT", "EXISTS", "FROM", "WHERE", "LEFT", "OUTER", "INNER", "JOIN", "FETCH",
            "TREAT", "AND", "OR", "AS", "ON", "ORDER", "BY", "ASC", "DSC", "GROUP", "COUNT", "SUM", "AVG", "MAX", "MIN", "IS", "NOT", "FALSE", "TRUE", "NULL", "LIKE", "IN",
            "BETWEEN", "HAVING", "EMPTY", "MEMBER", "OF", "UPPER", "LOWER", "TRIM");

    private ArchCondition<JavaMethod> useUpperCaseSQLStyle() {
        return new ArchCondition<>("have keywords in upper case") {

            @Override
            public void check(JavaMethod item, ConditionEvents events) {
                var queryAnnotation = item.getAnnotations().stream().filter(simpleNameAnnotation("Query")).findAny();
                if (queryAnnotation.isEmpty()) {
                    return;
                }
                Object valueProperty = queryAnnotation.get().getExplicitlyDeclaredProperty("value");
                if (!(valueProperty instanceof String query)) {
                    return;
                }
                String[] queryWords = query.split("[\\r\\n ]+");

                for (var word : queryWords) {
                    if (SQL_KEYWORDS.contains(word.toUpperCase()) && !StringUtils.isAllUpperCase(word)) {
                        events.add(violated(item, "In the Query of %s the keyword %s should be written in upper case.".formatted(item.getFullName(), word)));
                    }
                }
            }
        };
    }
}
