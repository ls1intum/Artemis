package de.tum.cit.aet.artemis.globalsearch;

import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertLectureExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertLectureNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertLectureUnitExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertLectureUnitNotInWeaviate;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
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
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertLectureExistsInWeaviate(weaviateService, lecture));

        long lectureId = lecture.getId();
        request.delete("/api/lecture/lectures/" + lectureId, HttpStatus.OK);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertLectureNotInWeaviate(weaviateService, lectureId));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteLecture_removesLectureUnitsFromWeaviate() throws Exception {
        Lecture lecture = lectureUtilService.createLecture(course);
        TextUnit textUnit = lectureUtilService.createTextUnit(lecture);
        searchableEntityWeaviateService.upsertLectureAsync(lecture);
        searchableEntityWeaviateService.upsertLectureUnitAsync(textUnit);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertLectureExistsInWeaviate(weaviateService, lecture);
            assertLectureUnitExistsInWeaviate(weaviateService, textUnit.getId());
        });

        long lectureId = lecture.getId();
        long textUnitId = textUnit.getId();
        request.delete("/api/lecture/lectures/" + lectureId, HttpStatus.OK);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertLectureNotInWeaviate(weaviateService, lectureId);
            assertLectureUnitNotInWeaviate(weaviateService, textUnitId);
        });
    }
}
