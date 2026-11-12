package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import de.tum.cit.aet.artemis.exercise.dto.AssessmentQueueTeamDTO;
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

    @Autowired
    private ExerciseTestRepository exerciseRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionService programmingSubmissionService;

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

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void assessmentQueueTeamsRejectsStudents() throws Exception {
        request.get("/api/exercise/exercises/" + examTextExercise.getId() + "/assessment-queue-teams", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void shouldRejectTeamQueueWhenExerciseBelongsToExam() throws Exception {
        examTextExercise.setMode(ExerciseMode.TEAM);
        exerciseRepository.save(examTextExercise);
        request.get("/api/exercise/exercises/" + examTextExercise.getId() + "/assessment-queue-teams", HttpStatus.BAD_REQUEST, String.class);
    }

    @ParameterizedTest
    @EnumSource(value = AssessmentType.class, names = { "MANUAL", "SEMI_AUTOMATIC" })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void teamQueueExistingAssessmentIgnoresAthenaResults(AssessmentType assessmentType) throws Exception {
        var exercise = teamExercise(ExerciseType.TEXT);
        var submission = teamSubmission(exercise, tutor1, "own");
        var athenaResult = new Result();
        athenaResult.setSubmission(submission);
        athenaResult.setExerciseId(exercise.getId());
        athenaResult.setCorrectionRound(0);
        athenaResult.setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
        resultRepository.save(athenaResult);
        assertThat(studentParticipationRepository.findTeamAssessmentQueueParticipations(exercise.getId(), tutor1.getId(), 0)).hasSize(1)
                .allSatisfy(entry -> assertThat(entry.submissionId()).isNull());
        var manualResult = new Result();
        manualResult.setSubmission(submission);
        manualResult.setExerciseId(exercise.getId());
        manualResult.setCorrectionRound(0);
        manualResult.setAssessmentType(assessmentType);
        manualResult.setAssessor(tutor1);
        manualResult.setScore(75.0);
        manualResult.setRated(true);
        resultRepository.save(manualResult);
        var newerSubmission = participationUtilService.addSubmission((StudentParticipation) submission.getParticipation(), new TextSubmission().text("Later answer"));
        var newerAthenaResult = new Result();
        newerAthenaResult.setSubmission(newerSubmission);
        newerAthenaResult.setExerciseId(exercise.getId());
        newerAthenaResult.setCorrectionRound(0);
        newerAthenaResult.setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
        resultRepository.save(newerAthenaResult);
        assertThat(studentParticipationRepository.findTeamAssessmentQueueParticipations(exercise.getId(), tutor1.getId(), 0)).extracting(entry -> entry.submissionId())
                .containsExactly(submission.getId());
        var queue = request.getList("/api/exercise/exercises/" + exercise.getId() + "/assessment-queue-teams", HttpStatus.OK, AssessmentQueueTeamDTO.class);
        assertThat(queue).hasSize(1);
        var displayed = queue.getFirst().submission().results().stream().filter(result -> result.assessmentType() == assessmentType).findFirst().orElseThrow();
        assertThat(displayed.correctionRound()).isZero();
        assertThat(displayed.completionDate()).isNull();
        assertThat(displayed.score()).isEqualTo(75.0);
        assertThat(displayed.rated()).isTrue();
        assertThat(displayed.assessor().id()).isEqualTo(tutor1.getId());
        assertThat(displayed.assessor().name()).isEqualTo(tutor1.getName());
        assertThat(queue.getFirst().submission().id()).isEqualTo(submission.getId());
    }

    /** The queue orders assessed work by submission date, using IDs only to break date ties. */
    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = { false, true })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void teamQueueSelectsLatestAssessedSubmissionByDate(boolean sameDate) {
        var exercise = teamExercise(ExerciseType.TEXT);
        var first = teamSubmission(exercise, tutor1, "own");
        var second = participationUtilService.addSubmission((StudentParticipation) first.getParticipation(), new TextSubmission().text("Other answer"));
        second.setSubmissionDate(sameDate ? first.getSubmissionDate() : first.getSubmissionDate().minusDays(1));
        submissionRepository.save(second);
        for (var submission : List.of(first, second)) {
            var result = new Result();
            result.setSubmission(submission);
            result.setExerciseId(exercise.getId());
            result.setCorrectionRound(0);
            result.setAssessmentType(AssessmentType.MANUAL);
            resultRepository.save(result);
        }
        assertThat(second.getId()).isGreaterThan(first.getId());
        assertThat(studentParticipationRepository.findTeamAssessmentQueueParticipations(exercise.getId(), tutor1.getId(), 0)).extracting(entry -> entry.submissionId())
                .containsExactly(sameDate ? second.getId() : first.getId());
    }

    /** Opening a completed team result cannot claim it, including when the tutor has no lock capacity. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void completedTeamTextResultRemainsUnassignedAtLockLimit() {
        var exercise = teamExercise(ExerciseType.TEXT);
        var completedSubmission = teamSubmission(exercise, tutor1, "own");
        for (int i = 0; i < MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR; i++) {
            var draft = participationUtilService.addSubmission((StudentParticipation) completedSubmission.getParticipation(), new TextSubmission().text("Draft"));
            var result = new Result();
            result.setSubmission(draft);
            result.setExerciseId(exercise.getId());
            result.setAssessmentType(AssessmentType.MANUAL);
            result.setCorrectionRound(0);
            result.setAssessor(tutor1);
            resultRepository.save(result);
        }
        var completed = new Result();
        completed.setSubmission(completedSubmission);
        completed.setExerciseId(exercise.getId());
        completed.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        completed.setCorrectionRound(0);
        completed.setCompletionDate(ZonedDateTime.now());
        resultRepository.save(completed);
        var locked = submissionService.lockSubmission(submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(completedSubmission.getId()), 0);
        assertThat(locked.getId()).isEqualTo(completed.getId());
        var saved = resultRepository.findByIdWithEagerFeedbacksAndAssessor(completed.getId()).orElseThrow();
        assertThat(saved.getAssessor()).isNull();
        assertThat(saved.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        assertThat(saved.getCompletionDate()).isNotNull();
        assertThat(submissionService.getLockedSubmissions(exercise.getCourseViaExerciseGroupOrCourseMemberElseThrow().getId()))
                .hasSize(Math.toIntExact(MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({ "TEXT,false", "TEXT,true", "MODELING,false", "MODELING,true", "FILE_UPLOAD,false", "FILE_UPLOAD,true", "PROGRAMMING,false",
            "PROGRAMMING,true" })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void reassignedDraftCanReopenAtLimitButUnassignedDraftCannotBeClaimed(ExerciseType type, boolean unassigned) throws Exception {
        var exercise = teamExercise(type);
        var own = teamSubmission(exercise, tutor1, "own");
        request.get(assessmentStartUrl(type, own.getId()), HttpStatus.OK, String.class);
        var ownResultId = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(own.getId()).getLatestResult().getId();
        for (int i = 1; i < MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR; i++) {
            Submission additionalDraft = switch (type) {
                case TEXT -> new TextSubmission().text("Open draft");
                case MODELING -> new ModelingSubmission().model("{}");
                case FILE_UPLOAD -> new FileUploadSubmission();
                case PROGRAMMING -> new ProgrammingSubmission();
                default -> throw new IllegalArgumentException();
            };
            var additional = participationUtilService.addSubmission((StudentParticipation) own.getParticipation(), additionalDraft);
            var result = new Result();
            result.setSubmission(additional);
            result.setExerciseId(exercise.getId());
            result.setAssessmentType(AssessmentType.MANUAL);
            result.setCorrectionRound(0);
            result.setAssessor(tutor1);
            resultRepository.save(result);
        }
        var transferred = teamSubmission(exercise, tutor2, "other");
        userUtilService.changeUser(TEST_PREFIX + "tutor2");
        request.get(assessmentStartUrl(type, transferred.getId()), HttpStatus.OK, String.class);
        var transferredResultId = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(transferred.getId()).getLatestResult().getId();
        var team = teamRepository.findWithStudentsByIdElseThrow(((StudentParticipation) transferred.getParticipation()).getTeam().orElseThrow().getId());
        team.setOwner(tutor1);
        teamRepository.saveWithTutorChange(exercise, team);
        if (unassigned) {
            var draft = resultRepository.findByIdWithEagerFeedbacksAndAssessor(transferredResultId).orElseThrow();
            draft.setAssessor(null);
            resultRepository.save(draft);
        }
        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        var courseId = exercise.getCourseViaExerciseGroupOrCourseMemberElseThrow().getId();
        int expectedLocks = Math.toIntExact(MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR) + (unassigned ? 0 : 1);
        assertThat(submissionService.getLockedSubmissions(courseId)).hasSize(expectedLocks);
        request.get(assessmentStartUrl(type, transferred.getId()), unassigned ? HttpStatus.BAD_REQUEST : HttpStatus.OK, String.class);
        assertThat(resultRepository.findByIdWithEagerFeedbacksAndAssessor(transferredResultId).orElseThrow().getAssessor()).isEqualTo(unassigned ? null : tutor1);
        request.get(assessmentStartUrl(type, own.getId()), HttpStatus.OK, String.class);
        assertThat(resultRepository.findByIdWithEagerFeedbacksAndAssessor(ownResultId).orElseThrow().getAssessor()).isEqualTo(tutor1);
        assertThat(submissionService.getLockedSubmissions(courseId)).hasSize(expectedLocks);
    }

    @ParameterizedTest
    @EnumSource(value = ExerciseType.class, names = { "TEXT", "MODELING", "FILE_UPLOAD", "PROGRAMMING" })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void teamQueueAndDirectStartsRespectOwnership(ExerciseType type) throws Exception {
        var exercise = teamExercise(type);
        var own = teamSubmission(exercise, tutor1, "own");
        var other = teamSubmission(exercise, tutor2, "other");
        var queueUrl = "/api/exercise/exercises/" + exercise.getId() + "/assessment-queue-teams";
        assertThat(request.getList(queueUrl, HttpStatus.OK, AssessmentQueueTeamDTO.class)).extracting(AssessmentQueueTeamDTO::shortName).containsExactly("own");
        request.get(assessmentStartUrl(type, other.getId()), HttpStatus.FORBIDDEN, String.class);
        request.get(assessmentStartUrl(type, own.getId()), HttpStatus.OK, String.class);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        assertThat(request.getList(queueUrl, HttpStatus.OK, AssessmentQueueTeamDTO.class)).extracting(AssessmentQueueTeamDTO::shortName).containsExactlyInAnyOrder("own", "other");
        var ownResult = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(own.getId()).getLatestResult();
        request.get(assessmentStartUrl(type, own.getId()), HttpStatus.OK, String.class);
        assertThat(resultRepository.findByIdWithEagerFeedbacksAndAssessor(ownResult.getId()).orElseThrow().getAssessor()).isEqualTo(tutor1);
        request.get(assessmentStartUrl(type, other.getId()), HttpStatus.OK, String.class);
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

    @ParameterizedTest
    @EnumSource(value = ExerciseType.class, names = { "TEXT", "MODELING", "FILE_UPLOAD", "PROGRAMMING" })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void teamAssessmentFollowsTutorChange(ExerciseType type) throws Exception {
        var exercise = teamExercise(type);
        var submission = teamSubmission(exercise, tutor1, "transferred");
        request.get(assessmentStartUrl(type, submission.getId()), HttpStatus.OK, String.class);
        // Programming can create a submission for the latest commit when locking.
        var draft = submissionService.<Submission>getAllSubmissionsAssessedByTutorForCorrectionRoundAndExerciseIgnoreTestRuns(exercise.getId(), tutor1, false, 0).getFirst();
        var resultId = draft.getLatestResult().getId();
        var savedDraft = resultRepository.findByIdWithEagerFeedbacksAndAssessor(resultId).orElseThrow();
        var feedback = new Feedback().text("Saved draft feedback").credits(1.0).type(FeedbackType.MANUAL_UNREFERENCED);
        feedback.setResult(savedDraft);
        savedDraft.getFeedbacks().add(feedback);
        resultRepository.save(savedDraft);
        var teamId = ((StudentParticipation) submission.getParticipation()).getTeam().orElseThrow().getId();
        var team = teamRepository.findWithStudentsByIdElseThrow(teamId);
        team.setOwner(tutor2);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        request.put("/api/exercise/exercises/" + exercise.getId() + "/teams/" + teamId, de.tum.cit.aet.artemis.exercise.dto.TeamInputDTO.of(team), HttpStatus.OK);
        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        assertThat(submissionService.getLockedSubmissions(exercise.getCourseViaExerciseGroupOrCourseMember().getId())).isEmpty();
        if (type == ExerciseType.PROGRAMMING) {
            request.put("/api/programming/programming-submissions/" + draft.getId() + "/cancel-assessment?resultId=" + resultId, null, HttpStatus.FORBIDDEN);
            assertThat(resultRepository.findByIdWithEagerFeedbacksAndAssessor(resultId).orElseThrow().getFeedbacks()).extracting(Feedback::getText)
                    .contains("Saved draft feedback");
        }
        assertThat(request.getList("/api/exercise/exercises/" + exercise.getId() + "/assessment-queue-teams", HttpStatus.OK, AssessmentQueueTeamDTO.class)).isEmpty();

        assertThat(submissionService.<Submission>getAllSubmissionsAssessedByTutorForCorrectionRoundAndExerciseIgnoreTestRuns(exercise.getId(), tutor1, false, 0)).isEmpty();
        writeTeamAssessment(type, draft, resultId, HttpStatus.FORBIDDEN, false);
        completeTeamAssessment(type, draft, resultId, HttpStatus.FORBIDDEN);
        assertThat(resultRepository.findByIdWithEagerFeedbacksAndAssessor(resultId).orElseThrow().getAssessor()).isEqualTo(tutor2);
        userUtilService.changeUser(TEST_PREFIX + "tutor2");
        assertThat(request.getList("/api/exercise/exercises/" + exercise.getId() + "/assessment-queue-teams", HttpStatus.OK, AssessmentQueueTeamDTO.class))
                .extracting(entry -> entry.submission().id()).containsExactly(draft.getId());
        assertThat(submissionService.<Submission>getAllSubmissionsAssessedByTutorForCorrectionRoundAndExerciseIgnoreTestRuns(exercise.getId(), tutor2, false, 0))
                .extracting(Submission::getId).containsExactly(draft.getId());
        assertThat(request.get(assessmentStartUrl(type, draft.getId()), HttpStatus.OK, String.class)).contains(tutor2.getLogin());
        assertThat(resultRepository.findByIdWithEagerFeedbacksAndAssessor(resultId).orElseThrow().getAssessor()).isEqualTo(tutor2);
        assertThat(resultRepository.findByIdWithEagerFeedbacksAndAssessor(resultId).orElseThrow().getFeedbacks()).extracting(Feedback::getText).contains("Saved draft feedback");
        assertThat(request.get(assessmentListUrl(type, exercise.getId()), HttpStatus.OK, String.class)).contains("\"id\":" + draft.getId());
        completeTeamAssessment(type, draft, resultId, HttpStatus.OK);

        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        request.get(assessmentStartUrl(type, draft.getId()), HttpStatus.FORBIDDEN, String.class);
        writeTeamAssessment(type, draft, resultId, HttpStatus.FORBIDDEN, false);
        completeTeamAssessment(type, draft, resultId, HttpStatus.FORBIDDEN);
        userUtilService.changeUser(TEST_PREFIX + "tutor2");
        var completed = resultRepository.findByIdWithEagerFeedbacksAndAssessor(resultId).orElseThrow();
        assertThat(completed.getAssessor()).isEqualTo(tutor2);
        assertThat(completed.getCompletionDate()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(value = ExerciseType.class, names = { "TEXT", "MODELING", "FILE_UPLOAD", "PROGRAMMING" })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void teamQueueListsAllEligibleSubmissionsAndDraftsWithoutClaiming(ExerciseType type) throws Exception {
        var exercise = teamExercise(type);
        var own = teamSubmission(exercise, tutor1, "own");
        var anotherOwn = teamSubmission(exercise, tutor1, "anotherown");
        var other = teamSubmission(exercise, tutor2, "other");
        var url = "/api/exercise/exercises/" + exercise.getId() + "/assessment-queue-teams";
        request.get(url + "?correction-round=-1", HttpStatus.BAD_REQUEST, String.class);
        exercise.setDueDate(ZonedDateTime.now().plusDays(1));
        exerciseRepository.save(exercise);
        assertThat(request.getList(url, HttpStatus.OK, AssessmentQueueTeamDTO.class)).isEmpty();
        exercise.setDueDate(ZonedDateTime.now().minusDays(1));
        exerciseRepository.save(exercise);
        var queue = request.getList(url, HttpStatus.OK, AssessmentQueueTeamDTO.class);
        assertThat(queue).extracting(entry -> entry.submission().id()).containsExactlyInAnyOrder(own.getId(), anotherOwn.getId());
        assertThat(queue).allSatisfy(entry -> {
            assertThat(entry.submission().text()).isNull();
            assertThat(entry.submission().model()).isNull();
        });
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        assertThat(request.getList(url, HttpStatus.OK, AssessmentQueueTeamDTO.class)).extracting(entry -> entry.submission().id()).containsExactlyInAnyOrder(own.getId(),
                anotherOwn.getId(), other.getId());
        for (var submission : List.of(own, anotherOwn, other)) {
            assertThat(submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(submission.getId()).getManualResults()).isEmpty();
        }
        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        request.get(assessmentStartUrl(type, anotherOwn.getId()), HttpStatus.OK, String.class);
        queue = request.getList(url, HttpStatus.OK, AssessmentQueueTeamDTO.class);
        assertThat(queue).hasSize(2);
        var draft = queue.stream().filter(entry -> entry.shortName().equals("anotherown")).findFirst().orElseThrow().submission();
        assertThat(draft.results()).anySatisfy(result -> assertThat(result.assessor()).isNotNull());
        exercise.setDueDate(ZonedDateTime.now().plusDays(1));
        exerciseRepository.save(exercise);
        assertThat(request.getList(url, HttpStatus.OK, AssessmentQueueTeamDTO.class)).extracting(AssessmentQueueTeamDTO::shortName).containsExactly("anotherown");
        exercise.setDueDate(ZonedDateTime.now().minusDays(1));
        exerciseRepository.save(exercise);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        assertThat(request.getList(url, HttpStatus.OK, AssessmentQueueTeamDTO.class)).hasSize(3);
        assertThat(resultRepository.findByIdWithEagerFeedbacksAndAssessor(draft.results().stream().filter(result -> result.assessor() != null).findFirst().orElseThrow().id())
                .orElseThrow().getAssessor()).isEqualTo(tutor1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void teamQueueIncludesLateCandidatesWhileRandomSelectionPrefersOnTime() throws Exception {
        var exercise = teamExercise(ExerciseType.TEXT);
        var onTime = teamSubmission(exercise, tutor1, "own");
        var late = teamSubmission(exercise, tutor1, "anotherown");
        late.setSubmissionDate(exercise.getDueDate().plusHours(1));
        submissionRepository.save(late);
        assertThat(request.getList("/api/exercise/exercises/" + exercise.getId() + "/assessment-queue-teams", HttpStatus.OK, AssessmentQueueTeamDTO.class))
                .extracting(entry -> entry.submission().id()).containsExactlyInAnyOrder(onTime.getId(), late.getId());
        assertThat(submissionService.getRandomAssessableSubmission(exercise, false, 0)).get().extracting(Submission::getId).isEqualTo(onTime.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void teamQueueUsesEachTeamsEffectiveDeadline() throws Exception {
        var exercise = teamExercise(ExerciseType.TEXT);
        exercise.setDueDate(ZonedDateTime.now().plusDays(1));
        exerciseRepository.save(exercise);
        var early = teamSubmission(exercise, tutor1, "own");
        var regular = teamSubmission(exercise, tutor1, "anotherown");
        var participation = (StudentParticipation) early.getParticipation();
        participation.setIndividualDueDate(ZonedDateTime.now().minusDays(1));
        studentParticipationRepository.save(participation);
        var url = "/api/exercise/exercises/" + exercise.getId() + "/assessment-queue-teams";
        assertThat(request.getList(url, HttpStatus.OK, AssessmentQueueTeamDTO.class)).extracting(entry -> entry.submission().id()).containsExactly(early.getId());
        exercise.setDueDate(null);
        exerciseRepository.save(exercise);
        assertThat(request.getList(url, HttpStatus.OK, AssessmentQueueTeamDTO.class)).extracting(entry -> entry.submission().id()).containsExactlyInAnyOrder(early.getId(),
                regular.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void automaticProgrammingTeamQueueHasNoNewCandidates() throws Exception {
        var exercise = (ProgrammingExercise) teamExercise(ExerciseType.PROGRAMMING);
        teamSubmission(exercise, tutor1, "own");
        exercise.setAssessmentType(AssessmentType.AUTOMATIC);
        exerciseRepository.save(exercise);
        assertThat(request.getList("/api/exercise/exercises/" + exercise.getId() + "/assessment-queue-teams", HttpStatus.OK, AssessmentQueueTeamDTO.class)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void teamExercisePracticeDraftCanBeReopenedOnlyByItsAssessor() throws Exception {
        var exercise = teamExercise(ExerciseType.TEXT);
        var participation = participationUtilService.createAndSavePracticeParticipationForExercise(exercise, TEST_PREFIX + "student1");
        var submission = participationUtilService.addSubmission(participation, new TextSubmission().text("Practice answer"));
        var draft = new Result();
        draft.setSubmission(submission);
        draft.setExerciseId(exercise.getId());
        draft.setAssessmentType(AssessmentType.MANUAL);
        draft.setAssessor(tutor1);
        draft.setCorrectionRound(0);
        draft = resultRepository.save(draft);
        var courseId = exercise.getCourseViaExerciseGroupOrCourseMemberElseThrow().getId();
        assertThat(submissionService.getLockedSubmissions(courseId)).extracting(Submission::getId).contains(submission.getId());
        assertThat(submissionRepository.countLockedSubmissionsByUserIdAndCourseId(tutor1.getId(), courseId)).isEqualTo(1);
        var url = assessmentStartUrl(ExerciseType.TEXT, submission.getId());
        request.get(url, HttpStatus.OK, String.class);
        assertThat(resultRepository.findByIdWithEagerFeedbacksAndAssessor(draft.getId()).orElseThrow().getAssessor()).isEqualTo(tutor1);
        userUtilService.changeUser(TEST_PREFIX + "tutor2");
        request.get(url, HttpStatus.LOCKED, String.class);
        assertThat(resultRepository.findByIdWithEagerFeedbacksAndAssessor(draft.getId()).orElseThrow().getAssessor()).isEqualTo(tutor1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void programmingTeamAssessmentKeepsSelectedCommit() throws Exception {
        var exercise = teamExercise(ExerciseType.PROGRAMMING);
        var selected = (ProgrammingSubmission) teamSubmission(exercise, tutor1, "own");
        selected.setCommitHash("1111111111111111111111111111111111111111");
        submissionRepository.save(selected);
        var newer = new ProgrammingSubmission();
        newer.setCommitHash("2222222222222222222222222222222222222222");
        participationUtilService.addSubmission((StudentParticipation) selected.getParticipation(), newer);
        var opened = request.get(assessmentStartUrl(ExerciseType.PROGRAMMING, selected.getId()), HttpStatus.OK,
                de.tum.cit.aet.artemis.programming.dto.ProgrammingSubmissionForTutorAssessmentDTO.class);
        assertThat(opened.id()).isEqualTo(selected.getId());
        assertThat(opened.commitHash()).isEqualTo(selected.getCommitHash());
        assertThat(submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(newer.getId()).getManualResults()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = ExerciseType.class, names = { "MODELING", "FILE_UPLOAD", "PROGRAMMING" })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void teamlessPracticeDraftRetainsAssessorWhenOpenedByAnotherTutor(ExerciseType type) throws Exception {
        var exercise = teamExercise(type);
        var submission = teamSubmission(exercise, tutor1, "own");
        StudentParticipation participation = type == ExerciseType.PROGRAMMING ? new de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation()
                : new StudentParticipation();
        participation.setExercise(exercise);
        participation.setInitializationState(submission.getParticipation().getInitializationState());
        participation.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        participation.setTestRun(true);
        participation = studentParticipationRepository.save(participation);
        submission.setParticipation(participation);
        submissionRepository.save(submission);
        var draft = new Result();
        draft.setSubmission(submission);
        draft.setExerciseId(exercise.getId());
        draft.setAssessmentType(type == ExerciseType.PROGRAMMING ? AssessmentType.SEMI_AUTOMATIC : AssessmentType.MANUAL);
        draft.setAssessor(tutor1);
        draft.setCorrectionRound(0);
        draft = resultRepository.save(draft);
        request.get(assessmentStartUrl(type, submission.getId()), HttpStatus.OK, String.class);
        userUtilService.changeUser(TEST_PREFIX + "tutor2");
        request.get(assessmentStartUrl(type, submission.getId()), HttpStatus.OK, String.class);
        var saved = resultRepository.findByIdWithEagerFeedbacksAndAssessor(draft.getId()).orElseThrow();
        assertThat(saved.getAssessor()).isEqualTo(tutor1);
        completeTeamAssessment(type, submission, draft.getId(), HttpStatus.FORBIDDEN);
        String cancelUrl = switch (type) {
            case MODELING -> "/api/modeling/modeling-submissions/";
            case FILE_UPLOAD -> "/api/fileupload/file-upload-submissions/";
            case PROGRAMMING -> "/api/programming/programming-submissions/";
            default -> throw new IllegalArgumentException();
        };
        request.put(cancelUrl + submission.getId() + "/cancel-assessment", null, HttpStatus.FORBIDDEN);
        assertThat(submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(submission.getId()).getManualResults()).hasSize(1);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = { false, true })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void programmingTeamDraftNoteSurvivesReopenAndSave(boolean transfer) throws Exception {
        var exercise = teamExercise(ExerciseType.PROGRAMMING);
        var submission = teamSubmission(exercise, tutor1, "own");
        var opened = request.get(assessmentStartUrl(ExerciseType.PROGRAMMING, submission.getId()), HttpStatus.OK,
                de.tum.cit.aet.artemis.programming.dto.ProgrammingSubmissionForTutorAssessmentDTO.class);
        var resultId = opened.results().getFirst().id();
        var saveUrl = "/api/programming/participations/" + opened.participation().id() + "/manual-results";
        request.put(saveUrl,
                Map.of("id", resultId, "rated", true, "score", 0.0, "successful", false, "feedbacks", List.of(), "assessmentNote", Map.of("note", "Keep this saved note")),
                HttpStatus.OK);
        var saved = resultRepository.findByIdWithEagerSubmissionAndFeedbackAndAssessmentNoteElseThrow(resultId);
        var noteId = saved.getAssessmentNote().getId();
        if (transfer) {
            var team = teamRepository.findWithStudentsByIdElseThrow(((StudentParticipation) submission.getParticipation()).getTeam().orElseThrow().getId());
            team.setOwner(tutor2);
            teamRepository.saveWithTutorChange(exercise, team);
            userUtilService.changeUser(TEST_PREFIX + "tutor2");
        }
        assertThat(opened.id()).isEqualTo(submission.getId());
        var reopened = request.get(assessmentStartUrl(ExerciseType.PROGRAMMING, submission.getId()), HttpStatus.OK,
                de.tum.cit.aet.artemis.programming.dto.ProgrammingSubmissionForTutorAssessmentDTO.class);
        var result = reopened.results().getFirst();
        assertThat(result.assessmentNote()).isNotNull();
        assertThat(result.assessmentNote().note()).isEqualTo("Keep this saved note");
        request.put(saveUrl, result, HttpStatus.OK);
        var resaved = resultRepository.findByIdWithEagerSubmissionAndFeedbackAndAssessmentNoteElseThrow(resultId);
        assertThat(resaved.getAssessmentNote().getId()).isEqualTo(noteId);
        assertThat(resaved.getAssessmentNote().getNote()).isEqualTo("Keep this saved note");
        assertThat(resaved.getAssessor()).isEqualTo(transfer ? tutor2 : tutor1);
    }

    /** Unsubmitted versions cannot hide or change the selected submitted version. */
    @ParameterizedTest
    @EnumSource(value = ExerciseType.class, names = { "TEXT", "MODELING", "FILE_UPLOAD", "PROGRAMMING" })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void teamQueueIgnoresNewerUnsubmittedVersions(ExerciseType type) throws Exception {
        var exercise = teamExercise(type);
        var submitted = teamSubmission(exercise, tutor1, "own");
        Submission draft = switch (type) {
            case TEXT -> new TextSubmission();
            case MODELING -> new ModelingSubmission();
            case FILE_UPLOAD -> new FileUploadSubmission();
            case PROGRAMMING -> new ProgrammingSubmission();
            default -> throw new IllegalArgumentException();
        };
        draft = participationUtilService.addSubmission((StudentParticipation) submitted.getParticipation(), draft);
        draft.setSubmitted(false);
        var url = "/api/exercise/exercises/" + exercise.getId() + "/assessment-queue-teams";
        for (var date : List.of(submitted.getSubmissionDate().plusHours(1), submitted.getSubmissionDate().minusHours(1))) {
            draft.setSubmissionDate(date);
            submissionRepository.save(draft);
            assertThat(request.getList(url, HttpStatus.OK, AssessmentQueueTeamDTO.class)).extracting(entry -> entry.submission().id()).containsExactly(submitted.getId());
        }
        draft.setSubmissionDate(null);
        submissionRepository.save(draft);
        assertThat(request.getList(url, HttpStatus.OK, AssessmentQueueTeamDTO.class)).extracting(entry -> entry.submission().id()).containsExactly(submitted.getId());
    }

    /** Programming queue candidates use dates, except that legacy null dates retain ID ordering. */
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({ "false,false", "true,false", "false,true" })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void programmingTeamQueueSelectsLatestCandidateByDate(boolean sameDate, boolean nullDate) throws Exception {
        var exercise = teamExercise(ExerciseType.PROGRAMMING);
        var first = teamSubmission(exercise, tutor1, "own");
        var second = participationUtilService.addSubmission((StudentParticipation) first.getParticipation(), new ProgrammingSubmission());
        second.setSubmitted(true);
        second.setSubmissionDate(nullDate ? null : sameDate ? first.getSubmissionDate() : first.getSubmissionDate().minusDays(1));
        submissionRepository.save(second);
        assertThat(second.getId()).isGreaterThan(first.getId());
        assertThat(studentParticipationRepository.findCourseTeamQueueCandidates(exercise.getId(), ZonedDateTime.now(), tutor1.getId())).extracting(candidate -> candidate.id())
                .containsExactly(sameDate || nullDate ? second.getId() : first.getId());
        var selectedId = sameDate || nullDate ? second.getId() : first.getId();
        var queueUrl = "/api/exercise/exercises/" + exercise.getId() + "/assessment-queue-teams";
        assertThat(request.getList(queueUrl, HttpStatus.OK, AssessmentQueueTeamDTO.class)).extracting(entry -> entry.submission().id()).containsExactly(selectedId);
        request.get(assessmentStartUrl(ExerciseType.PROGRAMMING, selectedId), HttpStatus.OK, String.class);
        var queue = request.getList(queueUrl, HttpStatus.OK, AssessmentQueueTeamDTO.class);
        assertThat(queue).extracting(entry -> entry.submission().id()).containsExactly(selectedId);
        assertThat(queue.getFirst().submission().results()).anySatisfy(result -> {
            assertThat(result.assessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
            assertThat(result.assessor().id()).isEqualTo(tutor1.getId());
            assertThat(result.completionDate()).isNull();
        });
        if (nullDate) {
            var team = teamRepository.findWithStudentsByIdElseThrow(((StudentParticipation) first.getParticipation()).getTeam().orElseThrow().getId());
            team.setOwner(tutor2);
            teamRepository.saveWithTutorChange(exercise, team);
            userUtilService.changeUser(TEST_PREFIX + "tutor2");
            queue = request.getList(queueUrl, HttpStatus.OK, AssessmentQueueTeamDTO.class);
            assertThat(queue).extracting(entry -> entry.submission().id()).containsExactly(selectedId);
            assertThat(queue.getFirst().submission().results()).anySatisfy(result -> {
                assertThat(result.assessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
                assertThat(result.assessor().id()).isEqualTo(tutor2.getId());
            });
        }
    }

    /** Starting from a stale queue cannot bypass an extended team or exercise deadline. */
    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = { false, true })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void programmingTeamStartRechecksDeadlines(boolean individualDeadline) throws Exception {
        var exercise = teamExercise(ExerciseType.PROGRAMMING);
        var submission = teamSubmission(exercise, tutor1, "own");
        if (individualDeadline) {
            var participation = (StudentParticipation) submission.getParticipation();
            participation.setIndividualDueDate(ZonedDateTime.now().plusDays(1));
            studentParticipationRepository.save(participation);
        }
        else {
            exercise.setDueDate(ZonedDateTime.now().plusDays(1));
            exerciseRepository.save(exercise);
        }
        request.get(assessmentStartUrl(ExerciseType.PROGRAMMING, submission.getId()), HttpStatus.FORBIDDEN, String.class);
        assertThat(submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(submission.getId()).getManualResults()).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void programmingReclaimsUnassignedManualDraft() throws Exception {
        var exercise = teamExercise(ExerciseType.PROGRAMMING);
        var original = teamSubmission(exercise, tutor1, "own");
        var opened = request.get(assessmentStartUrl(ExerciseType.PROGRAMMING, original.getId()), HttpStatus.OK,
                de.tum.cit.aet.artemis.programming.dto.ProgrammingSubmissionForTutorAssessmentDTO.class);
        var resultId = opened.results().getFirst().id();
        var draft = resultRepository.findByIdWithEagerSubmissionAndFeedbackAndAssessmentNoteElseThrow(resultId);
        draft.setAssessor(null);
        var note = new de.tum.cit.aet.artemis.assessment.domain.AssessmentNote();
        note.setNote("Unassigned saved draft");
        draft.setAssessmentNote(note);
        resultRepository.save(draft);
        var reclaimed = request.get(assessmentStartUrl(ExerciseType.PROGRAMMING, original.getId()), HttpStatus.OK,
                de.tum.cit.aet.artemis.programming.dto.ProgrammingSubmissionForTutorAssessmentDTO.class);
        assertThat(reclaimed.results()).extracting(result -> result.id()).contains(resultId);
        var saved = resultRepository.findByIdWithEagerSubmissionAndFeedbackAndAssessmentNoteElseThrow(resultId);
        assertThat(saved.getAssessor()).isEqualTo(tutor1);
        assertThat(saved.getAssessmentNote().getNote()).isEqualTo("Unassigned saved draft");
        assertThat(submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(opened.id()).getManualResults()).hasSize(1);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = { false, true })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void programmingReusesCompletedResultButNotUntypedResult(boolean completed) throws Exception {
        var exercise = teamExercise(ExerciseType.PROGRAMMING);
        var original = teamSubmission(exercise, tutor1, "own");
        var opened = request.get(assessmentStartUrl(ExerciseType.PROGRAMMING, original.getId()), HttpStatus.OK,
                de.tum.cit.aet.artemis.programming.dto.ProgrammingSubmissionForTutorAssessmentDTO.class);
        var resultId = opened.results().getFirst().id();
        var result = resultRepository.findByIdWithEagerSubmissionAndFeedbackAndAssessmentNoteElseThrow(resultId);
        result.setAssessor(null);
        result.setAssessmentType(completed ? AssessmentType.SEMI_AUTOMATIC : null);
        result.setCompletionDate(completed ? ZonedDateTime.now() : null);
        resultRepository.save(result);
        var locked = programmingSubmissionService
                .lockSubmission(submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(completed ? original.getId() : opened.id()), 0);
        if (completed) {
            assertThat(locked.getId()).isEqualTo(resultId);
            var savedSubmission = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(opened.id());
            assertThat(savedSubmission.getManualResults()).hasSize(1);
            assertThat(savedSubmission.getResultForCorrectionRound(0).getId()).isEqualTo(resultId);
        }
        else {
            assertThat(locked.getId()).isNotEqualTo(resultId);
        }
        assertThat(resultRepository.findByIdWithEagerSubmissionAndFeedbackAndAssessmentNoteElseThrow(resultId).getAssessor()).isNull();
    }

    /** Returns the proposal endpoint for an exercise type. */
    private String assessmentProposalUrl(ExerciseType type, Long exerciseId) {
        var path = switch (type) {
            case TEXT -> "text/text";
            case MODELING -> "modeling/modeling";
            case FILE_UPLOAD -> "fileupload/file-upload";
            case PROGRAMMING -> "programming/programming";
            default -> throw new IllegalArgumentException("Unsupported assessment type: " + type);
        };
        var parts = path.split("/");
        return "/api/" + parts[0] + "/exercises/" + exerciseId + "/" + parts[1] + "-submission-without-assessment";
    }

    /** Returns the current tutor assessment-list endpoint. */
    private String assessmentListUrl(ExerciseType type, Long exerciseId) {
        var url = assessmentProposalUrl(type, exerciseId).replace("-submission-without-assessment", "-submissions");
        return url + (type == ExerciseType.PROGRAMMING ? "?assessedByTutor=true" : "/assessed-by-me");
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
