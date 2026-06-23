package de.tum.cit.aet.artemis.globalsearch;

import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertCourseExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.queryCourseProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.CourseSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

/**
 * Integration tests for course Weaviate indexing.
 * <p>
 * Verifies that courses are correctly upserted and deleted in Weaviate
 * when created, updated, or deleted.
 * <p>
 * Tests are skipped when Docker is not available or the Weaviate container failed to start.
 */
@EnabledIf("isWeaviateEnabled")
class CourseWeaviateIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "courseweaviateint";

    @Autowired
    private SearchableEntityWeaviateService searchableEntityWeaviateService;

    @Autowired
    private WeaviateService weaviateService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private Course course;

    static boolean isWeaviateEnabled() {
        return weaviateContainer != null && weaviateContainer.isRunning();
    }

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        // Pyris is not running in integration tests — stub the FAQ deletion to prevent PyrisConnectorException
        doNothing().when(pyrisFaqApi).deleteFaq(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpsertCourse_indexesInWeaviate() throws Exception {
        searchableEntityWeaviateService.upsertCourseAsync(CourseSearchableEntityDTO.fromCourse(course));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertCourseExistsInWeaviate(weaviateService, course));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpsertCourse_updatesPropertiesInWeaviate() throws Exception {
        searchableEntityWeaviateService.upsertCourseAsync(CourseSearchableEntityDTO.fromCourse(course));
        assertCourseExistsInWeaviate(weaviateService, course);

        // Update the course title
        course.setTitle("Updated Course Title");
        searchableEntityWeaviateService.upsertCourseAsync(CourseSearchableEntityDTO.fromCourse(course));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var properties = queryCourseProperties(weaviateService, course.getId());
            assertThat(properties).isNotNull();
            assertThat(properties.get(SearchableEntitySchema.Properties.TITLE)).isEqualTo("Updated Course Title");
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpsertCourse_storesShortNameAndDescription() throws Exception {
        course.setShortName("TST");
        course.setDescription("A test course description");
        searchableEntityWeaviateService.upsertCourseAsync(CourseSearchableEntityDTO.fromCourse(course));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var properties = queryCourseProperties(weaviateService, course.getId());
            assertThat(properties).isNotNull();
            assertThat(properties.get(SearchableEntitySchema.Properties.SHORT_NAME)).isEqualTo("TST");
            assertThat(properties.get(SearchableEntitySchema.Properties.DESCRIPTION)).isEqualTo("A test course description");
        });
    }
}
