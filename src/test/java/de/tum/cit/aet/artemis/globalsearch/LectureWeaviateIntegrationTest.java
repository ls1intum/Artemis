package de.tum.cit.aet.artemis.globalsearch;

import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertLectureExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertLectureNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertLectureUnitExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertLectureUnitNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.queryLectureProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.lecture.dto.LectureSeriesCreateLectureDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

/**
 * Integration tests verifying that deleting a lecture via the REST API removes
 * the lecture and its lecture units from Weaviate.
 * <p>
 * Tests are skipped when Docker is not available or the Weaviate container failed to start.
 */
@EnabledIf("isWeaviateEnabled")
class LectureWeaviateIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "lecweaviateint";

    @Autowired
    private SearchableEntityWeaviateService searchableEntityWeaviateService;

    @Autowired
    private WeaviateService weaviateService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private ChannelService channelService;

    @Autowired
    private LectureRepository lectureRepository;

    private Course course;

    static boolean isWeaviateEnabled() {
        return weaviateContainer != null && weaviateContainer.isRunning();
    }

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteLecture_removesLectureFromWeaviate() throws Exception {
        Lecture lecture = lectureUtilService.createLecture(course);
        searchableEntityWeaviateService.upsertLectureAsync(lecture);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertLectureExistsInWeaviate(weaviateService, lecture));

        long lectureId = lecture.getId();
        request.delete("/api/lecture/lectures/" + lectureId, HttpStatus.OK);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertLectureNotInWeaviate(weaviateService, lectureId));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteLecture_removesLectureUnitsFromWeaviate() throws Exception {
        Lecture lecture = lectureUtilService.createLecture(course);
        TextUnit textUnit = lectureUtilService.createTextUnit(lecture);
        searchableEntityWeaviateService.upsertLectureAsync(lecture);
        searchableEntityWeaviateService.upsertLectureUnitAsync(textUnit);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertLectureExistsInWeaviate(weaviateService, lecture);
            assertLectureUnitExistsInWeaviate(weaviateService, textUnit.getId());
        });

        long lectureId = lecture.getId();
        long textUnitId = textUnit.getId();
        request.delete("/api/lecture/lectures/" + lectureId, HttpStatus.OK);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertLectureNotInWeaviate(weaviateService, lectureId);
            assertLectureUnitNotInWeaviate(weaviateService, textUnitId);
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateLectureSeries_indexesLecturesWithCorrectedTitlesInWeaviate() throws Exception {
        ZonedDateTime date1 = ZonedDateTime.now().minusDays(10);
        ZonedDateTime date2 = ZonedDateTime.now().minusDays(5);
        ZonedDateTime date3 = ZonedDateTime.now();

        // Set up two pre-existing lectures with default names and channels
        Lecture existingLecture1 = new Lecture();
        existingLecture1.setTitle("Lecture 1");
        existingLecture1.setCourse(course);
        existingLecture1.setStartDate(date1);
        existingLecture1 = lectureRepository.save(existingLecture1);
        channelService.createLectureChannel(existingLecture1, Optional.empty());

        Lecture existingLecture2 = new Lecture();
        existingLecture2.setTitle("Lecture 2");
        existingLecture2.setCourse(course);
        existingLecture2.setStartDate(date3);
        existingLecture2 = lectureRepository.save(existingLecture2);
        channelService.createLectureChannel(existingLecture2, Optional.empty());

        // Create a new lecture via series endpoint that sorts between the existing ones.
        // Title "Lecture 3" should be corrected to "Lecture 2" by correctDefaultLectureAndChannelNames.
        LectureSeriesCreateLectureDTO newLectureDTO = new LectureSeriesCreateLectureDTO("Lecture 3", date2, date2.plusHours(1));
        request.postWithoutResponseBody("/api/lecture/courses/" + course.getId() + "/lectures", List.of(newLectureDTO), HttpStatus.NO_CONTENT);

        // Find the newly created lecture (the one with start date = date2)
        Lecture newLecture = lectureRepository.findAllByCourseId(course.getId()).stream()
                .filter(l -> l.getStartDate() != null && l.getStartDate().toInstant().equals(date2.toInstant())).findFirst().orElseThrow();

        // Verify the title was corrected in the database
        assertThat(newLecture.getTitle()).isEqualTo("Lecture 2");

        // Verify Weaviate has the corrected title, not the original "Lecture 3"
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var properties = queryLectureProperties(weaviateService, newLecture.getId());
            assertThat(properties).as("New lecture should be indexed in Weaviate").isNotNull();
            assertThat(properties.get(SearchableEntitySchema.Properties.TITLE)).isEqualTo("Lecture 2");
        });
    }
}
