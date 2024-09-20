package de.tum.cit.aet.artemis.shared.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.and;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.INTERFACES;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.core.domain.properties.HasType.Predicates.rawType;
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.core.repository.base.RepositoryImpl;

/**
 * This class contains architecture tests for the persistence layer.
 */
class RepositoryArchitectureTest extends AbstractArchitectureTest {

    @Test
    void shouldBeNamedRepository() {
        ArchRule rule = classes().that().areAnnotatedWith(Repository.class).should().haveSimpleNameEndingWith("Repository")
                .because("repositories should have a name ending with 'Repository'.");
        rule.check(productionClasses);
    }

    @Test
    void shouldBeInRepositoryPackage() {
        ArchRule rule = classes().that().areAnnotatedWith(Repository.class).should().resideInAPackage("..repository..")
                .because("repositories should be in the package 'repository'.");
        rule.check(productionClasses);
    }

    @Test
    void testJPQLStyle() {
        var queryRule = methods().that().areAnnotatedWith(Query.class).should(USE_UPPER_CASE_SQL_STYLE).because("@Query content should follow the style guide");
        queryRule.check(allClasses);
    }

    // See https://openjpa.apache.org/builds/1.2.3/apache-openjpa/docs/jpa_langref.html#jpa_langref_from_identifiers
    private static final Set<String> SQL_KEYWORDS = Set.of("SELECT", "UPDATE", "SET", "DELETE", "DISTINCT", "EXISTS", "FROM", "WHERE", "LEFT", "OUTER", "INNER", "JOIN", "FETCH",
            "TREAT", "AND", "OR", "AS", "ON", "ORDER", "BY", "ASC", "DSC", "GROUP", "COUNT", "SUM", "AVG", "MAX", "MIN", "IS", "NOT", "FALSE", "TRUE", "NULL", "LIKE", "IN",
            "BETWEEN", "HAVING", "EMPTY", "MEMBER", "OF", "UPPER", "LOWER", "TRIM");

    private static final ArchCondition<JavaMethod> USE_UPPER_CASE_SQL_STYLE = new ArchCondition<>("have keywords in upper case") {

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
                    events.add(violated(item, "In the Query of %s the keyword \"%s\" should be written in upper case.".formatted(item.getFullName(), word)));
                }
            }
        }
    };

    @Test
    void testNoUnusedRepositoryMethods() {
        ArchRule unusedMethods = noMethods().that().areDeclaredInClassesThat().areInterfaces().and().areDeclaredInClassesThat().areAnnotatedWith(Repository.class)
                .should(new ArchCondition<>("not be referenced") {

                    @Override
                    public void check(JavaMethod javaMethod, ConditionEvents conditionEvents) {
                        var calls = javaMethod.getAccessesToSelf();
                        if (calls.isEmpty()) {
                            conditionEvents.add(SimpleConditionEvent.violated(javaMethod, "Method is not used"));
                        }
                    }
                }).because("unused methods should be removed from repositories to keep a clean code base.");
        unusedMethods.check(allClasses);
    }

    @Test
    void testNoEntityGraphsOnQueries() {
        ArchRule noEntityGraphsOnQueries = noMethods().that().areAnnotatedWith(Query.class).and().areDeclaredInClassesThat().areInterfaces().and().areDeclaredInClassesThat()
                .areAnnotatedWith(Repository.class).should().beAnnotatedWith(EntityGraph.class)
                .because("Spring Boot 3 ignores EntityGraphs on JPQL queries. You need to integrate a JOIN FETCH into the query.");
        noEntityGraphsOnQueries.check(productionClasses);
    }

    @Test
    void testRepositoryParamAnnotation() {
        var useParamInQueries = methods().that().areAnnotatedWith(Query.class).should(haveAllParametersAnnotatedWithUnless(rawType(Param.class), type(Pageable.class)));
        var notUseParamOutsideQueries = methods().that().areNotAnnotatedWith(Query.class).should(notHaveAnyParameterAnnotatedWith(rawType(Param.class)));
        useParamInQueries.check(productionClasses);
        notUseParamOutsideQueries.check(productionClasses);
    }

    @Test
    void persistenceShouldNotAccessServices() {
        noClasses().that().areAnnotatedWith(Repository.class).should().accessClassesThat().areAnnotatedWith(Service.class).check(allClasses);
    }

    @Test
    void testTransactional() {
        var classesPredicated = and(INTERFACES, annotatedWith(Repository.class));
        var transactionalRule = methods().that().areAnnotatedWith(simpleNameAnnotation("Transactional")).should().beDeclaredInClassesThat(classesPredicated);

        // TODO: In the future we should reduce this number and eventually replace it by transactionalRule.check(allClasses)
        // The following methods currently violate this rule:
        // Method <de.tum.cit.aet.artemis.service.LectureImportService.importLecture(Lecture, Course)>
        // Method <de.tum.cit.aet.artemis.service.exam.StudentExamService.generateMissingStudentExams(Exam)>
        // Method <de.tum.cit.aet.artemis.service.exam.StudentExamService.generateStudentExams(Exam)>
        // Method <de.tum.cit.aet.artemis.service.programming.ProgrammingExerciseImportBasicService.importProgrammingExerciseBasis(ProgrammingExercise, ProgrammingExercise)>
        // Method <de.tum.cit.aet.artemis.service.tutorialgroups.TutorialGroupsConfigurationService.onTimeZoneUpdate(Course)>
        var result = transactionalRule.evaluate(allClasses);
        assertThat(result.getFailureReport().getDetails()).hasSize(5);
    }

    @Test
    void testOnlySpringTransactionalAnnotation() {
        ArchRule onlySpringTransactionalAnnotation = noMethods().should().beAnnotatedWith(javax.transaction.Transactional.class).orShould()
                .beAnnotatedWith(jakarta.transaction.Transactional.class)
                .because("Only Spring's Transactional annotation should be used as the usage of the other two is not reliable.");
        onlySpringTransactionalAnnotation.check(allClasses);
    }

    @Test
    void repositoriesImplementArtemisJpaRepository() {
        classes().that().areAssignableTo(JpaRepository.class).and().doNotBelongToAnyOf(RepositoryImpl.class).should().beAssignableTo(ArtemisJpaRepository.class).check(allClasses);
    }

    @Test
    void orElseThrowShouldNotBeCalled() {
        noClasses().that().areAssignableTo(ArtemisJpaRepository.class).should().callMethod(Optional.class, "orElseThrow").orShould()
                .callMethod(Optional.class, "orElseThrow", Supplier.class).because("ArtemisJpaRepository offers the method getValueElseThrow for this use case").check(allClasses);

    }

    @Test
    void usedInProductionCode() {
        var excludedMethods = Set.of("de.tum.cit.aet.artemis.core.repository.CustomAuditEventRepository.find(java.lang.String, java.time.Instant, java.lang.String)");
        methods().that().areDeclaredInClassesThat().areAnnotatedWith(Repository.class).and().areDeclaredInClassesThat(new DescribedPredicate<>("") {

            @Override
            public boolean test(JavaClass javaClass) {
                return productionClasses.contain(javaClass.getName());
            }
        }).should(new ArchCondition<>("be used by production code") {

            @Override
            public void check(JavaMethod javaMethod, ConditionEvents conditionEvents) {
                if (excludedMethods.contains(javaMethod.getFullName())) {
                    return;
                }

                var calls = javaMethod.getAccessesToSelf();
                var productionCalls = calls.stream().filter(call -> productionClasses.contain(call.getOriginOwner().getName())).collect(Collectors.toSet());
                if (productionCalls.isEmpty() && !calls.isEmpty()) {
                    conditionEvents.add(SimpleConditionEvent.violated(javaMethod, "Method " + javaMethod.getFullName() + " is not used in production code"));
                }
            }
        }).because("methods that are not used in production code should be moved to test repositories").check(allClasses);
    }
}
