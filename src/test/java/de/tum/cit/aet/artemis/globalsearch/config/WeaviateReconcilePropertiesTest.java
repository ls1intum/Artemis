package de.tum.cit.aet.artemis.globalsearch.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;

/**
 * Unit tests for {@link WeaviateReconcileProperties}.
 */
class WeaviateReconcilePropertiesTest {

    private static WeaviateReconcileProperties withTypes(List<String> entityTypes) {
        return new WeaviateReconcileProperties(true, true, false, entityTypes, 500, 100, 200, 1000, 5, 100, 100, 0.25);
    }

    @Test
    void testRejectsUnknownEntityTypeAtStartup() {
        // A typo used to bind fine and then fail the missing pass on that type every tick, starving the types after it.
        assertThatThrownBy(() -> withTypes(List.of(SearchableEntitySchema.TypeValues.COURSE, "lectur"))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lectur");
    }

    @Test
    void testAcceptsEverySupportedEntityTypeIncludingOptInPosts() {
        List<String> types = List.of(SearchableEntitySchema.TypeValues.COURSE, SearchableEntitySchema.TypeValues.LECTURE, SearchableEntitySchema.TypeValues.LECTURE_UNIT,
                SearchableEntitySchema.TypeValues.EXAM, SearchableEntitySchema.TypeValues.EXERCISE, SearchableEntitySchema.TypeValues.FAQ,
                SearchableEntitySchema.TypeValues.CHANNEL, SearchableEntitySchema.TypeValues.POST, SearchableEntitySchema.TypeValues.ANSWER_POST);

        assertThat(withTypes(types).entityTypes()).containsExactlyElementsOf(types);
    }

    @Test
    void testKeepsAnImmutableCopyOfTheConfiguredTypes() {
        List<String> configured = new ArrayList<>(List.of(SearchableEntitySchema.TypeValues.COURSE));
        WeaviateReconcileProperties properties = withTypes(configured);
        configured.add(SearchableEntitySchema.TypeValues.FAQ);

        assertThat(properties.entityTypes()).containsExactly(SearchableEntitySchema.TypeValues.COURSE);
        assertThatThrownBy(() -> properties.entityTypes().add(SearchableEntitySchema.TypeValues.FAQ)).isInstanceOf(UnsupportedOperationException.class);
    }
}
