package de.tum.in.www1.artemis.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.mathexercise.MathExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

class MathSubmissionIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "mathsubmissionintegration";

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private MathSubmissionRepository submissionRepository;

    @Autowired
    private SubmissionVersionRepository submissionVersionRepository;

    @Autowired
    private StudentParticipationRepository participationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private MathExerciseUtilService mathExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private MathExercise finishedMathExercise;

    private MathExercise releasedMathExercise;

    private MathSubmission mathSubmission;

    private MathSubmission lateMathSubmission;

    private MathSubmission notSubmittedMathSubmission;

    private StudentParticipation lateParticipation;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        Course course1 = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        Course course2 = mathExerciseUtilService.addCourseWithOneFinishedMathExercise();
        releasedMathExercise = exerciseUtilService.findMathExerciseWithTitle(course1.getExercises(), "Math");
        finishedMathExercise = exerciseUtilService.findMathExerciseWithTitle(course2.getExercises(), "Finished");
        lateParticipation = participationUtilService.createAndSaveParticipationForExercise(finishedMathExercise, TEST_PREFIX + "student1");
        lateParticipation.setInitializationDate(ZonedDateTime.now().minusDays(2));
        participationRepository.save(lateParticipation);
        participationUtilService.createAndSaveParticipationForExercise(releasedMathExercise, TEST_PREFIX + "student1");

        mathSubmission = ParticipationFactory.generateMathSubmission("example text", true);
        lateMathSubmission = ParticipationFactory.generateLateMathSubmission("example text 2");
        notSubmittedMathSubmission = ParticipationFactory.generateMathSubmission("example text 2", false);

        // Add users that are not in exercise/course
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor2");
        userUtilService.createAndSaveUser(TEST_PREFIX + "student3");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3")
    void testRepositoryMethods() {
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> submissionRepository.findByIdWithParticipationExerciseResultAssessorElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> submissionRepository.findByIdWithEagerResultsAndFeedbackElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> submissionRepository.getSubmissionWithResultAndFeedbackByResultIdElseThrow(Long.MAX_VALUE));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getMathSubmissionWithResult() throws Exception {
        mathSubmission = mathExerciseUtilService.saveMathSubmission(finishedMathExercise, mathSubmission, TEST_PREFIX + "student1");
        participationUtilService.addResultToSubmission(mathSubmission, AssessmentType.MANUAL);

        MathSubmission mathSubmission = request.get("/api/math-submissions/" + this.mathSubmission.getId(), HttpStatus.OK, MathSubmission.class);

        assertThat(mathSubmission).as("math submission without assessment was found").isNotNull();
        assertThat(mathSubmission.getId()).as("correct math submission was found").isEqualTo(this.mathSubmission.getId());
        assertThat(mathSubmission.getText()).as("text of math submission is correct").isEqualTo(this.mathSubmission.getText());
        assertThat(mathSubmission.getResults()).as("results are not loaded properly").isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getMathSubmissionWithResult_notInvolved_notAllowed() throws Exception {
        mathSubmission = mathExerciseUtilService.saveMathSubmission(finishedMathExercise, mathSubmission, TEST_PREFIX + "student1");
        request.get("/api/math-submissions/" + this.mathSubmission.getId(), HttpStatus.FORBIDDEN, MathSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllMathSubmissions_studentHiddenForTutor() throws Exception {
        mathSubmission = mathExerciseUtilService.saveMathSubmissionWithResultAndAssessor(finishedMathExercise, mathSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        List<MathSubmission> mathSubmissions = request.getList("/api/exercises/" + finishedMathExercise.getId() + "/math-submissions?assessedByTutor=true", HttpStatus.OK,
                MathSubmission.class);

        assertThat(mathSubmissions).as("one math submission was found").hasSize(1);
        assertThat(mathSubmissions.get(0).getId()).as("correct math submission was found").isEqualTo(mathSubmission.getId());
        assertThat(((StudentParticipation) mathSubmissions.get(0).getParticipation()).getStudent()).as(TEST_PREFIX + "student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAllMathSubmissions_studentVisibleForInstructor() throws Exception {
        mathSubmission = mathExerciseUtilService.saveMathSubmission(finishedMathExercise, mathSubmission, TEST_PREFIX + "student1");

        List<MathSubmission> mathSubmissions = request.getList("/api/exercises/" + finishedMathExercise.getId() + "/math-submissions", HttpStatus.OK, MathSubmission.class);

        assertThat(mathSubmissions).as("one math submission was found").hasSize(1);
        assertThat(mathSubmissions.get(0).getId()).as("correct math submission was found").isEqualTo(mathSubmission.getId());
        assertThat(((StudentParticipation) mathSubmissions.get(0).getParticipation()).getStudent()).as(TEST_PREFIX + "student of participation is hidden").isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getAllMathSubmissions_assessedByTutorForStudent() throws Exception {
        mathSubmission = mathExerciseUtilService.saveMathSubmission(finishedMathExercise, mathSubmission, TEST_PREFIX + "student1");
        request.getList("/api/exercises/" + finishedMathExercise.getId() + "/math-submissions?assessedByTutor=true", HttpStatus.FORBIDDEN, MathSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllMathSubmissions_notAssessedByTutorForTutor() throws Exception {
        mathSubmission = mathExerciseUtilService.saveMathSubmission(finishedMathExercise, mathSubmission, TEST_PREFIX + "student1");
        request.getList("/api/exercises/" + finishedMathExercise.getId() + "/math-submissions", HttpStatus.FORBIDDEN, MathSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void getAllMathSubmission_notTutorInExercise() throws Exception {
        mathSubmission = mathExerciseUtilService.saveMathSubmission(finishedMathExercise, mathSubmission, TEST_PREFIX + "student1");
        request.getList("/api/exercises/" + finishedMathExercise.getId() + "/math-submissions?assessedByTutor=true", HttpStatus.FORBIDDEN, MathSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getMathSubmissionWithoutAssessment_studentHidden() throws Exception {
        mathSubmission = mathExerciseUtilService.saveMathSubmission(finishedMathExercise, mathSubmission, TEST_PREFIX + "student1");

        MathSubmission mathSubmissionWithoutAssessment = request.get("/api/exercises/" + finishedMathExercise.getId() + "/math-submission-without-assessment", HttpStatus.OK,
                MathSubmission.class);

        assertThat(mathSubmissionWithoutAssessment).as("math submission without assessment was found").isNotNull();
        assertThat(mathSubmissionWithoutAssessment.getId()).as("correct math submission was found").isEqualTo(mathSubmission.getId());
        assertThat(((StudentParticipation) mathSubmissionWithoutAssessment.getParticipation()).getStudent()).as(TEST_PREFIX + "student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getMathSubmissionWithoutAssessment_lockSubmission() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        mathSubmission = mathExerciseUtilService.saveMathSubmission(finishedMathExercise, mathSubmission, TEST_PREFIX + "student1");

        MathSubmission storedSubmission = request.get("/api/exercises/" + finishedMathExercise.getId() + "/math-submission-without-assessment?lock=true", HttpStatus.OK,
                MathSubmission.class);

        assertThat(storedSubmission).as("submission was found").isEqualToIgnoringGivenFields(mathSubmission, "results", "submissionDate");
        assertThat(storedSubmission.getSubmissionDate()).as("submission date is correct").isEqualToIgnoringNanos(mathSubmission.getSubmissionDate());
        assertThat(storedSubmission.getLatestResult()).as("result is set").isNotNull();
        assertThat(storedSubmission.getLatestResult().getAssessor()).as("assessor is tutor1").isEqualTo(user);
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getMathSubmissionWithoutAssessment_selectInTime() throws Exception {

        mathSubmission = mathExerciseUtilService.saveMathSubmission(finishedMathExercise, mathSubmission, TEST_PREFIX + "student1");
        lateMathSubmission = mathExerciseUtilService.saveMathSubmission(finishedMathExercise, lateMathSubmission, TEST_PREFIX + "student2");

        assertThat(mathSubmission.getSubmissionDate()).as("first submission is in-time").isBefore(finishedMathExercise.getDueDate());
        assertThat(lateMathSubmission.getSubmissionDate()).as("second submission is late").isAfter(finishedMathExercise.getDueDate());

        MathSubmission storedSubmission = request.get("/api/exercises/" + finishedMathExercise.getId() + "/math-submission-without-assessment", HttpStatus.OK,
                MathSubmission.class);

        assertThat(storedSubmission).as("math submission without assessment was found").isNotNull();
        assertThat(storedSubmission.getId()).as("in-time math submission was found").isEqualTo(mathSubmission.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getMathSubmissionWithoutAssessment_noSubmittedSubmission_null() throws Exception {
        MathSubmission submission = ParticipationFactory.generateMathSubmission("text", false);
        mathExerciseUtilService.saveMathSubmission(finishedMathExercise, submission, TEST_PREFIX + "student1");

        var response = request.get("/api/exercises/" + finishedMathExercise.getId() + "/math-submission-without-assessment", HttpStatus.OK, MathSubmission.class);
        assertThat(response).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void getMathSubmissionWithoutAssessment_notTutorInExercise() throws Exception {
        mathSubmission = mathExerciseUtilService.saveMathSubmission(finishedMathExercise, mathSubmission, TEST_PREFIX + "student1");
        request.get("/api/exercises/" + finishedMathExercise.getId() + "/math-submission-without-assessment", HttpStatus.FORBIDDEN, MathSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getMathSubmissionWithoutAssessment_dueDateNotOver() throws Exception {
        mathSubmission = mathExerciseUtilService.saveMathSubmission(releasedMathExercise, mathSubmission, TEST_PREFIX + "student1");

        request.get("/api/exercises/" + releasedMathExercise.getId() + "/math-submission-without-assessment", HttpStatus.FORBIDDEN, MathSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getMathSubmissionWithoutAssessment_asStudent_forbidden() throws Exception {
        mathSubmission = mathExerciseUtilService.saveMathSubmission(finishedMathExercise, mathSubmission, TEST_PREFIX + "student1");

        request.get("/api/exercises/" + finishedMathExercise.getId() + "/math-submission-without-assessment", HttpStatus.FORBIDDEN, MathSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getResultsForCurrentStudent_assessorHiddenForStudent() throws Exception {
        mathSubmission = ParticipationFactory.generateMathSubmission("Some text", true);
        mathExerciseUtilService.saveMathSubmissionWithResultAndAssessor(finishedMathExercise, mathSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        Exercise returnedExercise = request.get("/api/exercises/" + finishedMathExercise.getId() + "/details", HttpStatus.OK, Exercise.class);

        assertThat(returnedExercise.getStudentParticipations().iterator().next().getResults().iterator().next().getAssessor()).as("assessor is null").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitExercise_afterDueDate_forbidden() throws Exception {
        request.put("/api/exercises/" + finishedMathExercise.getId() + "/math-submissions", mathSubmission, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitExercise_beforeDueDate_isTeamMode() throws Exception {
        releasedMathExercise.setMode(ExerciseMode.TEAM);
        exerciseRepo.save(releasedMathExercise);
        Team team = new Team();
        team.setName("Team");
        team.setShortName("team");
        team.setExercise(releasedMathExercise);
        team.addStudents(userRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow());
        team.addStudents(userRepository.findOneByLogin(TEST_PREFIX + "student2").orElseThrow());
        teamRepository.save(releasedMathExercise, team);

        StudentParticipation participation = participationUtilService.addTeamParticipationForExercise(releasedMathExercise, team.getId());
        releasedMathExercise.setStudentParticipations(Set.of(participation));

        MathSubmission submission = request.putWithResponseBody("/api/exercises/" + releasedMathExercise.getId() + "/math-submissions", mathSubmission, MathSubmission.class,
                HttpStatus.OK);

        userUtilService.changeUser(TEST_PREFIX + "student1");
        Optional<SubmissionVersion> version = submissionVersionRepository.findLatestVersion(submission.getId());
        assertThat(version).as("submission version was created").isNotEmpty();
        assertThat(version.orElseThrow().getId()).isEqualTo(1);
        assertThat(version.orElseThrow().getAuthor().getLogin()).as("submission version has correct author").isEqualTo(TEST_PREFIX + "student1");
        assertThat(version.get().getContent()).as("submission version has correct content").isEqualTo(submission.getText());

        userUtilService.changeUser(TEST_PREFIX + "student2");
        submission.setText(submission.getText() + " Extra contribution.");
        request.put("/api/exercises/" + releasedMathExercise.getId() + "/math-submissions", submission, HttpStatus.OK);

        // create new submission to simulate other teams working at the same time
        request.putWithResponseBody("/api/exercises/" + releasedMathExercise.getId() + "/math-submissions", mathSubmission, MathSubmission.class, HttpStatus.OK);

        userUtilService.changeUser(TEST_PREFIX + "student2");
        version = submissionVersionRepository.findLatestVersion(submission.getId());
        assertThat(version).as("submission version was created").isNotEmpty();
        assertThat(version.orElseThrow().getId()).isEqualTo(2);
        assertThat(version.orElseThrow().getAuthor().getLogin()).as("submission version has correct author").isEqualTo(TEST_PREFIX + "student2");
        assertThat(version.get().getContent()).as("submission version has correct content").isEqualTo(submission.getText());

        submission.setText(submission.getText() + " Even more.");
        request.put("/api/exercises/" + releasedMathExercise.getId() + "/math-submissions", submission, HttpStatus.OK);
        userUtilService.changeUser(TEST_PREFIX + "student2");
        Optional<SubmissionVersion> newVersion = submissionVersionRepository.findLatestVersion(submission.getId());
        assertThat(newVersion.orElseThrow().getId()).as("submission version was not created").isEqualTo(version.get().getId());

        exerciseRepo.save(releasedMathExercise.participations(Set.of()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitExercise_beforeDueDate_allowed() throws Exception {
        MathSubmission submission = request.putWithResponseBody("/api/exercises/" + releasedMathExercise.getId() + "/math-submissions", mathSubmission, MathSubmission.class,
                HttpStatus.OK);

        assertThat(submission.getSubmissionDate()).isCloseTo(ZonedDateTime.now(), within(500, ChronoUnit.MILLIS));
        assertThat(submission.getParticipation().getInitializationState()).isEqualTo(InitializationState.FINISHED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void saveAndSubmitMathSubmission_tooLarge() throws Exception {
        // should be ok
        char[] chars = new char[(int) (Constants.MAX_SUBMISSION_TEXT_LENGTH)];
        Arrays.fill(chars, 'a');
        mathSubmission.setText(new String(chars));
        request.postWithResponseBody("/api/exercises/" + releasedMathExercise.getId() + "/math-submissions", mathSubmission, MathSubmission.class, HttpStatus.OK);

        // should be too large
        char[] charsTooLarge = new char[(int) (Constants.MAX_SUBMISSION_TEXT_LENGTH + 1)];
        Arrays.fill(charsTooLarge, 'a');
        mathSubmission.setText(new String(charsTooLarge));
        request.put("/api/exercises/" + releasedMathExercise.getId() + "/math-submissions", mathSubmission, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitExercise_beforeDueDateWithTwoSubmissions_allowed() throws Exception {
        final var submitPath = "/api/exercises/" + releasedMathExercise.getId() + "/math-submissions";
        final var newSubmissionText = "Some other test text";
        mathSubmission = request.putWithResponseBody(submitPath, mathSubmission, MathSubmission.class, HttpStatus.OK);
        mathSubmission.setText(newSubmissionText);
        request.put(submitPath, mathSubmission, HttpStatus.OK);

        final var submissionInDb = submissionRepository.findById(mathSubmission.getId());
        assertThat(submissionInDb).isPresent();
        assertThat(submissionInDb.get().getText()).isEqualTo(newSubmissionText);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitExercise_afterDueDateWithParticipationStartAfterDueDate_allowed() throws Exception {
        lateParticipation.setInitializationDate(ZonedDateTime.now());
        participationRepository.save(lateParticipation);

        request.put("/api/exercises/" + releasedMathExercise.getId() + "/math-submissions", mathSubmission, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void saveExercise_beforeDueDate() throws Exception {
        MathSubmission storedSubmission = request.putWithResponseBody("/api/exercises/" + releasedMathExercise.getId() + "/math-submissions", notSubmittedMathSubmission,
                MathSubmission.class, HttpStatus.OK);
        assertThat(storedSubmission.isSubmitted()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void saveExercise_afterDueDateWithParticipationStartAfterDueDate() throws Exception {
        exerciseUtilService.updateExerciseDueDate(releasedMathExercise.getId(), ZonedDateTime.now().minusHours(1));
        lateParticipation.setInitializationDate(ZonedDateTime.now());
        participationRepository.save(lateParticipation);

        MathSubmission storedSubmission = request.putWithResponseBody("/api/exercises/" + releasedMathExercise.getId() + "/math-submissions", notSubmittedMathSubmission,
                MathSubmission.class, HttpStatus.OK);
        assertThat(storedSubmission.isSubmitted()).isFalse();

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void submitExercise_notStudentInCourse() throws Exception {
        request.post("/api/exercises/" + releasedMathExercise.getId() + "/math-submissions", mathSubmission, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitExercise_submissionIsAlreadyCreated_badRequest() throws Exception {
        mathSubmission = submissionRepository.save(mathSubmission);
        request.post("/api/exercises/" + releasedMathExercise.getId() + "/math-submissions", mathSubmission, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitExercise_noExercise_badRequest() throws Exception {
        var fakeExerciseId = releasedMathExercise.getId() + 100L;
        request.post("/api/exercises/" + fakeExerciseId + "/math-submissions", mathSubmission, HttpStatus.NOT_FOUND);
    }

    private void checkDetailsHidden(MathSubmission submission, boolean isStudent) {
        assertThat(submission.getParticipation().getResults()).as("results are hidden in participation").isNullOrEmpty();
        if (isStudent) {
            assertThat(submission.getLatestResult()).as("result is hidden").isNull();
        }
        else {
            assertThat(((StudentParticipation) submission.getParticipation()).getStudent()).as(TEST_PREFIX + "student of participation is hidden").isEmpty();
        }
    }
}
