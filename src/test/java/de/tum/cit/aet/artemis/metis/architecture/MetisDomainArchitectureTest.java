package de.tum.cit.aet.artemis.metis.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;

/**
 * Keeps the Metis entities free of JPA associations, from both sides, and keeps every other module independent of Metis.
 * <p>
 * A Metis row names its parent, an exercise, a participation or a tutorial group, by id. A mapped association in either
 * direction would make the parent and the Metis row load together: an inverse one-to-one on the exercise cannot be lazy
 * without bytecode enhancement, so every load of an exercise would read metis_exercise_config, and a collection on the
 * parent is one more association every fetch has to leave out. {@code MetisLazyLoadingIntegrationTest} holds the runtime
 * side of the same statement, by reading the SQL that loading a parent or a Metis row executes.
 * <p>
 * No other module needs Metis, so the module has no {@code api} package, and an access test of the kind other modules have
 * would have no rule to apply. The second rule takes its place: no production class outside Metis depends on any class of
 * the module, its entities included. Deletion reaches the Metis rows through the cascades of the database, and the
 * configuration row of a new exercise is written without a Metis class.
 */
class MetisDomainArchitectureTest extends AbstractArchitectureTest {

    private static final String METIS_PACKAGE = ARTEMIS_PACKAGE + ".metis..";

    private static final String METIS_DOMAIN_PACKAGE = ARTEMIS_PACKAGE + ".metis.domain..";

    @Test
    void metisEntitiesReferenceTheirParentsByIdOnly() {
        fields().that().areDeclaredInClassesThat().resideInAPackage(METIS_DOMAIN_PACKAGE).should().notBeAnnotatedWith(OneToOne.class).andShould()
                .notBeAnnotatedWith(OneToMany.class).andShould().notBeAnnotatedWith(ManyToOne.class).andShould().notBeAnnotatedWith(ManyToMany.class).andShould()
                .notBeAnnotatedWith(ElementCollection.class).andShould().notBeAnnotatedWith(JoinColumn.class)
                .because("Metis rows reference their parents by id so that nothing is ever loaded together with an exercise, a participation or a tutorial group; "
                        + "the lazy-loading integration test holds the runtime side of this")
                .check(productionClasses);
    }

    @Test
    void nothingOutsideMetisDependsOnTheModule() {
        noClasses().that().resideOutsideOfPackage(METIS_PACKAGE).should().dependOnClassesThat().resideInAPackage(METIS_PACKAGE)
                .because("no other module needs Metis: the parent entities must not grow an inverse mapping towards Metis, and the module has no api package "
                        + "that other modules could use; one has to be added before any other module may call Metis")
                .check(productionClasses);
    }
}
