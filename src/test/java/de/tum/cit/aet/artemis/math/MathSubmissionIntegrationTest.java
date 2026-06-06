package de.tum.cit.aet.artemis.math;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.dto.MathSubmissionDTO;
import de.tum.cit.aet.artemis.math.repository.MathSubmissionRepository;
import de.tum.cit.aet.artemis.math.util.MathExerciseFactory;
import de.tum.cit.aet.artemis.math.util.MathExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class MathSubmissionIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "mathsubmission";

    @Autowired
    private MathExerciseUtilService mathExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private StudentParticipationTestRepository studentParticipationTestRepository;

    @Autowired
    private MathSubmissionRepository mathSubmissionRepository;

    private Course course;

    private MathExercise exercise;

    private StudentParticipation participation;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        course = mathExerciseUtilService.addCourseWithMathExercise();
        exercise = (MathExercise) course.getExercises().iterator().next();
        participation = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createMathSubmission_asStudent_savesSubmission() throws Exception {
        MathSubmissionDTO submissionDTO = MathExerciseFactory.generateMathSubmissionDTO(false);

        MathSubmissionDTO result = request.postWithResponseBody("/api/math/exercises/" + exercise.getId() + "/math-submissions", submissionDTO, MathSubmissionDTO.class,
                HttpStatus.OK);

        assertThat(result).isNotNull();
        assertThat(result.id()).isNotNull();
        assertThat(result.submitted()).isFalse();
        assertThat(result.results()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createMathSubmission_setsLifecycleMetadataAndFinishesParticipation() throws Exception {
        MathSubmissionDTO submissionDTO = MathExerciseFactory.generateMathSubmissionDTO(true);

        MathSubmissionDTO result = request.postWithResponseBody("/api/math/exercises/" + exercise.getId() + "/math-submissions", submissionDTO, MathSubmissionDTO.class,
                HttpStatus.OK);

        assertThat(result.id()).isNotNull();
        assertThat(result.submitted()).isTrue();
        assertThat(result.submissionDate()).isNotNull();

        // the server (not the client) must own the lifecycle metadata and state
        MathSubmission persisted = mathSubmissionRepository.findById(result.id()).orElseThrow();
        assertThat(persisted.getType()).isEqualTo(SubmissionType.MANUAL);
        assertThat(persisted.getSubmissionDate()).isNotNull();
        assertThat(studentParticipationTestRepository.findById(participation.getId()).orElseThrow().getInitializationState()).isEqualTo(InitializationState.FINISHED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createMathSubmission_afterDueDate_returnsForbidden() throws Exception {
        // exercise whose due date has already passed, with a participation that was started before the due date.
        // Derive all dates from a single baseline so the test data is deterministic and not timing-sensitive.
        final ZonedDateTime baseTime = ZonedDateTime.now();
        MathExercise pastExercise = MathExerciseFactory.generateMathExercise(baseTime.minusDays(3), baseTime.minusDays(1), baseTime.plusDays(1), course);
        mathExerciseUtilService.saveExercise(pastExercise);
        StudentParticipation pastParticipation = participationUtilService.createAndSaveParticipationForExercise(pastExercise, TEST_PREFIX + "student1");
        pastParticipation.setInitializationDate(baseTime.minusDays(2));
        studentParticipationTestRepository.save(pastParticipation);

        MathSubmissionDTO submissionDTO = MathExerciseFactory.generateMathSubmissionDTO(true);

        request.postWithResponseBody("/api/math/exercises/" + pastExercise.getId() + "/math-submissions", submissionDTO, MathSubmissionDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void updateMathSubmission_persistsContent() throws Exception {
        MathSubmissionDTO submissionDTO = new MathSubmissionDTO(null, false, null, null, null, "my work");

        MathSubmissionDTO result = request.putWithResponseBody("/api/math/exercises/" + exercise.getId() + "/math-submissions", submissionDTO, MathSubmissionDTO.class,
                HttpStatus.OK);

        assertThat(result.content()).isEqualTo("my work");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForMathEditor_withExistingSubmission_returnsSubmission() throws Exception {
        MathSubmission saved = mathExerciseUtilService.createAndSaveSubmissionForExercise(exercise, TEST_PREFIX + "student1", false);

        MathSubmissionDTO result = request.get("/api/math/participations/" + saved.getParticipation().getId() + "/math-editor", HttpStatus.OK, MathSubmissionDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(saved.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForMathEditor_noSubmissionYet_returnsEmptySubmission() throws Exception {
        MathSubmissionDTO result = request.get("/api/math/participations/" + participation.getId() + "/math-editor", HttpStatus.OK, MathSubmissionDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.id()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getMathSubmission_asOwner_returnsOk() throws Exception {
        MathSubmission saved = mathExerciseUtilService.createAndSaveSubmissionForExercise(exercise, TEST_PREFIX + "student1", false);

        MathSubmissionDTO result = request.get("/api/math/math-submissions/" + saved.getId(), HttpStatus.OK, MathSubmissionDTO.class);

        assertThat(result.id()).isEqualTo(saved.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void getMathSubmission_asOtherStudent_returnsForbidden() throws Exception {
        MathSubmission saved = mathExerciseUtilService.createAndSaveSubmissionForExercise(exercise, TEST_PREFIX + "student1", false);

        request.get("/api/math/math-submissions/" + saved.getId(), HttpStatus.FORBIDDEN, MathSubmissionDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getSubmittedMathSubmissions_asTutor_returnsList() throws Exception {
        mathExerciseUtilService.createAndSaveSubmissionForExercise(exercise, TEST_PREFIX + "student1", true);

        var results = request.getList("/api/math/exercises/" + exercise.getId() + "/math-submissions", HttpStatus.OK, MathSubmissionDTO.class);

        assertThat(results).hasSize(1);
    }
}
