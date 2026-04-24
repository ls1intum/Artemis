package de.tum.cit.aet.artemis.globalsearch;

import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertExamExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertExerciseExamDatesInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertExerciseExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.queryExamProperties;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.dto.ExamUpdateDTO;
import de.tum.cit.aet.artemis.exam.util.ExamFactory;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

/**
 * Integration tests verifying that exam create and update operations correctly index exams in Weaviate.
 * <p>
 * Tests are skipped when Docker is not available or the Weaviate container failed to start.
 */
@EnabledIf("isWeaviateEnabled")
class ExamWeaviateIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "examweaviateint";

    @Autowired
    private WeaviateService weaviateService;

    @Autowired
    private SearchableEntityWeaviateService searchableEntityWeaviateService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

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
    void testCreateExam_indexesExamInWeaviate() throws Exception {
        Exam exam = ExamFactory.generateExam(course, "weaviate-create-test");
        exam.setTitle("Weaviate Create Test Exam");

        Exam createdExam = request.postWithResponseBody("/api/exam/courses/" + course.getId() + "/exams", ExamUpdateDTO.of(exam), Exam.class, HttpStatus.CREATED);

        assertThat(createdExam.getId()).isNotNull();
        assertExamExistsInWeaviate(weaviateService, createdExam.getId());

        var properties = queryExamProperties(weaviateService, createdExam.getId());
        assertThat(properties).isNotNull();
        assertThat(properties.get(SearchableEntitySchema.Properties.TITLE)).isEqualTo("Weaviate Create Test Exam");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_updatesExamInWeaviate() throws Exception {
        Exam exam = examUtilService.addExam(course);
        examUtilService.addExamChannel(exam, "weaviate-update-test");

        // Verify the exam is initially not in Weaviate (it was created via the util, not the API)
        // Now create it via API so it gets indexed
        Exam examForCreate = ExamFactory.generateExam(course, "weaviate-update-create");
        Exam createdExam = request.postWithResponseBody("/api/exam/courses/" + course.getId() + "/exams", ExamUpdateDTO.of(examForCreate), Exam.class, HttpStatus.CREATED);
        assertExamExistsInWeaviate(weaviateService, createdExam.getId());

        // Update the exam title and dates
        createdExam.setTitle("Updated Weaviate Exam Title");
        ZonedDateTime newStartDate = createdExam.getStartDate().plusHours(1);
        createdExam.setStartDate(newStartDate);
        createdExam.setEndDate(newStartDate.plusHours(2));
        createdExam.setWorkingTime(createdExam.getDuration());

        Exam updatedExam = request.putWithResponseBody("/api/exam/courses/" + course.getId() + "/exams", ExamUpdateDTO.of(createdExam), Exam.class, HttpStatus.OK);

        assertExamExistsInWeaviate(weaviateService, updatedExam.getId());

        var properties = queryExamProperties(weaviateService, updatedExam.getId());
        assertThat(properties).isNotNull();
        assertThat(properties.get(SearchableEntitySchema.Properties.TITLE)).isEqualTo("Updated Weaviate Exam Title");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExamWorkingTime_updatesExamInWeaviate() throws Exception {
        // Create exam via API so it gets indexed
        Exam exam = ExamFactory.generateExam(course, "weaviate-wt-test");
        Exam createdExam = request.postWithResponseBody("/api/exam/courses/" + course.getId() + "/exams", ExamUpdateDTO.of(exam), Exam.class, HttpStatus.CREATED);
        assertExamExistsInWeaviate(weaviateService, createdExam.getId());

        ZonedDateTime originalEndDate = createdExam.getEndDate();
        int workingTimeChange = 600; // extend by 10 minutes

        Exam updatedExam = request.patchWithResponseBody("/api/exam/courses/" + course.getId() + "/exams/" + createdExam.getId() + "/working-time", workingTimeChange, Exam.class,
                HttpStatus.OK);

        assertThat(updatedExam.getEndDate()).isAfter(originalEndDate);
        assertExamExistsInWeaviate(weaviateService, updatedExam.getId());

        var properties = queryExamProperties(weaviateService, updatedExam.getId());
        assertThat(properties).isNotNull();
        assertThat(properties.get(SearchableEntitySchema.Properties.TITLE)).isEqualTo(createdExam.getTitle());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExamWorkingTime_updatesExerciseExamDatesInWeaviate() throws Exception {
        // Create an exam with exercises via util and seed them in Weaviate
        Exam exam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course);
        examUtilService.addExamChannel(exam, "weaviate-wt-ex-test");
        searchableEntityWeaviateService.upsertExamAsync(exam);
        searchableEntityWeaviateService.updateExamExercisesAsync(exam);

        // Verify exercises are initially indexed with original exam dates
        for (ExerciseGroup group : exam.getExerciseGroups()) {
            for (Exercise exercise : group.getExercises()) {
                assertExerciseExistsInWeaviate(weaviateService, exercise);
                assertExerciseExamDatesInWeaviate(weaviateService, exercise.getId(), exam);
            }
        }

        int workingTimeChange = 600; // extend by 10 minutes

        Exam updatedExam = request.patchWithResponseBody("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/working-time", workingTimeChange, Exam.class,
                HttpStatus.OK);

        // Verify exercises now reflect the updated exam end date
        for (ExerciseGroup group : exam.getExerciseGroups()) {
            for (Exercise exercise : group.getExercises()) {
                assertExerciseExamDatesInWeaviate(weaviateService, exercise.getId(), updatedExam);
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExamDates_updatesExerciseExamDatesInWeaviate() throws Exception {
        // Create an exam with exercises via util and seed them in Weaviate
        Exam exam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course);
        examUtilService.addExamChannel(exam, "weaviate-upd-ex-test");
        searchableEntityWeaviateService.upsertExamAsync(exam);
        searchableEntityWeaviateService.updateExamExercisesAsync(exam);

        for (ExerciseGroup group : exam.getExerciseGroups()) {
            for (Exercise exercise : group.getExercises()) {
                assertExerciseExistsInWeaviate(weaviateService, exercise);
            }
        }

        // Update exam dates via the update endpoint
        ZonedDateTime newStartDate = exam.getStartDate().plusHours(2);
        exam.setStartDate(newStartDate);
        exam.setEndDate(newStartDate.plusHours(3));
        exam.setWorkingTime(exam.getDuration());

        Exam updatedExam = request.putWithResponseBody("/api/exam/courses/" + course.getId() + "/exams", ExamUpdateDTO.of(exam), Exam.class, HttpStatus.OK);

        // Verify exercises now reflect the updated exam dates
        for (ExerciseGroup group : exam.getExerciseGroups()) {
            for (Exercise exercise : group.getExercises()) {
                assertExerciseExamDatesInWeaviate(weaviateService, exercise.getId(), updatedExam);
            }
        }
    }
}
