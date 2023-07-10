package de.tum.in.www1.artemis;

import static com.tngtech.archunit.base.DescribedPredicate.*;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.*;
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.*;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import java.util.*;
import java.util.stream.Collectors;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.*;

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

    // Custom Predicates for JavaAnnotations since ArchUnit only defines them for classes

    private DescribedPredicate<? super JavaAnnotation<?>> simpleNameAnnotation(String name) {
        return equalTo(name).as("Annotation with simple name " + name).onResultOf(annotation -> annotation.getRawType().getSimpleName());
    }

    private DescribedPredicate<? super JavaAnnotation<?>> resideInPackageAnnotation(String packageName) {
        return equalTo(packageName).as("Annotation in package " + packageName).onResultOf(annotation -> annotation.getRawType().getPackageName());
    }

    private ArchCondition<JavaMethod> notHaveAnyParameterAnnotatedWith(DescribedPredicate<? super JavaAnnotation<?>> annotationPredicate) {
        return new ArchCondition<>("have parameters annotated with ") {

            @Override
            public void check(JavaMethod item, ConditionEvents events) {
                boolean satisfied = item.getParameterAnnotations().stream().flatMap(Collection::stream).noneMatch(annotationPredicate);
                if (!satisfied) {
                    events.add(violated(item, String.format("Method %s has parameter violating %s", item.getFullName(), annotationPredicate.getDescription())));
                }
            }
        };
    }
}
