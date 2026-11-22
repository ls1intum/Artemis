package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.ComplaintDTO;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.assessment.util.ComplaintUtilService;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionResponseDTO;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionWithComplaintDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

class SubmissionServiceTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "submissionservicetest"; // only lower case is supported

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private ExerciseTestRepository exerciseRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ResultTestRepository resultRepository;

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private UserTestRepository userRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ComplaintUtilService complaintUtilService;

    private User student1;

    private User tutor1;

    private User tutor2;

    private Course examCourse;

    private TextExercise examTextExercise;

    private ModelingExercise examModelingExercise;

    private ProgrammingExercise examProgrammingExercise;

    private FileUploadExercise examFileUploadExercise;

    private Submission submission1;

    private Submission submission2;

    private Optional<Submission> unassessedSubmissionCorrectionRound0Tutor1;

    private Optional<Submission> unassessedSubmissionCorrectionRound1Tutor1;

    private Optional<Submission> unassessedSubmissionCorrectionRound0Tutor2;

    private Optional<Submission> unassessedSubmissionCorrectionRound1Tutor2;

    private List<Submission> submissionListTutor1CorrectionRound0;

    private List<Submission> submissionListTutor2CorrectionRound0;

    private List<Submission> submissionListTutor1CorrectionRound1;

    private List<Submission> submissionListTutor2CorrectionRound1;

    // set to true, if a tutor is only able to assess a submission if they have not assessed it any prior correction rounds
    private final boolean tutorAssessUnique = true;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 3, 2, 0, 1);
        student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        tutor1 = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        tutor2 = userUtilService.getUserByLogin(TEST_PREFIX + "tutor2");

        examCourse = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        Exam exam = examUtilService.addExam(examCourse);

        exam.setNumberOfCorrectionRoundsInExam(2);
        exam = examRepository.save(exam);

        exam = examUtilService.addExerciseGroupsAndExercisesToExam(exam, true);
        examTextExercise = (TextExercise) exam.getExerciseGroups().getFirst().getExercises().stream().filter(ex -> ex instanceof TextExercise).findAny().orElse(null);
        examModelingExercise = (ModelingExercise) exam.getExerciseGroups().get(3).getExercises().stream().filter(ex -> ex instanceof ModelingExercise).findAny().orElse(null);
        examProgrammingExercise = (ProgrammingExercise) exam.getExerciseGroups().get(6).getExercises().stream().filter(ex -> ex instanceof ProgrammingExercise).findAny()
                .orElse(null);
        examFileUploadExercise = (FileUploadExercise) exam.getExerciseGroups().get(2).getExercises().stream().filter(exy -> exy instanceof FileUploadExercise).findAny()
                .orElse(null);
    }

    @AfterEach
    void tearDown() {
        if (submissionListTutor1CorrectionRound0 != null) {
            submissionListTutor1CorrectionRound0.clear();
        }
        if (submissionListTutor2CorrectionRound0 != null) {
            submissionListTutor2CorrectionRound0.clear();
        }
        if (submissionListTutor1CorrectionRound1 != null) {
            submissionListTutor1CorrectionRound1.clear();
        }
        if (submissionListTutor2CorrectionRound1 != null) {
            submissionListTutor2CorrectionRound1.clear();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCheckSubmissionAllowanceGroupCheck() {
        // Remove student1 from the course so they have no enrollment — access should be denied
        userUtilService.unenrollUserFromCourse(student1, examCourse);
        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> submissionService.checkSubmissionAllowanceElseThrow(examTextExercise, null, student1));
    }

    /** Tutor reassignment waits for a started draft to commit, then transfers that draft too. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void teamDraftCreationIsSerializedWithTutorChange() throws Exception {
        var exercise = teamExercise(ExerciseType.TEXT);
        var submission = teamSubmission(exercise, tutor1, "own");
        var team = ((StudentParticipation) submission.getParticipation()).getTeam().orElseThrow();
        var transferStarted = new java.util.concurrent.CountDownLatch(1);
        var transfer = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<Team>>();
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var resultId = teamRepository.withAssessmentLock(team, () -> {
                transfer.set(executor.submit(() -> {
                    var updated = teamRepository.findWithStudentsByIdElseThrow(team.getId());
                    updated.setOwner(tutor2);
                    transferStarted.countDown();
                    return teamRepository.saveWithTutorChange(exercise, updated, tutor2);
                }));
                try {
                    assertThat(transferStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    org.assertj.core.api.Assertions.assertThatThrownBy(() -> transfer.get().get(200, java.util.concurrent.TimeUnit.MILLISECONDS))
                            .isInstanceOf(java.util.concurrent.TimeoutException.class);
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                return submissionService.lockSubmission(submission, 0).getId();
            });
            transfer.get().get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(resultRepository.findByIdWithEagerFeedbacksAndAssessor(resultId).orElseThrow().getAssessor()).isEqualTo(tutor2);
            // Simulate a request that loaded ownership before the transfer, but reaches the lock afterwards.
            team.setOwner(tutor1);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> submissionService.lockSubmission(submission, 0));
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void tutorChangePreservesCompletedAssessmentsAndTransfersOtherExerciseDrafts() throws Exception {
        var text = teamExercise(ExerciseType.TEXT);
        var textSubmission = teamSubmission(text, tutor1, "own");
        request.get(assessmentStartUrl(ExerciseType.TEXT, textSubmission.getId()), HttpStatus.OK, String.class);
        var completed = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(textSubmission.getId()).getLatestResult();
        completeTeamAssessment(ExerciseType.TEXT, textSubmission, completed.getId(), HttpStatus.OK);
        var modeling = teamExercise(ExerciseType.MODELING);
        var modelingSubmission = teamSubmission(modeling, tutor1, "own");
        request.get(assessmentStartUrl(ExerciseType.MODELING, modelingSubmission.getId()), HttpStatus.OK, String.class);
        var draft = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(modelingSubmission.getId()).getLatestResult();
        var team = teamRepository.findWithStudentsByIdElseThrow(((StudentParticipation) textSubmission.getParticipation()).getTeam().orElseThrow().getId());
        team.setOwner(tutor2);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        request.put("/api/exercise/exercises/" + text.getId() + "/teams/" + team.getId(), de.tum.cit.aet.artemis.exercise.dto.TeamInputDTO.of(team), HttpStatus.OK);
        assertThat(resultRepository.findByIdWithEagerFeedbacksAndAssessor(completed.getId()).orElseThrow().getAssessor()).isEqualTo(tutor1);
        assertThat(resultRepository.findByIdWithEagerFeedbacksAndAssessor(draft.getId()).orElseThrow().getAssessor()).isEqualTo(tutor2);
        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        var cancelUrl = "/api/text/participations/" + textSubmission.getParticipation().getId() + "/submissions/" + textSubmission.getId() + "/cancel-assessment";
        request.post(cancelUrl, null, HttpStatus.FORBIDDEN);
        request.post(cancelUrl + "?resultId=" + completed.getId(), null, HttpStatus.FORBIDDEN);
        assertThat(resultRepository.findByIdWithEagerFeedbacksAndAssessor(completed.getId())).isPresent();
    }

    /** Student owners keep team ownership without becoming assessment tutors. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void studentTeamOwnerUnassignsDraft() throws Exception {
        var exercise = teamExercise(ExerciseType.TEXT);
        var submission = teamSubmission(exercise, tutor1, "own");
        request.get(assessmentStartUrl(ExerciseType.TEXT, submission.getId()), HttpStatus.OK, String.class);
        var resultId = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(submission.getId()).getLatestResult().getId();
        var team = teamRepository.findWithStudentsByIdElseThrow(((StudentParticipation) submission.getParticipation()).getTeam().orElseThrow().getId());
        team.setOwner(student1);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        request.put("/api/exercise/exercises/" + exercise.getId() + "/teams/" + team.getId(), de.tum.cit.aet.artemis.exercise.dto.TeamInputDTO.of(team), HttpStatus.OK);
        assertThat(teamRepository.findWithStudentsByIdElseThrow(team.getId()).getOwner()).isEqualTo(student1);
        assertThat(resultRepository.findByIdWithEagerFeedbacksAndAssessor(resultId).orElseThrow().getAssessor()).isNull();
    }

    /** Creates a released team exercise with a passed submission deadline. */
    private Exercise teamExercise(ExerciseType type) {
        Exercise exercise = switch (type) {
            case TEXT -> examTextExercise;
            case MODELING -> examModelingExercise;
            case FILE_UPLOAD -> examFileUploadExercise;
            case PROGRAMMING -> examProgrammingExercise;
            default -> throw new IllegalArgumentException("Unsupported assessment type: " + type);
        };
        exercise.setExerciseGroup(null);
        exercise.setCourse(examCourse);
        exercise.setMode(ExerciseMode.TEAM);
        exercise.setReleaseDate(ZonedDateTime.now().minusDays(3));
        exercise.setDueDate(ZonedDateTime.now().minusDays(1));
        exercise.setAssessmentDueDate(ZonedDateTime.now().plusDays(1));
        exercise.setMaxPoints(10.0);
        return exerciseRepository.save(exercise);
    }

    /** Creates a submitted team participation owned by the given tutor. */
    private Submission teamSubmission(Exercise exercise, User owner, String name) {
        var team = new Team();
        team.setName(name);
        team.setShortName(name);
        team.setExercise(exercise);
        team.setOwner(owner);
        String studentLogin = switch (name) {
            case "other" -> "student2";
            case "anotherown" -> "student3";
            default -> "student1";
        };
        team.setStudents(Set.of(userUtilService.getUserByLogin(TEST_PREFIX + studentLogin)));
        team = teamRepository.save(exercise, team);
        StudentParticipation participation = exercise instanceof ProgrammingExercise programmingExercise
                ? participationUtilService.addTeamParticipationForProgrammingExercise(programmingExercise, team)
                : participationUtilService.addTeamParticipationForExercise(exercise, team.getId());
        Submission submission = switch (exercise) {
            case TextExercise ignored -> new TextSubmission().text("Team answer");
            case ModelingExercise ignored -> new ModelingSubmission().model("{}");
            case FileUploadExercise ignored -> new FileUploadSubmission();
            case ProgrammingExercise ignored -> new ProgrammingSubmission();
            default -> throw new IllegalArgumentException("Unsupported assessment exercise");
        };
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now().minusDays(2));
        submission = participationUtilService.addSubmission(participation, submission);
        if (exercise instanceof ProgrammingExercise) {
            var automaticResult = new Result();
            automaticResult.setAssessmentType(AssessmentType.AUTOMATIC);
            automaticResult.setSubmission(submission);
            automaticResult.setExerciseId(exercise.getId());
            resultRepository.save(automaticResult);
        }
        return submission;
    }

    /** Returns the assessment-start endpoint for a submission. */
    private String assessmentStartUrl(ExerciseType type, Long submissionId) {
        return switch (type) {
            case TEXT -> "/api/text/text-submissions/" + submissionId + "/for-assessment";
            case MODELING -> "/api/modeling/modeling-submissions/" + submissionId + "/for-assessment";
            case FILE_UPLOAD -> "/api/fileupload/file-upload-submissions/" + submissionId;
            case PROGRAMMING -> "/api/programming/programming-submissions/" + submissionId + "/lock";
            default -> throw new IllegalArgumentException("Unsupported assessment type: " + type);
        };
    }

    /** Submits an assessment through its exercise-specific endpoint. */
    private void completeTeamAssessment(ExerciseType type, Submission submission, Long resultId, HttpStatus expectedStatus) throws Exception {
        writeTeamAssessment(type, submission, resultId, expectedStatus, true);
    }

    /** Saves or submits feedback through the exercise-specific endpoint. */
    private void writeTeamAssessment(ExerciseType type, Submission submission, Long resultId, HttpStatus expectedStatus, boolean submit) throws Exception {
        var body = Map.of("id", resultId, "feedbacks", List.of(), "textBlocks", Set.of(), "rated", true, "score", 0.0, "successful", false);
        switch (type) {
            case TEXT -> {
                var base = "/api/text/participations/" + submission.getParticipation().getId() + "/results/" + resultId;
                if (submit) {
                    request.postWithoutResponseBody(base + "/submit-text-assessment", body, expectedStatus);
                }
                else {
                    request.put(base + "/text-assessment", body, expectedStatus);
                }
            }
            case MODELING ->
                request.put("/api/modeling/modeling-submissions/" + submission.getId() + "/results/" + resultId + "/assessment?submit=" + submit, body, expectedStatus);
            case FILE_UPLOAD -> request.put("/api/fileupload/file-upload-submissions/" + submission.getId() + "/feedback?submit=" + submit, body, expectedStatus);
            case PROGRAMMING -> request.put("/api/programming/participations/" + submission.getParticipation().getId() + "/manual-results?submit=" + submit, body, expectedStatus);
            default -> throw new IllegalArgumentException("Unsupported assessment type: " + type);
        }
    }

    private void queryTestingBasics(Exercise exercise) {
        Exam exam = exercise.getExerciseGroup().getExam();

        exam.setNumberOfCorrectionRoundsInExam(2);

        examRepository.save(exam);

        if (submission1 != null && submission2 != null) {
            submission1.submitted(true);
            submission2.submitted(true);

            if (exercise instanceof ProgrammingExercise) {
                programmingExerciseUtilService.addProgrammingSubmission(examProgrammingExercise, (ProgrammingSubmission) submission1, TEST_PREFIX + "student1");
                programmingExerciseUtilService.addProgrammingSubmission(examProgrammingExercise, (ProgrammingSubmission) submission2, TEST_PREFIX + "student1");
            }
            else {
                participationUtilService.addSubmission(exercise, submission1, TEST_PREFIX + "student1");
                participationUtilService.addSubmission(exercise, submission2, TEST_PREFIX + "student2");
            }
        }
    }

    private void getTutorSpecificCallsTutor1(Exercise exercise) {
        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        unassessedSubmissionCorrectionRound0Tutor1 = submissionService.getRandomAssessableSubmission(exercise, true, 0);
        unassessedSubmissionCorrectionRound1Tutor1 = submissionService.getRandomAssessableSubmission(exercise, true, 1);
        submissionListTutor1CorrectionRound0 = submissionService.getAllSubmissionsAssessedByTutorForCorrectionRoundAndExerciseIgnoreTestRuns(exercise.getId(), tutor1, true, 0);
        submissionListTutor1CorrectionRound1 = submissionService.getAllSubmissionsAssessedByTutorForCorrectionRoundAndExerciseIgnoreTestRuns(exercise.getId(), tutor1, true, 1);
    }

    private void getTutorSpecificCallsTutor2(Exercise exercise) {
        userUtilService.changeUser(TEST_PREFIX + "tutor2");
        unassessedSubmissionCorrectionRound0Tutor2 = submissionService.getRandomAssessableSubmission(exercise, true, 0);
        unassessedSubmissionCorrectionRound1Tutor2 = submissionService.getRandomAssessableSubmission(exercise, true, 1);
        submissionListTutor2CorrectionRound0 = submissionService.getAllSubmissionsAssessedByTutorForCorrectionRoundAndExerciseIgnoreTestRuns(exercise.getId(), tutor2, true, 0);
        submissionListTutor2CorrectionRound1 = submissionService.getAllSubmissionsAssessedByTutorForCorrectionRoundAndExerciseIgnoreTestRuns(exercise.getId(), tutor2, true, 1);
    }

    private void getQueryResults(Exercise exercise) {
        getTutorSpecificCallsTutor1(exercise);
        getTutorSpecificCallsTutor2(exercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testProgrammingExerciseGetRandomSubmissionEligibleForNewAssessmentNoAssessments() {
        submission1 = new ProgrammingSubmission();
        submission2 = new ProgrammingSubmission();

        // setup
        queryTestingBasics(this.examProgrammingExercise);

        getQueryResults(this.examProgrammingExercise);

        assertThat(examProgrammingExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).isPresent();
        assertThat(unassessedSubmissionCorrectionRound0Tutor1.get()).isIn(submission1, submission2);
        assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();

        assertThat(submissionListTutor1CorrectionRound0).isEmpty();
        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testFileUploadExerciseGetRandomSubmissionEligibleForNewAssessment() {
        submission1 = new FileUploadSubmission();
        submission2 = new FileUploadSubmission();
        // setup
        queryTestingBasics(this.examFileUploadExercise);

        getQueryResults(this.examFileUploadExercise);

        assertThat(examFileUploadExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).isPresent();
        assertThat(unassessedSubmissionCorrectionRound0Tutor1.get()).isIn(submission1, submission2);
        assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();

        assertThat(submissionListTutor1CorrectionRound0).isEmpty();
        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testFileUploadExerciseGetRandomSubmissionEligibleForNewAssessmentWithoutSubmission() {
        // setup
        queryTestingBasics(this.examFileUploadExercise);

        getQueryResults(this.examFileUploadExercise);

        assertThat(examFileUploadExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).isEmpty();
        assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();

        assertThat(submissionListTutor1CorrectionRound0).isEmpty();
        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testTextExerciseGetRandomSubmissionEligibleForNewAssessmentNoAssessments() {
        submission1 = new TextSubmission();
        submission2 = new TextSubmission();
        // setup
        queryTestingBasics(this.examTextExercise);

        getQueryResults(this.examTextExercise);

        assertThat(examTextExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).isPresent();
        assertThat(unassessedSubmissionCorrectionRound0Tutor1.get()).isIn(submission1, submission2);
        assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();

        assertThat(submissionListTutor1CorrectionRound0).isEmpty();
        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testTextExerciseGetRandomSubmissionEligibleForNewAssessmentOneAssessmentsWithoutLock() {
        submission1 = new TextSubmission();
        submission2 = new TextSubmission();
        // setup
        queryTestingBasics(examTextExercise);

        participationUtilService.addResultToSubmission(submission1, AssessmentType.MANUAL, tutor1, 10D, true);

        // checks
        getQueryResults(examTextExercise);

        assertThat(examTextExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).contains(submission2);
        assertThat(unassessedSubmissionCorrectionRound0Tutor2).contains(submission2);

        if (tutorAssessUnique) {
            assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();
        }
        else {
            assertThat(unassessedSubmissionCorrectionRound1Tutor1).contains(submission1);
        }
        assertThat(unassessedSubmissionCorrectionRound1Tutor2).contains(submission1);

        assertThat(submissionListTutor1CorrectionRound0).hasSize(1);
        assertThat(submissionListTutor1CorrectionRound0.getFirst()).isEqualTo(submission1);
        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).isEmpty();

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testTextExerciseGetRandomSubmissionEligibleForNewAssessmentOneAssessmentsWithLock() {
        submission1 = new TextSubmission();
        submission2 = new TextSubmission();
        // setup
        queryTestingBasics(this.examTextExercise);

        Result resultWithLock;

        resultWithLock = submissionService.saveNewEmptyResult(submission1);
        resultWithLock.setAssessor(tutor1);
        resultWithLock.setAssessmentType(AssessmentType.MANUAL);
        resultWithLock.setExerciseId(examTextExercise.getId());
        resultRepository.save(resultWithLock);

        // checks
        getQueryResults(examTextExercise);

        assertThat(examTextExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).contains(submission2);
        assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();

        assertThat(submissionListTutor1CorrectionRound0).hasSize(1);
        assertThat(submissionListTutor1CorrectionRound0.getFirst()).isEqualTo(submission1);
        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).isEmpty();

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testTextExerciseGetRandomSubmissionEligibleForNewAssessmentOneAssessmentsInSecondCorrectionRoundWithoutLock() {
        submission1 = new TextSubmission();
        submission2 = new TextSubmission();
        // setup
        queryTestingBasics(this.examTextExercise);

        participationUtilService.addResultToSubmission(submission1, AssessmentType.MANUAL, tutor1, 10D, true);
        participationUtilService.addResultToSubmission(submission1, AssessmentType.MANUAL, tutor2, 20D, true);

        // checks
        getQueryResults(examTextExercise);

        assertThat(submission1.getResults()).hasSize(2);

        assertThat(examTextExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).contains(submission2);
        assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();

        assertThat(submissionListTutor1CorrectionRound0).hasSize(1);
        assertThat(submissionListTutor1CorrectionRound0.getFirst()).isEqualTo(submission1);
        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).hasSize(1);
        assertThat(submissionListTutor2CorrectionRound1.getFirst()).isEqualTo(submission1);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testTextExerciseGetRandomSubmissionEligibleForNewAssessmentOneAssessmentsInSecondCorrectionRoundWithLock() {
        submission1 = new TextSubmission();
        submission2 = new TextSubmission();
        // setup
        queryTestingBasics(this.examTextExercise);

        participationUtilService.addResultToSubmission(submission1, AssessmentType.MANUAL, tutor1, 10D, true);

        Result resultForSecondCorrectionWithLock;

        resultForSecondCorrectionWithLock = submissionService.saveNewEmptyResult(submission1);
        resultForSecondCorrectionWithLock.setAssessor(tutor2);
        resultForSecondCorrectionWithLock.setAssessmentType(AssessmentType.MANUAL);
        resultForSecondCorrectionWithLock.setExerciseId(examTextExercise.getId());
        resultRepository.save(resultForSecondCorrectionWithLock);

        // checks
        getQueryResults(examTextExercise);

        assertThat(submission1.getResults()).hasSize(2);

        assertThat(examTextExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).contains(submission2);
        assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();

        assertThat(submissionListTutor1CorrectionRound0).hasSize(1);
        assertThat(submissionListTutor1CorrectionRound0.getFirst()).isEqualTo(submission1);
        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).hasSize(1);
        assertThat(submissionListTutor2CorrectionRound1.getFirst()).isEqualTo(submission1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testModelingExerciseGetRandomSubmissionEligibleForNewAssessmentNoAssessments() {
        submission1 = new ModelingSubmission();
        submission2 = new ModelingSubmission();
        // setup
        queryTestingBasics(this.examModelingExercise);

        // checks
        getQueryResults(examModelingExercise);

        assertThat(examModelingExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).isPresent();
        assertThat(unassessedSubmissionCorrectionRound0Tutor1.get()).isIn(submission1, submission2);
        assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();

        assertThat(submissionListTutor1CorrectionRound0).isEmpty();
        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testModelingExerciseGetRandomSubmissionEligibleForNewAssessmentOneAssessmentsWithoutLock() {
        submission1 = new ModelingSubmission();
        submission2 = new ModelingSubmission();
        // setup
        queryTestingBasics(this.examModelingExercise);

        participationUtilService.addResultToSubmission(submission1, AssessmentType.MANUAL, tutor1, 10D, true);

        // checks
        getQueryResults(examModelingExercise);

        assertThat(examModelingExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).contains(submission2);

        if (tutorAssessUnique) {
            assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();
        }
        else {
            assertThat(unassessedSubmissionCorrectionRound1Tutor1).contains(submission1);
        }

        assertThat(unassessedSubmissionCorrectionRound1Tutor2).contains(submission1);

        assertThat(submissionListTutor1CorrectionRound0).hasSize(1);
        assertThat(submissionListTutor1CorrectionRound0.getFirst()).isEqualTo(submission1);

        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).isEmpty();

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testModelingExerciseGetRandomSubmissionEligibleForNewAssessmentOneAssessmentsWithLock() {
        submission1 = new ModelingSubmission();
        submission2 = new ModelingSubmission();
        // setup
        queryTestingBasics(this.examModelingExercise);

        Result resultWithLock;

        resultWithLock = submissionService.saveNewEmptyResult(submission1);
        resultWithLock.setAssessor(tutor1);
        resultWithLock.setAssessmentType(AssessmentType.MANUAL);
        resultWithLock.setExerciseId(examModelingExercise.getId());
        resultRepository.save(resultWithLock);

        // checks
        getQueryResults(examModelingExercise);

        assertThat(examModelingExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).contains(submission2);
        assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();

        assertThat(submissionListTutor1CorrectionRound0).hasSize(1);
        assertThat(submissionListTutor1CorrectionRound0.getFirst()).isEqualTo(submission1);
        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).isEmpty();

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testModelingExerciseGetRandomSubmissionEligibleForNewAssessmentOneAssessmentsInSecondCorrectionRoundWithoutLock() {
        submission1 = new ModelingSubmission();
        submission2 = new ModelingSubmission();
        // setup
        queryTestingBasics(this.examModelingExercise);

        participationUtilService.addResultToSubmission(submission1, AssessmentType.MANUAL, tutor1, 10D, true);
        participationUtilService.addResultToSubmission(submission1, AssessmentType.MANUAL, tutor2, 20D, true);

        // checks
        getQueryResults(examModelingExercise);

        assertThat(submission1.getResults()).hasSize(2);

        assertThat(examModelingExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).contains(submission2);
        assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();

        assertThat(submissionListTutor1CorrectionRound0).hasSize(1);
        assertThat(submissionListTutor1CorrectionRound0.getFirst()).isEqualTo(submission1);
        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).hasSize(1);
        assertThat(submissionListTutor2CorrectionRound1.getFirst()).isEqualTo(submission1);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testModelingExerciseGetRandomSubmissionEligibleForNewAssessmentOneAssessmentsInSecondCorrectionRoundWithLock() {
        submission1 = new ModelingSubmission();
        submission2 = new ModelingSubmission();
        // setup
        queryTestingBasics(this.examModelingExercise);

        participationUtilService.addResultToSubmission(submission1, AssessmentType.MANUAL, tutor1, 10D, true);

        Result resultForSecondCorrectionWithLock = submissionService.saveNewEmptyResult(submission1);
        resultForSecondCorrectionWithLock.setAssessor(tutor2);
        resultForSecondCorrectionWithLock.setAssessmentType(AssessmentType.MANUAL);
        resultForSecondCorrectionWithLock.setExerciseId(examModelingExercise.getId());
        resultRepository.save(resultForSecondCorrectionWithLock);

        // checks
        getQueryResults(examModelingExercise);

        assertThat(submission1.getResults()).hasSize(2);

        assertThat(examModelingExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).contains(submission2);
        assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();

        assertThat(submissionListTutor1CorrectionRound0).hasSize(1);
        assertThat(submissionListTutor1CorrectionRound0.getFirst()).isEqualTo(submission1);
        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).hasSize(1);
        assertThat(submissionListTutor2CorrectionRound1.getFirst()).isEqualTo(submission1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetSubmissionsWithComplaintsForExerciseAsInstructor() {
        var participation1 = participationUtilService.createAndSaveParticipationForExercise(examTextExercise, TEST_PREFIX + "student1");
        var participation2 = participationUtilService.createAndSaveParticipationForExercise(examTextExercise, TEST_PREFIX + "student2");
        var participation3 = participationUtilService.createAndSaveParticipationForExercise(examTextExercise, TEST_PREFIX + "student3");

        participationUtilService.addSubmissionWithFinishedResultsWithAssessor(participation1, new TextSubmission(), TEST_PREFIX + "tutor2");
        var submissionWithComplaintSameTutor = participationUtilService.addSubmissionWithFinishedResultsWithAssessor(participation2, new TextSubmission(),
                TEST_PREFIX + "instructor1");
        var submissionWithComplaintOtherTutor = participationUtilService.addSubmissionWithFinishedResultsWithAssessor(participation3, new TextSubmission(), TEST_PREFIX + "tutor2");
        complaintUtilService.addComplaintToSubmission(submissionWithComplaintSameTutor, TEST_PREFIX + "student2", ComplaintType.COMPLAINT);
        complaintUtilService.addComplaintToSubmission(submissionWithComplaintOtherTutor, TEST_PREFIX + "student3", ComplaintType.COMPLAINT);

        List<SubmissionWithComplaintDTO> dtoList = submissionService.getSubmissionsWithComplaintsForExercise(examTextExercise.getId(), true);

        List<Long> submissionIdsFromDTO = dtoList.stream().map(SubmissionWithComplaintDTO::submission).filter(Objects::nonNull).map(SubmissionResponseDTO::id).toList();
        List<ComplaintDTO> complaintsFromDTO = dtoList.stream().map(SubmissionWithComplaintDTO::complaint).filter(Objects::nonNull).toList();

        assertThat(dtoList).hasSize(2);
        assertThat(complaintsFromDTO).hasSize(2);
        assertThat(submissionIdsFromDTO).isEqualTo(List.of(submissionWithComplaintSameTutor.getId(), submissionWithComplaintOtherTutor.getId()));

        dtoList.forEach(dto -> {
            if (Objects.equals(dto.submission().id(), submissionWithComplaintSameTutor.getId())) {
                assertThat(complaintRepository.findByResultSubmissionId(dto.submission().id()).orElseThrow().getStudent().getLogin()).isEqualTo(TEST_PREFIX + "student2");
            }
            else if (Objects.equals(dto.submission().id(), submissionWithComplaintOtherTutor.getId())) {
                assertThat(complaintRepository.findByResultSubmissionId(dto.submission().id()).orElseThrow().getStudent().getLogin()).isEqualTo(TEST_PREFIX + "student3");
            }
            else {
                fail("Unreachable statement");
            }
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testGetSubmissionsWithComplaintsForExerciseAsTutor() {
        var participation1 = participationUtilService.createAndSaveParticipationForExercise(examTextExercise, TEST_PREFIX + "student1");
        var participation2 = participationUtilService.createAndSaveParticipationForExercise(examTextExercise, TEST_PREFIX + "student2");
        var participation3 = participationUtilService.createAndSaveParticipationForExercise(examTextExercise, TEST_PREFIX + "student3");

        participationUtilService.addSubmissionWithFinishedResultsWithAssessor(participation1, new TextSubmission(), TEST_PREFIX + "tutor2");
        var submissionWithComplaintSameTutor = participationUtilService.addSubmissionWithFinishedResultsWithAssessor(participation2, new TextSubmission(), TEST_PREFIX + "tutor1");
        var submissionWithComplaintOtherTutor = participationUtilService.addSubmissionWithFinishedResultsWithAssessor(participation3, new TextSubmission(), TEST_PREFIX + "tutor2");
        complaintUtilService.addComplaintToSubmission(submissionWithComplaintSameTutor, TEST_PREFIX + "student2", ComplaintType.COMPLAINT);
        complaintUtilService.addComplaintToSubmission(submissionWithComplaintOtherTutor, TEST_PREFIX + "student3", ComplaintType.COMPLAINT);

        List<SubmissionWithComplaintDTO> dtoList = submissionService.getSubmissionsWithComplaintsForExercise(examTextExercise.getId(), false);

        List<Long> submissionIdsFromDTO = dtoList.stream().map(SubmissionWithComplaintDTO::submission).filter(Objects::nonNull).map(SubmissionResponseDTO::id).toList();
        List<ComplaintDTO> complaintsFromDTO = dtoList.stream().map(SubmissionWithComplaintDTO::complaint).filter(Objects::nonNull).toList();

        assertThat(dtoList).hasSize(1);
        assertThat(complaintsFromDTO).hasSize(1);
        assertThat(submissionIdsFromDTO).isEqualTo(List.of(submissionWithComplaintOtherTutor.getId()));
        dtoList.forEach(dto -> {
            if (Objects.equals(dto.submission().id(), submissionWithComplaintOtherTutor.getId())) {
                assertThat(complaintRepository.findByResultSubmissionId(dto.submission().id()).orElseThrow().getStudent().getLogin()).isEqualTo(TEST_PREFIX + "student3");
            }
            else {
                fail("Unreachable statement");
            }
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testGetSubmissionsWithMoreFeedbackRequestsForExerciseAsTutor() {
        var participation1 = participationUtilService.createAndSaveParticipationForExercise(examTextExercise, TEST_PREFIX + "student1");
        var participation2 = participationUtilService.createAndSaveParticipationForExercise(examTextExercise, TEST_PREFIX + "student2");
        var participation3 = participationUtilService.createAndSaveParticipationForExercise(examTextExercise, TEST_PREFIX + "student3");

        participationUtilService.addSubmissionWithFinishedResultsWithAssessor(participation1, new TextSubmission(), TEST_PREFIX + "tutor2");
        var submissionWithRequestSameTutor = participationUtilService.addSubmissionWithFinishedResultsWithAssessor(participation2, new TextSubmission(), TEST_PREFIX + "tutor1");
        var submissionWithRequestOtherTutor = participationUtilService.addSubmissionWithFinishedResultsWithAssessor(participation3, new TextSubmission(), TEST_PREFIX + "tutor2");
        complaintUtilService.addComplaintToSubmission(submissionWithRequestSameTutor, TEST_PREFIX + "student2", ComplaintType.MORE_FEEDBACK);
        complaintUtilService.addComplaintToSubmission(submissionWithRequestOtherTutor, TEST_PREFIX + "student3", ComplaintType.MORE_FEEDBACK);

        List<SubmissionWithComplaintDTO> dtoList = submissionService.getSubmissionsWithMoreFeedbackRequestsForExercise(examTextExercise.getId());

        List<Long> submissionIdsFromDTO = dtoList.stream().map(SubmissionWithComplaintDTO::submission).filter(Objects::nonNull).map(SubmissionResponseDTO::id).toList();
        List<ComplaintDTO> requestsFromDTO = dtoList.stream().map(SubmissionWithComplaintDTO::complaint).filter(Objects::nonNull).toList();

        assertThat(dtoList).hasSize(1);
        assertThat(requestsFromDTO).hasSize(1);
        assertThat(submissionIdsFromDTO).isEqualTo(List.of(submissionWithRequestSameTutor.getId()));
        dtoList.forEach(dto -> {
            if (Objects.equals(dto.submission().id(), submissionWithRequestSameTutor.getId())) {
                assertThat(complaintRepository.findByResultSubmissionId(dto.submission().id()).orElseThrow().getStudent().getLogin()).isEqualTo(TEST_PREFIX + "student2");
            }
            else {
                fail("Unreachable statement");
            }
        });
    }

    @Test
    void testCopyFeedbackSetValues() {
        List<Feedback> oldFeedbacks = List.of(new Feedback().text("Feedback 1").credits(1.), // should get positive = true
                new Feedback().type(FeedbackType.AUTOMATIC).credits(0.).positive(true), // should stay positive
                new Feedback().detailText("test"), // no credits, should get credits = 0 and positive = true
                new Feedback().credits(-2.5) // should get positive = false
        );
        Result oldResult = new Result();
        oldResult.setExerciseId(examTextExercise.getId());
        oldResult.setFeedbacks(oldFeedbacks);

        // Copying the feedback saves the new result, so it needs the submission it belongs to.
        Submission submission = participationUtilService.addSubmission(examTextExercise, new TextSubmission(), TEST_PREFIX + "student1");
        Result newResult = new Result();
        newResult.setExerciseId(examTextExercise.getId());
        newResult.setSubmission(submission);

        Set<Feedback> newFeedbacks = submissionService.copyFeedbackToNewResult(newResult, oldResult);

        assertThat(newFeedbacks).containsExactlyInAnyOrderElementsOf(newResult.getFeedbacks()).hasSameSizeAs(oldFeedbacks);
        Feedback positiveCreditsFeedback = newFeedbacks.stream().filter(f -> "Feedback 1".equals(f.getText())).findFirst().orElseThrow();
        Feedback automaticFeedback = newFeedbacks.stream().filter(f -> f.getType() == FeedbackType.AUTOMATIC).findFirst().orElseThrow();
        Feedback noCreditsFeedback = newFeedbacks.stream().filter(f -> "test".equals(f.getDetailText())).findFirst().orElseThrow();
        Feedback negativeCreditsFeedback = newFeedbacks.stream().filter(f -> f.getCredits() != null && f.getCredits() < 0).findFirst().orElseThrow();
        assertThat(positiveCreditsFeedback.isPositive()).isTrue();
        assertThat(automaticFeedback.isPositive()).isTrue();
        assertThat(noCreditsFeedback.isPositive()).isTrue();
        assertThat(noCreditsFeedback.getCredits()).isZero();
        assertThat(negativeCreditsFeedback.isPositive()).isFalse();
    }
}
