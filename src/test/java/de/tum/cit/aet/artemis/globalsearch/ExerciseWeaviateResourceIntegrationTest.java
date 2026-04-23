package de.tum.cit.aet.artemis.globalsearch;

import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertExerciseExistsInWeaviate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.ExerciseGroupRepository;
import de.tum.cit.aet.artemis.exam.util.ExamFactory;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.globalsearch.dto.GlobalSearchResultDTO;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;

/**
 * Integration tests for {@link de.tum.cit.aet.artemis.globalsearch.web.ExerciseWeaviateResource}
 * verifying role-based access control for exercise search endpoints.
 * <p>
 * Tests verify:
 * - Students cannot see exam exercises before the exam has started
 * - Students can see exam exercises once the exam has started
 * - Students cannot see unreleased exercises
 * - Tutors cannot see exam exercises before the exam has ended
 * - Tutors can see exam exercises after the exam has ended
 * - Editors and instructors can see all exercises at any time
 */
@EnabledIf("isWeaviateEnabled")
class ExerciseWeaviateResourceIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "exweaviateres";

    @Autowired
    private SearchableEntityWeaviateService searchableEntityWeaviateService;

    @Autowired
    private WeaviateService weaviateService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ExerciseGroupRepository exerciseGroupRepository;

    private Course course;

    private ProgrammingExercise releasedExercise;

    private ProgrammingExercise unreleasedExercise;

    private TextExercise notStartedExamExercise;

    private TextExercise ongoingExamExercise;

    private TextExercise endedExamExercise;

    static boolean isWeaviateEnabled() {
        return weaviateContainer != null && weaviateContainer.isRunning();
    }

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        // Create course with a released programming exercise
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        releasedExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        releasedExercise.setTitle("WeaviateSearchable Released Exercise");
        releasedExercise.setReleaseDate(ZonedDateTime.now().minusDays(1));
        exerciseRepository.save(releasedExercise);

        // Create an unreleased exercise in the same course (release date in the future)
        unreleasedExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course, false);
        unreleasedExercise.setTitle("WeaviateSearchable Unreleased Exercise");
        unreleasedExercise.setReleaseDate(ZonedDateTime.now().plusDays(7));
        exerciseRepository.save(unreleasedExercise);

        // Create an exam exercise where the exam has NOT started yet (start date in the future)
        Exam notStartedExam = ExamFactory.generateExam(course, ZonedDateTime.now().minusHours(1), ZonedDateTime.now().plusHours(1), ZonedDateTime.now().plusHours(3), false);
        notStartedExam = examRepository.save(notStartedExam);
        var notStartedExerciseGroup = ExamFactory.generateExerciseGroup(true, notStartedExam);
        notStartedExerciseGroup = exerciseGroupRepository.save(notStartedExerciseGroup);
        notStartedExamExercise = TextExerciseFactory.generateTextExerciseForExam(notStartedExerciseGroup);
        notStartedExamExercise.setTitle("WeaviateSearchable NotStarted Exam Exercise");
        notStartedExamExercise = exerciseRepository.save(notStartedExamExercise);

        // Create an exam exercise where the exam is ongoing (started but not ended)
        Exam ongoingExam = ExamFactory.generateExam(course, ZonedDateTime.now().minusHours(2), ZonedDateTime.now().minusHours(1), ZonedDateTime.now().plusHours(1), false);
        ongoingExam = examRepository.save(ongoingExam);
        var ongoingExerciseGroup = ExamFactory.generateExerciseGroup(true, ongoingExam);
        ongoingExerciseGroup = exerciseGroupRepository.save(ongoingExerciseGroup);
        ongoingExamExercise = TextExerciseFactory.generateTextExerciseForExam(ongoingExerciseGroup);
        ongoingExamExercise.setTitle("WeaviateSearchable Ongoing Exam Exercise");
        ongoingExamExercise = exerciseRepository.save(ongoingExamExercise);

        // Create an exam exercise where the exam has already ended
        Exam endedExam = ExamFactory.generateExam(course, ZonedDateTime.now().minusDays(3), ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusDays(1), false);
        endedExam = examRepository.save(endedExam);
        var endedExerciseGroup = ExamFactory.generateExerciseGroup(true, endedExam);
        endedExerciseGroup = exerciseGroupRepository.save(endedExerciseGroup);
        endedExamExercise = TextExerciseFactory.generateTextExerciseForExam(endedExerciseGroup);
        endedExamExercise.setTitle("WeaviateSearchable Ended Exam Exercise");
        endedExamExercise = exerciseRepository.save(endedExamExercise);

        // Index all exercises in Weaviate
        searchableEntityWeaviateService.upsertExerciseAsync(releasedExercise);
        searchableEntityWeaviateService.upsertExerciseAsync(unreleasedExercise);
        searchableEntityWeaviateService.upsertExerciseAsync(notStartedExamExercise);
        searchableEntityWeaviateService.upsertExerciseAsync(ongoingExamExercise);
        searchableEntityWeaviateService.upsertExerciseAsync(endedExamExercise);

        // Wait for all exercises to be indexed
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertExerciseExistsInWeaviate(weaviateService, releasedExercise);
            assertExerciseExistsInWeaviate(weaviateService, unreleasedExercise);
            assertExerciseExistsInWeaviate(weaviateService, notStartedExamExercise);
            assertExerciseExistsInWeaviate(weaviateService, ongoingExamExercise);
            assertExerciseExistsInWeaviate(weaviateService, endedExamExercise);
        });
    }

    private List<String> getResultTitles(List<GlobalSearchResultDTO> results) {
        return results.stream().map(GlobalSearchResultDTO::title).toList();
    }

    @Nested
    class GlobalSearchEndpointTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testStudentCannotSeeNotStartedExamExercises() throws Exception {
            var results = request.getList("/api/search?q=WeaviateSearchable&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).doesNotContain("WeaviateSearchable NotStarted Exam Exercise");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testStudentCanSeeStartedExamExercises() throws Exception {
            var results = request.getList("/api/search?q=WeaviateSearchable&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).contains("WeaviateSearchable Ongoing Exam Exercise", "WeaviateSearchable Ended Exam Exercise");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testStudentCannotSeeUnreleasedExercises() throws Exception {
            var results = request.getList("/api/search?q=WeaviateSearchable&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).contains("WeaviateSearchable Released Exercise");
            assertThat(titles).doesNotContain("WeaviateSearchable Unreleased Exercise");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void testTutorCannotSeeNotEndedExamExercises() throws Exception {
            var results = request.getList("/api/search?q=WeaviateSearchable&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).doesNotContain("WeaviateSearchable NotStarted Exam Exercise", "WeaviateSearchable Ongoing Exam Exercise");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void testTutorCanSeeEndedExamExercises() throws Exception {
            var results = request.getList("/api/search?q=WeaviateSearchable&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).contains("WeaviateSearchable Ended Exam Exercise");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void testTutorCanSeeUnreleasedExercises() throws Exception {
            var results = request.getList("/api/search?q=WeaviateSearchable&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).contains("WeaviateSearchable Unreleased Exercise");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void testEditorCanSeeAllExercises() throws Exception {
            var results = request.getList("/api/search?q=WeaviateSearchable&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).contains("WeaviateSearchable Released Exercise", "WeaviateSearchable Unreleased Exercise", "WeaviateSearchable NotStarted Exam Exercise",
                    "WeaviateSearchable Ongoing Exam Exercise", "WeaviateSearchable Ended Exam Exercise");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testInstructorCanSeeAllExercises() throws Exception {
            var results = request.getList("/api/search?q=WeaviateSearchable&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).contains("WeaviateSearchable Released Exercise", "WeaviateSearchable Unreleased Exercise", "WeaviateSearchable NotStarted Exam Exercise",
                    "WeaviateSearchable Ongoing Exam Exercise", "WeaviateSearchable Ended Exam Exercise");
        }
    }

    @Nested
    class GlobalSearchWithoutCourseIdTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testGlobalSearchStudentFiltersCorrectly() throws Exception {
            // Use a high limit because the Weaviate collection is shared across test classes and may contain exercises from other tests
            var results = request.getList("/api/search?q=WeaviateSearchable&limit=100", HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).contains("WeaviateSearchable Released Exercise");
            assertThat(titles).doesNotContain("WeaviateSearchable Unreleased Exercise", "WeaviateSearchable NotStarted Exam Exercise");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testGlobalSearchInstructorSeesAll() throws Exception {
            // Use a high limit because the Weaviate collection is shared across test classes and may contain exercises from other tests
            var results = request.getList("/api/search?q=WeaviateSearchable&limit=100", HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).contains("WeaviateSearchable Released Exercise", "WeaviateSearchable Unreleased Exercise", "WeaviateSearchable NotStarted Exam Exercise",
                    "WeaviateSearchable Ongoing Exam Exercise", "WeaviateSearchable Ended Exam Exercise");
        }
    }

    @Nested
    class ExerciseSearchEndpointTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testStudentCannotSeeNotStartedExamOrUnreleasedExercises() throws Exception {
            var results = request.getList("/api/exercises/search?q=WeaviateSearchable&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).doesNotContain("WeaviateSearchable NotStarted Exam Exercise", "WeaviateSearchable Unreleased Exercise");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testStudentCanSeeStartedExamExercises() throws Exception {
            var results = request.getList("/api/exercises/search?q=WeaviateSearchable&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).contains("WeaviateSearchable Ongoing Exam Exercise", "WeaviateSearchable Ended Exam Exercise");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void testEditorCanSeeAllExercises() throws Exception {
            var results = request.getList("/api/exercises/search?q=WeaviateSearchable&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).contains("WeaviateSearchable Released Exercise", "WeaviateSearchable Unreleased Exercise", "WeaviateSearchable NotStarted Exam Exercise",
                    "WeaviateSearchable Ongoing Exam Exercise", "WeaviateSearchable Ended Exam Exercise");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testEmptyQueryReturnsBadRequest() throws Exception {
            request.getList("/api/exercises/search?q=&courseId=" + course.getId(), HttpStatus.BAD_REQUEST, GlobalSearchResultDTO.class);
        }
    }

}
