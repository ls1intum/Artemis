package de.tum.cit.aet.artemis.core.service.featureusage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.domain.JavaClass;

import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;

/**
 * Keeps the curated feature catalogue in step with the controllers that actually exist.
 * <p>
 * The catalogue is what the admin page navigates, so an omission is not a cosmetic problem: usage of an uncatalogued
 * endpoint is reported under a nondescript "other" bucket instead of the feature it belongs to, and nobody would notice.
 * These assertions turn both directions of drift into a build failure, which is the only reason the catalogue can be
 * trusted to be complete.
 * <p>
 * Uses ArchUnit's class scanning rather than reflection over class literals, because
 * {@code ArchitectureTest.testNoRestControllersImported} forbids importing a {@code @RestController}. That is also why the
 * catalogue is keyed by simple name, and why this test is needed to catch the typo a string key allows.
 */
class FeatureUsageCatalogueTest extends AbstractArchitectureTest {

    @Test
    void everyControllerShouldBeCatalogued() {
        Set<String> uncatalogued = artemisControllerNames().stream().filter(name -> FeatureUsageCatalogue.labelFor(name).isEmpty()).collect(Collectors.toCollection(TreeSet::new));

        assertThat(uncatalogued).as("""
                These REST controllers are not in FeatureUsageCatalogue, so their usage would be reported under "other" \
                instead of a named feature. Add each one to the area and feature it belongs to.""").isEmpty();
    }

    @Test
    void everyCataloguedControllerShouldExist() {
        Set<String> stale = FeatureUsageCatalogue.catalogedControllers().stream().filter(name -> !artemisControllerNames().contains(name))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(stale).as("""
                These FeatureUsageCatalogue entries name a controller that no longer exists, so they are either a typo or \
                left over from a rename or deletion.""").isEmpty();
    }

    @Test
    void everyLabelShouldBeAnAreaAndAFeatureInKebabCase() {
        Set<String> malformed = artemisControllerNames().stream().map(FeatureUsageCatalogue::labelFor).flatMap(Optional::stream)
                .filter(label -> !label.matches("[a-z0-9]+(-[a-z0-9]+)*/[a-z0-9]+(-[a-z0-9]+)*")).collect(Collectors.toCollection(TreeSet::new));

        // the page splits the label on the slash to build the tree, so exactly one level of nesting is expected
        assertThat(malformed).as("Labels must be \"area/feature\" in kebab-case").isEmpty();
    }

    @Test
    void shouldKeepTheTaxonomyAtAReadableSize() {
        Set<String> labels = artemisControllerNames().stream().map(FeatureUsageCatalogue::labelFor).flatMap(Optional::stream).collect(Collectors.toSet());
        Set<String> areas = labels.stream().map(label -> label.substring(0, label.indexOf('/'))).collect(Collectors.toSet());

        // Deliberately loose bounds. The point is not the exact number but that the tree stays navigable: collapsing to a
        // handful of features would stop answering the sub-feature question, and one feature per endpoint would just be the
        // raw endpoint list again.
        assertThat(labels).as("features in the catalogue").hasSizeBetween(100, 260);
        assertThat(areas).as("distinct area names").hasSizeBetween(20, 120);
    }

    @Test
    void shouldGroupSeveralControllersIntoOneFeatureWhereTheSplitIsAnImplementationDetail() {
        // the five programming exercise CRUD controllers are one thing an instructor does
        assertThat(FeatureUsageCatalogue.labelFor("ProgrammingExerciseCreationResource")).contains("authoring/exercise-management");
        assertThat(FeatureUsageCatalogue.labelFor("ProgrammingExerciseUpdateResource")).contains("authoring/exercise-management");
        assertThat(FeatureUsageCatalogue.labelFor("ProgrammingExercisePartialUpdateResource")).contains("authoring/exercise-management");
        assertThat(FeatureUsageCatalogue.labelFor("ProgrammingExerciseDeletionResource")).contains("authoring/exercise-management");
        assertThat(FeatureUsageCatalogue.labelFor("ProgrammingExerciseRetrievalResource")).contains("authoring/exercise-management");
    }

    private Set<String> artemisControllerNames() {
        return productionClasses.stream().filter(javaClass -> javaClass.isAnnotatedWith(RestController.class)).map(JavaClass::getSimpleName).collect(Collectors.toSet());
    }
}
