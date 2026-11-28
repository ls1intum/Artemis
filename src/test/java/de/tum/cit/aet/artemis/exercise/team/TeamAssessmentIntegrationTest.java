package de.tum.cit.aet.artemis.exercise.team;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR;
import static de.tum.cit.aet.artemis.core.util.QueryCountAssert.assertThatDb;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.AssessmentQueueTeamDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingSubmissionForTutorAssessmentDTO;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Covers the team assessment queue of the exercise assessment dashboard and the team rules for starting and reopening assessments.
 */
class TeamAssessmentIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "teamassessmentint"; // only lower case is supported

    // the user, the queue entries, the candidate and assessed submissions, their results and the authorization lookups
    private static final long QUEUE_QUERY_BUDGET = 10;

    @Autowired
    private ResultTestRepository resultRepository;

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ExerciseTestRepository exerciseRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    private User tutor1;

    private User tutor2;

    private Course course;

    private Exam exam;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 3, 2, 0, 1);
        tutor1 = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        tutor2 = userUtilService.getUserByLogin(TEST_PREFIX + "tutor2");
        // teamExercise() turns the exam exercises into course team exercises
        course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        exam = examUtilService.addExerciseGroupsAndExercisesToExam(examUtilService.addExam(course), true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void queueRejectsExamExercisesAndStudents() throws Exception {
        Exercise examExercise = examExercise(ExerciseType.TEXT);
        examExercise.setMode(ExerciseMode.TEAM);
        exerciseRepository.save(examExercise);
        request.get(queueUrl(examExercise), HttpStatus.BAD_REQUEST, String.class);
        Exercise exercise = teamExercise(ExerciseType.TEXT);
        request.get(queueUrl(exercise) + "?correction-round=-1", HttpStatus.BAD_REQUEST, String.class);
        userUtilService.changeUser(TEST_PREFIX + "student1");
        request.get(queueUrl(exercise), HttpStatus.FORBIDDEN, String.class);
    }

    /** Tutors see and assess their own teams, instructors all teams; listing claims nothing and answers stay out of the queue. */
    @ParameterizedTest
    @EnumSource(value = ExerciseType.class, names = { "TEXT", "MODELING", "FILE_UPLOAD", "PROGRAMMING" })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void queueAndStartsRespectTeamOwnership(ExerciseType type) throws Exception {
        Exercise exercise = teamExercise(type);
        Submission own = teamSubmission(exercise, tutor1, "own");
        Submission other = teamSubmission(exercise, tutor2, "other");

        var queue = request.getList(queueUrl(exercise), HttpStatus.OK, AssessmentQueueTeamDTO.class);
        assertThat(queue).extracting(AssessmentQueueTeamDTO::shortName).containsExactly("own");
        assertThat(queue.getFirst().submission().text()).isNull();
        assertThat(queue.getFirst().submission().model()).isNull();
        assertThat(manualResults(own)).isEmpty();

        request.get(assessmentStartUrl(type, other.getId()), HttpStatus.FORBIDDEN, String.class);
        request.get(assessmentStartUrl(type, own.getId()), HttpStatus.OK, String.class);
        Result draft = manualResults(own).getFirst();
        assertThat(request.getList(queueUrl(exercise), HttpStatus.OK, AssessmentQueueTeamDTO.class).getFirst().submission().results())
                .filteredOn(result -> result.assessor() != null).extracting(result -> result.assessor().id()).containsExactly(tutor1.getId());

        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        assertThat(request.getList(queueUrl(exercise), HttpStatus.OK, AssessmentQueueTeamDTO.class)).extracting(AssessmentQueueTeamDTO::shortName).containsExactlyInAnyOrder("own",
                "other");
        request.get(assessmentStartUrl(type, own.getId()), HttpStatus.OK, String.class);
        assertThat(resultRepository.findByIdWithEagerFeedbacksAndAssessor(draft.getId()).orElseThrow().getAssessor()).isEqualTo(tutor1);
        assertThat(manualResults(other)).isEmpty();
    }

    /** Each team's own deadline, including an extension, gates the queue and new assessments; existing assessments stay reopenable. */
    @ParameterizedTest
    @EnumSource(value = ExerciseType.class, names = { "TEXT", "MODELING", "FILE_UPLOAD", "PROGRAMMING" })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void newAssessmentsWaitForEachTeamsDeadline(ExerciseType type) throws Exception {
        Exercise exercise = teamExercise(type);
        Submission regular = teamSubmission(exercise, tutor1, "own");
        Submission extended = teamSubmission(exercise, tutor1, "anotherown");
        var participation = (StudentParticipation) extended.getParticipation();
        participation.setIndividualDueDate(ZonedDateTime.now().plusDays(1));
        studentParticipationRepository.save(participation);

        assertThat(request.getList(queueUrl(exercise), HttpStatus.OK, AssessmentQueueTeamDTO.class)).extracting(entry -> entry.submission().id()).containsExactly(regular.getId());
        request.get(assessmentStartUrl(type, extended.getId()), HttpStatus.FORBIDDEN, String.class);
        assertThat(manualResults(extended)).isEmpty();
        request.get(assessmentStartUrl(type, regular.getId()), HttpStatus.OK, String.class);
        Long draftId = manualResults(regular).getFirst().getId();

        exercise.setDueDate(ZonedDateTime.now().plusDays(1));
        exerciseRepository.save(exercise);
        assertThat(request.getList(queueUrl(exercise), HttpStatus.OK, AssessmentQueueTeamDTO.class)).extracting(AssessmentQueueTeamDTO::shortName).containsExactly("own");
        // programming exercises allow no manual result before the due date at all, which is not specific to teams
        request.get(assessmentStartUrl(type, regular.getId()), type == ExerciseType.PROGRAMMING ? HttpStatus.FORBIDDEN : HttpStatus.OK, String.class);
        assertThat(manualResults(regular)).extracting(Result::getId).containsExactly(draftId);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void programmingQueueWaitsForBuildDateAndManualAssessment() throws Exception {
        var exercise = (ProgrammingExercise) teamExercise(ExerciseType.PROGRAMMING);
        Submission submission = teamSubmission(exercise, tutor1, "own");
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusDays(1));
        exerciseRepository.save(exercise);
        assertThat(request.getList(queueUrl(exercise), HttpStatus.OK, AssessmentQueueTeamDTO.class)).isEmpty();
        request.get(assessmentStartUrl(ExerciseType.PROGRAMMING, submission.getId()), HttpStatus.FORBIDDEN, String.class);

        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        exercise.setAssessmentType(AssessmentType.AUTOMATIC);
        exerciseRepository.save(exercise);
        assertThat(request.getList(queueUrl(exercise), HttpStatus.OK, AssessmentQueueTeamDTO.class)).isEmpty();
    }

    /** Unsubmitted versions and Athena results neither replace nor assess the submitted version. */
    @ParameterizedTest
    @EnumSource(value = ExerciseType.class, names = { "TEXT", "PROGRAMMING" })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void queueShowsLatestSubmittedVersion(ExerciseType type) throws Exception {
        Exercise exercise = teamExercise(type);
        Submission submitted = teamSubmission(exercise, tutor1, "own");
        Submission draft = participationUtilService.addSubmission((StudentParticipation) submitted.getParticipation(),
                type == ExerciseType.TEXT ? new TextSubmission() : new ProgrammingSubmission());
        draft.setSubmitted(false);
        draft.setSubmissionDate(submitted.getSubmissionDate().plusHours(1));
        submissionRepository.save(draft);
        saveResult(submitted, AssessmentType.AUTOMATIC_ATHENA, null);
        assertThat(request.getList(queueUrl(exercise), HttpStatus.OK, AssessmentQueueTeamDTO.class)).extracting(entry -> entry.submission().id())
                .containsExactly(submitted.getId());

        saveResult(submitted, AssessmentType.MANUAL, tutor1);
        var assessed = request.getList(queueUrl(exercise), HttpStatus.OK, AssessmentQueueTeamDTO.class);
        assertThat(assessed).extracting(entry -> entry.submission().id()).containsExactly(submitted.getId());
        assertThat(assessed.getFirst().submission().results()).filteredOn(result -> result.assessor() != null).extracting(result -> result.assessor().name())
                .containsExactly(tutor1.getName());
    }

    /** Programming candidates prefer the latest submission date and use the id to break ties. */
    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void programmingQueueSelectsLatestCandidateByDate(boolean sameDate) throws Exception {
        Exercise exercise = teamExercise(ExerciseType.PROGRAMMING);
        Submission first = teamSubmission(exercise, tutor1, "own");
        Submission second = participationUtilService.addSubmission((StudentParticipation) first.getParticipation(), new ProgrammingSubmission());
        second.setSubmitted(true);
        second.setSubmissionDate(sameDate ? first.getSubmissionDate() : first.getSubmissionDate().minusDays(1));
        submissionRepository.save(second);
        assertThat(request.getList(queueUrl(exercise), HttpStatus.OK, AssessmentQueueTeamDTO.class)).extracting(entry -> entry.submission().id())
                .containsExactly(sameDate ? second.getId() : first.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void programmingStartAssessesLatestRepositoryCommit() throws Exception {
        Exercise exercise = teamExercise(ExerciseType.PROGRAMMING);
        var latest = (ProgrammingSubmission) teamSubmission(exercise, tutor1, "own");
        var older = new ProgrammingSubmission();
        older.setCommitHash("1111111111111111111111111111111111111111");
        older.setSubmitted(true);
        older.setSubmissionDate(latest.getSubmissionDate().minusHours(1));
        participationUtilService.addSubmission((StudentParticipation) latest.getParticipation(), older);

        var opened = request.get(assessmentStartUrl(ExerciseType.PROGRAMMING, older.getId()), HttpStatus.OK, ProgrammingSubmissionForTutorAssessmentDTO.class);
        assertThat(opened.id()).isEqualTo(latest.getId());
        assertThat(manualResults(older)).isEmpty();
    }

    /** Practice participations without a team are not restricted to a team tutor or deadline, but drafts stay locked to their assessor. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void teamlessPracticeAssessmentIsNotRestricted() throws Exception {
        Exercise exercise = teamExercise(ExerciseType.TEXT);
        var participation = participationUtilService.createAndSavePracticeParticipationForExercise(exercise, TEST_PREFIX + "student1");
        Submission submission = participationUtilService.addSubmission(participation, new TextSubmission().text("Practice answer"));
        exercise.setDueDate(ZonedDateTime.now().plusDays(1));
        exerciseRepository.save(exercise);

        request.get(assessmentStartUrl(ExerciseType.TEXT, submission.getId()), HttpStatus.OK, String.class);
        assertThat(manualResults(submission)).singleElement().satisfies(result -> assertThat(result.getAssessor()).isEqualTo(tutor1));
        userUtilService.changeUser(TEST_PREFIX + "tutor2");
        request.get(assessmentStartUrl(ExerciseType.TEXT, submission.getId()), HttpStatus.LOCKED, String.class);
    }

    /** Opening a completed team result does not claim it, even when the tutor has no lock capacity left. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void openingCompletedAssessmentAtLockLimitDoesNotClaimIt() throws Exception {
        Exercise exercise = teamExercise(ExerciseType.TEXT);
        Submission completedSubmission = teamSubmission(exercise, tutor1, "own");
        for (int i = 0; i < MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR; i++) {
            saveResult(participationUtilService.addSubmission((StudentParticipation) completedSubmission.getParticipation(), new TextSubmission().text("Draft")),
                    AssessmentType.MANUAL, tutor1);
        }
        Result completed = saveResult(completedSubmission, AssessmentType.SEMI_AUTOMATIC, null);
        completed.setCompletionDate(ZonedDateTime.now());
        resultRepository.save(completed);

        request.get(assessmentStartUrl(ExerciseType.TEXT, completedSubmission.getId()), HttpStatus.OK, String.class);
        Result saved = resultRepository.findByIdWithEagerFeedbacksAndAssessor(completed.getId()).orElseThrow();
        assertThat(saved.getAssessor()).isNull();
        assertThat(saved.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        assertThat(submissionService.getLockedSubmissions(course.getId())).hasSize(Math.toIntExact(MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void queueUsesConstantNumberOfQueries() throws Exception {
        Exercise exercise = teamExercise(ExerciseType.TEXT);
        teamSubmission(exercise, tutor1, "own");
        saveResult(teamSubmission(exercise, tutor1, "anotherown"), AssessmentType.MANUAL, tutor1);
        teamSubmission(exercise, tutor2, "other");
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        var queue = assertThatDb(() -> request.getList(queueUrl(exercise), HttpStatus.OK, AssessmentQueueTeamDTO.class)).hasBeenCalledAtMostTimes(QUEUE_QUERY_BUDGET);
        assertThat(queue).hasSize(3);
    }

    private Exercise examExercise(ExerciseType type) {
        return exam.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream()).filter(exercise -> exercise.getExerciseType() == type).findFirst().orElseThrow();
    }

    /** Turns an exam exercise into a released course team exercise whose deadline has passed. */
    private Exercise teamExercise(ExerciseType type) {
        Exercise exercise = examExercise(type);
        exercise.setExerciseGroup(null);
        exercise.setCourse(course);
        exercise.setMode(ExerciseMode.TEAM);
        exercise.setReleaseDate(ZonedDateTime.now().minusDays(3));
        exercise.setDueDate(ZonedDateTime.now().minusDays(1));
        exercise.setAssessmentDueDate(ZonedDateTime.now().plusDays(1));
        exercise.setMaxPoints(10.0);
        return exerciseRepository.save(exercise);
    }

    /** Creates a team owned by the given tutor with one submitted submission; programming submissions also get an automatic result. */
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
            case ProgrammingExercise ignored -> {
                var programmingSubmission = new ProgrammingSubmission();
                programmingSubmission.setCommitHash(gitService.getLastCommitHash(((ProgrammingExerciseStudentParticipation) participation).getVcsRepositoryUri()));
                yield programmingSubmission;
            }
            default -> throw new IllegalArgumentException("Unsupported assessment exercise");
        };
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now().minusDays(2));
        submission = participationUtilService.addSubmission(participation, submission);
        if (exercise instanceof ProgrammingExercise) {
            saveResult(submission, AssessmentType.AUTOMATIC, null);
        }
        return submission;
    }

    private Result saveResult(Submission submission, AssessmentType assessmentType, User assessor) {
        var result = new Result();
        result.setSubmission(submission);
        result.setExerciseId(submission.getParticipation().getExercise().getId());
        result.setCorrectionRound(0);
        result.setAssessmentType(assessmentType);
        result.setAssessor(assessor);
        return resultRepository.save(result);
    }

    private List<Result> manualResults(Submission submission) {
        return List.copyOf(submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(submission.getId()).getManualResults());
    }

    private static String queueUrl(Exercise exercise) {
        return "/api/exercise/exercises/" + exercise.getId() + "/assessment-queue-teams";
    }

    private static String assessmentStartUrl(ExerciseType type, Long submissionId) {
        return switch (type) {
            case TEXT -> "/api/text/text-submissions/" + submissionId + "/for-assessment";
            case MODELING -> "/api/modeling/modeling-submissions/" + submissionId + "/for-assessment";
            case FILE_UPLOAD -> "/api/fileupload/file-upload-submissions/" + submissionId;
            case PROGRAMMING -> "/api/programming/programming-submissions/" + submissionId + "/lock";
            default -> throw new IllegalArgumentException("Unsupported assessment type: " + type);
        };
    }
}
