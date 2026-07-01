package de.tum.cit.aet.artemis.math;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.dto.MathExerciseDTO;
import de.tum.cit.aet.artemis.math.repository.MathExerciseRepository;
import de.tum.cit.aet.artemis.math.util.MathExerciseFactory;
import de.tum.cit.aet.artemis.math.util.MathExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class MathExerciseIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "mathexercise";

    @Autowired
    private MathExerciseRepository mathExerciseRepository;

    @Autowired
    private MathExerciseUtilService mathExerciseUtilService;

    @Autowired
    private UserUtilService userUtilService;

    private Course course;

    private MathExercise exercise;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        course = mathExerciseUtilService.addCourseWithMathExercise();
        exercise = (MathExercise) course.getExercises().iterator().next();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createMathExercise_asInstructor_returnsCreated() throws Exception {
        MathExerciseDTO newExercise = MathExerciseFactory.generateMathExerciseDTO(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1),
                ZonedDateTime.now().plusDays(2), course);

        MathExerciseDTO result = request.postWithResponseBody("/api/math/math-exercises", newExercise, MathExerciseDTO.class, HttpStatus.CREATED);

        assertThat(result).isNotNull();
        assertThat(result.id()).isNotNull();
        assertThat(result.description()).isEqualTo(newExercise.description());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createMathExercise_asStudent_returnsForbidden() throws Exception {
        MathExerciseDTO newExercise = MathExerciseFactory.generateMathExerciseDTO(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1),
                ZonedDateTime.now().plusDays(2), course);

        request.postWithResponseBody("/api/math/math-exercises", newExercise, MathExerciseDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createMathExercise_withExistingId_returnsBadRequest() throws Exception {
        MathExerciseDTO exerciseWithId = MathExerciseDTO.of(exercise);

        request.postWithResponseBody("/api/math/math-exercises", exerciseWithId, MathExerciseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getMathExercise_asTutor_returnsOk() throws Exception {
        MathExerciseDTO result = request.get("/api/math/math-exercises/" + exercise.getId(), HttpStatus.OK, MathExerciseDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(exercise.getId());
        assertThat(result.description()).isEqualTo(exercise.getDescription());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getMathExercise_asStudent_returnsForbidden() throws Exception {
        request.get("/api/math/math-exercises/" + exercise.getId(), HttpStatus.FORBIDDEN, MathExerciseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getMathExercisesForCourse_returnsList() throws Exception {
        var results = request.getList("/api/math/courses/" + course.getId() + "/math-exercises", HttpStatus.OK, MathExerciseDTO.class);

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().id()).isEqualTo(exercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateMathExercise_asInstructor_returnsOk() throws Exception {
        exercise.setDescription("Updated description");
        MathExerciseDTO updateDTO = MathExerciseDTO.of(exercise);

        MathExerciseDTO result = request.putWithResponseBody("/api/math/math-exercises", updateDTO, MathExerciseDTO.class, HttpStatus.OK);

        assertThat(result.description()).isEqualTo("Updated description");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteMathExercise_asInstructor_returnsOk() throws Exception {
        request.delete("/api/math/math-exercises/" + exercise.getId(), HttpStatus.OK);

        assertThat(mathExerciseRepository.findById(exercise.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteMathExercise_asStudent_returnsForbidden() throws Exception {
        request.delete("/api/math/math-exercises/" + exercise.getId(), HttpStatus.FORBIDDEN);
        // the exercise must still exist after a forbidden delete
        assertThat(mathExerciseRepository.findById(exercise.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMathExercise_asInstructor_returnsCreated() throws Exception {
        MathExerciseDTO importTarget = MathExerciseFactory.generateMathExerciseDTO(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1),
                ZonedDateTime.now().plusDays(2), course);

        MathExerciseDTO result = request.postWithResponseBody("/api/math/math-exercises/import?sourceExerciseId=" + exercise.getId(), importTarget, MathExerciseDTO.class,
                HttpStatus.CREATED);

        assertThat(result).isNotNull();
        assertThat(result.id()).isNotEqualTo(exercise.getId());
        assertThat(result.description()).isEqualTo(exercise.getDescription());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMathExercise_preservesManualDerivation() throws Exception {
        MathExerciseDTO importTarget = new MathExerciseDTO(null, "Imported Math Exercise", null, "Prove that 0 + x = x.", "Prove that 0 + x = x", "Apply add_zero_left.", null,
                null, 10.0, 0.0, IncludedInOverallScore.INCLUDED_COMPLETELY, false, false, false, false, null, ZonedDateTime.now().minusDays(1), null,
                ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2), null, course.getId(), true);

        MathExerciseDTO result = request.postWithResponseBody("/api/math/math-exercises/import?sourceExerciseId=" + exercise.getId(), importTarget, MathExerciseDTO.class,
                HttpStatus.CREATED);

        assertThat(result).isNotNull();
        assertThat(result.manualDerivation()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMathExercise_copiesExampleSubmissionInsteadOfSharing() throws Exception {
        ExampleSubmission originalExampleSubmission = mathExerciseUtilService.addExampleSubmissionToMathExercise(exercise, "example work");
        long originalSubmissionId = originalExampleSubmission.getSubmission().getId();

        MathExerciseDTO importTarget = new MathExerciseDTO(null, "Imported With Example", null, "Prove that 0 + x = x.", "Prove that 0 + x = x", "Apply add_zero_left.", null, null,
                10.0, 0.0, IncludedInOverallScore.INCLUDED_COMPLETELY, false, false, false, false, null, ZonedDateTime.now().minusDays(1), null, ZonedDateTime.now().plusDays(1),
                ZonedDateTime.now().plusDays(2), null, course.getId(), false);

        // Import must succeed: sharing the template's submission would violate the unique @OneToOne constraint on ExampleSubmission.submission.
        MathExerciseDTO result = request.postWithResponseBody("/api/math/math-exercises/import?sourceExerciseId=" + exercise.getId(), importTarget, MathExerciseDTO.class,
                HttpStatus.CREATED);

        MathExercise imported = mathExerciseRepository.findByIdWithCourseAndExampleSubmissions(result.id()).orElseThrow();
        assertThat(imported.getExampleSubmissions()).hasSize(1);
        Submission copiedSubmission = imported.getExampleSubmissions().iterator().next().getSubmission();
        assertThat(copiedSubmission).isNotNull();
        // The copy must be a distinct submission, not the shared template one, so deleting either exercise cannot cascade-remove the other's data.
        assertThat(copiedSubmission.getId()).isNotEqualTo(originalSubmissionId);
        assertThat(((MathSubmission) copiedSubmission).getContent()).isEqualTo("example work");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMathExercise_copiesExampleSubmissionAssessment() throws Exception {
        ExampleSubmission originalExampleSubmission = mathExerciseUtilService.addExampleSubmissionWithAssessmentToMathExercise(exercise, "assessed work", 7.0, "good derivation");
        long originalResultId = originalExampleSubmission.getSubmission().getResults().getFirst().getId();

        MathExerciseDTO importTarget = new MathExerciseDTO(null, "Imported With Assessment", null, "Prove that 0 + x = x.", "Prove that 0 + x = x", "Apply add_zero_left.", null,
                null, 10.0, 0.0, IncludedInOverallScore.INCLUDED_COMPLETELY, false, false, false, false, null, ZonedDateTime.now().minusDays(1), null, ZonedDateTime.now().plusDays(1),
                ZonedDateTime.now().plusDays(2), null, course.getId(), false);

        MathExerciseDTO result = request.postWithResponseBody("/api/math/math-exercises/import?sourceExerciseId=" + exercise.getId(), importTarget, MathExerciseDTO.class,
                HttpStatus.CREATED);

        MathExercise imported = mathExerciseRepository.findByIdWithCourseAndExampleSubmissions(result.id()).orElseThrow();
        Submission copiedSubmission = imported.getExampleSubmissions().iterator().next().getSubmission();
        // The multi-join fetch must yield exactly one (non-duplicated) result, copied as a distinct entity with its feedback and score.
        assertThat(copiedSubmission.getResults()).hasSize(1);
        Result copiedResult = copiedSubmission.getResults().getFirst();
        assertThat(copiedResult.getId()).isNotEqualTo(originalResultId);
        assertThat(copiedResult.getScore()).isEqualTo(7.0);
        assertThat(copiedResult.getFeedbacks()).hasSize(1);
        assertThat(copiedResult.getFeedbacks().iterator().next().getDetailText()).isEqualTo("good derivation");
    }
}
