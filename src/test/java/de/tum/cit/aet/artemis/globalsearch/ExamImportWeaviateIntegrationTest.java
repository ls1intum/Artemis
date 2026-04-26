package de.tum.cit.aet.artemis.globalsearch;

import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertExamExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertExerciseExamDatesInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertExerciseExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.queryExamProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CREATED;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.dto.ExamImportDTO;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

/**
 * Integration tests verifying that exam imports correctly index exams and exercises in Weaviate.
 * <p>
 * Tests cover both full exam import ({@code POST /api/exam/courses/{courseId}/exam-import})
 * and exercise group import into an existing exam
 * ({@code POST /api/exam/courses/{courseId}/exams/{examId}/import-exercise-group}).
 * <p>
 * Tests are skipped when Docker is not available or the Weaviate container failed to start.
 */
@EnabledIf("isWeaviateEnabled")
class ExamImportWeaviateIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "eximweaviateint";

    @Autowired
    private WeaviateService weaviateService;

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
    void testImportExamWithExercises_indexesExamAndExercisesInWeaviate() throws Exception {
        Exam sourceExam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course);
        sourceExam.setChannelName("weaviate-import-test");
        ExamImportDTO importDTO = ExamImportDTO.of(sourceExam, course.getId());

        Exam importedExam = request.postWithResponseBody("/api/exam/courses/" + course.getId() + "/exam-import", importDTO, Exam.class, CREATED);

        assertThat(importedExam.getId()).isNotNull();
        assertThat(importedExam.getExerciseGroups()).hasSize(4);

        // Verify the exam itself is indexed in Weaviate
        assertExamExistsInWeaviate(weaviateService, importedExam.getId());

        var examProperties = queryExamProperties(weaviateService, importedExam.getId());
        assertThat(examProperties).isNotNull();
        assertThat(examProperties.get(SearchableEntitySchema.Properties.TITLE)).isEqualTo(importedExam.getTitle());

        // Verify all imported exercises are indexed in Weaviate with correct exam dates
        // Set up the course/exam chain that is not populated in the deserialized response
        importedExam.setCourse(course);
        for (ExerciseGroup group : importedExam.getExerciseGroups()) {
            group.setExam(importedExam);
            for (Exercise exercise : group.getExercises()) {
                assertExerciseExistsInWeaviate(weaviateService, exercise);
                assertExerciseExamDatesInWeaviate(weaviateService, exercise.getId(), importedExam);
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithoutExercises_indexesExamInWeaviate() throws Exception {
        Exam sourceExam = examUtilService.addExam(course);
        sourceExam.setChannelName("weaviate-empty-import");
        ExamImportDTO importDTO = ExamImportDTO.of(sourceExam, course.getId());

        Exam importedExam = request.postWithResponseBody("/api/exam/courses/" + course.getId() + "/exam-import", importDTO, Exam.class, CREATED);

        assertThat(importedExam.getId()).isNotNull();

        // Verify the exam is indexed even without exercises
        assertExamExistsInWeaviate(weaviateService, importedExam.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExerciseGroupsToExistingExam_indexesExercisesInWeaviate() throws Exception {
        // Create a target exam without exercises
        Exam targetExam = examUtilService.addExam(course);
        examUtilService.addExamChannel(targetExam, "weaviate-eg-import");

        // Create a source exam with exercises to import from
        Exam sourceExam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course);
        List<ExerciseGroup> exerciseGroupsToImport = sourceExam.getExerciseGroups();

        List<ExerciseGroup> importedGroups = request.postListWithResponseBody("/api/exam/courses/" + course.getId() + "/exams/" + targetExam.getId() + "/import-exercise-group",
                exerciseGroupsToImport, ExerciseGroup.class, HttpStatus.OK);

        // The target exam originally had no exercise groups, so all returned groups are newly imported
        // Filter to only the groups that actually have exercises (the empty group from source is skipped)
        List<ExerciseGroup> groupsWithExercises = importedGroups.stream().filter(g -> !g.getExercises().isEmpty()).toList();
        assertThat(groupsWithExercises).hasSize(4);

        // Verify the exam is indexed/updated in Weaviate
        assertExamExistsInWeaviate(weaviateService, targetExam.getId());

        // Verify all imported exercises are indexed in Weaviate
        // Set up the course/exam chain that is not populated in the deserialized response
        targetExam.setCourse(course);
        for (ExerciseGroup group : groupsWithExercises) {
            group.setExam(targetExam);
            for (Exercise exercise : group.getExercises()) {
                assertExerciseExistsInWeaviate(weaviateService, exercise);
                assertExerciseExamDatesInWeaviate(weaviateService, exercise.getId(), targetExam);
            }
        }
    }
}
