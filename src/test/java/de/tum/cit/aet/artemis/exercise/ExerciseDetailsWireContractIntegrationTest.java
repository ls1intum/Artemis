package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseAthenaConfigRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.plagiarism.api.PlagiarismCaseApi;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPenaltyPolicy;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.repository.QuizBatchRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizBatchService;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Pins the JSON of {@code GET exercises/{id}/details} and {@code GET participations/{id}/submissions} to what the routes
 * put on the wire while they returned entities.
 * <p>
 * The details route is also reachable with a SCORPIO tool token, so its consumer cannot be read in this repository and
 * the wire has to stay exactly as it was. Each test therefore loads and filters the entity graph the way the route did
 * before its migration, serializes it with the application mapper, and compares the whole document with the response:
 * every key, every value, down to the results of every submission. A difference is reported with its path.
 */
class ExerciseDetailsWireContractIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "exdetailswire";

    private static final String STUDENT = TEST_PREFIX + "student1";

    private static final String TUTOR = TEST_PREFIX + "tutor1";

    private static final String INSTRUCTOR = TEST_PREFIX + "instructor1";

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private ExerciseService exerciseService;

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private CourseAthenaConfigRepository courseAthenaConfigRepository;

    @Autowired
    private AuthorizationCheckService authCheckService;

    @Autowired
    private QuizBatchService quizBatchService;

    @Autowired
    private QuizBatchRepository quizBatchRepository;

    @Autowired
    private PlagiarismCaseApi plagiarismCaseApi;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private PostTestRepository postRepository;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private TeamRepository teamRepository;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        User student = userUtilService.getUserByLogin(STUDENT);
        student.setImageUrl("https://artemis.example/users/" + STUDENT + ".png");
        userTestRepository.save(student);
        User tutor = userUtilService.getUserByLogin(TUTOR);
        tutor.setImageUrl("https://artemis.example/users/" + TUTOR + ".png");
        userTestRepository.save(tutor);
    }

    @Test
    @WithMockUser(username = STUDENT, roles = "USER")
    void details_everyExerciseType_withVisibleResults_matchesTheEntityWire() throws Exception {
        Course course = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, true);
        addRichSubmissions(course);
        TextExercise textExercise = ExerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        PlagiarismCase plagiarismCase = addNotifiedPlagiarismCase(textExercise);

        for (Exercise exercise : course.getExercises()) {
            JsonNode response = assertDetailsMatchEntityWire(exercise.getId());
            JsonNode submissions = response.at("/exercise/studentParticipations/0/submissions");
            assertThat(submissions.size()).as("submissions of %s", exercise.getType()).isGreaterThanOrEqualTo(1);
            assertThat(StreamSupport.stream(submissions.spliterator(), false).anyMatch(submission -> submission.path("results").size() > 0))
                    .as("a %s submission carries its visible results", exercise.getType()).isTrue();
        }
        JsonNode textResponse = getJson("/api/exercise/exercises/" + textExercise.getId() + "/details");
        assertThat(textResponse.at("/plagiarismCaseInfo/id").asLong()).isEqualTo(plagiarismCase.getId());
        assertThat(textResponse.at("/plagiarismCaseInfo/verdict").asString()).isEqualTo(PlagiarismVerdict.WARNING.name());
        JsonNode quizResponse = getJson("/api/exercise/exercises/" + ExerciseUtilService.getFirstExerciseWithType(course, QuizExercise.class).getId() + "/details");
        assertThat(quizResponse.at("/exercise/quizBatches").size()).as("the synchronized quiz reports its batch").isEqualTo(1);
    }

    @Test
    @WithMockUser(username = STUDENT, roles = "USER")
    void details_everyExerciseType_beforeTheAssessmentDueDate_matchesTheEntityWire() throws Exception {
        Course course = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, false);
        for (Exercise exercise : course.getExercises()) {
            JsonNode response = assertDetailsMatchEntityWire(exercise.getId());
            if (!(exercise instanceof ProgrammingExercise) && !(exercise instanceof QuizExercise)) {
                assertThat(response.at("/exercise/studentParticipations/0/submissions/0").has("results")).as("manual results of %s stay hidden", exercise.getType()).isFalse();
            }
        }
    }

    @Test
    @WithMockUser(username = STUDENT, roles = "USER")
    void details_teamExercises_matchTheEntityWire() throws Exception {
        User student = userUtilService.getUserByLogin(STUDENT);
        User tutor = userUtilService.getUserByLogin(TUTOR);

        Course textCourse = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("team wire text", TEST_PREFIX);
        TextExercise textExercise = ExerciseUtilService.getFirstExerciseWithType(textCourse, TextExercise.class);
        textExercise.setMode(ExerciseMode.TEAM);
        textExercise = exerciseRepository.save(textExercise);
        Team textTeam = teamUtilService.createTeam(Set.of(student, userUtilService.getUserByLogin(TEST_PREFIX + "student2")), tutor, textExercise, TEST_PREFIX + "textteam");
        textTeam.setImage("data:image/png;base64,iVBORw0KGgo=");
        textTeam = teamRepository.save(textTeam);
        StudentParticipation textParticipation = participationUtilService.addTeamParticipationForExercise(textExercise, textTeam.getId());
        TextSubmission textSubmission = new TextSubmission();
        textSubmission.setText("team text");
        addSubmissionWithResult(textParticipation, textSubmission, AssessmentType.AUTOMATIC);

        Course programmingCourse = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        ProgrammingExercise programmingExercise = ExerciseUtilService.getFirstExerciseWithType(programmingCourse, ProgrammingExercise.class);
        programmingExercise.setMode(ExerciseMode.TEAM);
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusDays(1));
        programmingExercise = exerciseRepository.save(programmingExercise);
        Team programmingTeam = teamUtilService.createTeam(Set.of(student), tutor, programmingExercise, TEST_PREFIX + "progteam");
        programmingTeam.setImage("data:image/png;base64,iVBORw0KGgo=");
        programmingTeam = teamRepository.save(programmingTeam);
        ProgrammingExerciseStudentParticipation teamParticipationToSave = ParticipationFactory.generateTeamProgrammingExerciseStudentParticipation(programmingExercise,
                programmingTeam);
        teamParticipationToSave.setBranch("main");
        ProgrammingExerciseStudentParticipation programmingParticipation = studentParticipationRepository.save(teamParticipationToSave);
        ProgrammingSubmission programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setCommitHash("abc123");
        addSubmissionWithResult(programmingParticipation, programmingSubmission, AssessmentType.AUTOMATIC);

        for (Exercise exercise : List.of(textExercise, programmingExercise)) {
            JsonNode response = assertDetailsMatchEntityWire(exercise.getId());
            assertThat(response.at("/exercise/studentAssignedTeamId").isNumber()).as("team id of %s", exercise.getType()).isTrue();
            assertThat(response.at("/exercise/studentParticipations/0/team/students/0/login").asString()).isEqualTo(STUDENT);
            assertThat(response.at("/exercise/studentParticipations/0/team/owner/login").asString()).isEqualTo(TUTOR);
        }
    }

    @Test
    @WithMockUser(username = STUDENT, roles = "USER")
    void details_programmingExercise_forStudentAndTutor_matchesTheEntityWire() throws Exception {
        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        ProgrammingExercise exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise.setTestRepositoryUri("http://localhost:8080/git/TST/tst-tests.git");
        exercise.setReleaseDate(ZonedDateTime.now().minusDays(1));
        exercise = exerciseRepository.save(exercise);
        SubmissionPenaltyPolicy policy = new SubmissionPenaltyPolicy();
        policy.setActive(true);
        policy.setSubmissionLimit(5);
        policy.setExceedingPenalty(1.5);
        programmingExerciseUtilService.addSubmissionPolicyToExercise(policy, exercise);

        ProgrammingExerciseStudentParticipation participation = ParticipationFactory.generateIndividualProgrammingExerciseStudentParticipation(exercise,
                userUtilService.getUserByLogin(STUDENT));
        participation.setRepositoryUri("http://user@localhost:8080/git/TST/tst-" + STUDENT + ".git");
        participation.setIndividualDueDate(ZonedDateTime.now().plusDays(3));
        participation.setPresentationScore(2.0);
        participation = studentParticipationRepository.save(participation);
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setCommitHash("def456");
        submission.setBuildFailed(true);
        addSubmissionWithResult(participation, submission, AssessmentType.AUTOMATIC);

        JsonNode studentResponse = assertDetailsMatchEntityWire(exercise.getId());
        assertThat(studentResponse.at("/exercise/submissionPolicy/type").asString()).isEqualTo("submission_penalty");
        assertThat(studentResponse.at("/exercise/studentParticipations/0/type").asString()).isEqualTo("programming");
        assertThat(studentResponse.at("/exercise/studentParticipations/0").has("userIndependentRepositoryUri")).isTrue();
        assertThat(studentResponse.at("/exercise").has("testRepositoryUri")).as("students never see the test repository").isFalse();

        userUtilService.changeUser(TUTOR);
        JsonNode tutorResponse = assertDetailsMatchEntityWire(exercise.getId());
        assertThat(tutorResponse.at("/exercise/testRepositoryUri").asString()).as("tutors keep the test repository").isEqualTo("http://localhost:8080/git/TST/tst-tests.git");
    }

    @Test
    @WithMockUser(username = STUDENT, roles = "USER")
    void details_batchedQuizWithQuestions_forStudentAndTutor_matchesTheEntityWire() throws Exception {
        Course course = courseUtilService.createCourse();
        userUtilService.enrollPrefixedUsersInCourse(course, TEST_PREFIX);
        QuizExercise quiz = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, ZonedDateTime.now().minusHours(2), null, null, QuizMode.BATCHED);
        QuizBatch batch = QuizExerciseFactory.generateQuizBatch(quiz, ZonedDateTime.now().minusHours(1));
        batch.setPassword("12345678");
        batch = quizBatchRepository.save(batch);
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(quiz, STUDENT);
        QuizSubmission submission = new QuizSubmission();
        submission.setScoreInPoints(2.0);
        submission.setQuizBatch(batch.getId());
        addSubmissionWithResult(participation, submission, AssessmentType.AUTOMATIC);

        JsonNode studentResponse = assertDetailsMatchEntityWire(quiz.getId());
        assertThat(studentResponse.at("/exercise/quizBatches/0/password").asString()).isEqualTo("12345678");
        assertThat(studentResponse.at("/exercise/studentParticipations/0/submissions/0/quizBatch").asLong()).isEqualTo(batch.getId());

        userUtilService.changeUser(TUTOR);
        assertDetailsMatchEntityWire(quiz.getId());
    }

    @Test
    @WithMockUser(username = INSTRUCTOR, roles = "INSTRUCTOR")
    void submissions_everyExerciseType_matchTheEntityWire() throws Exception {
        Course course = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, true);
        addRichSubmissions(course);
        for (Exercise exercise : course.getExercises()) {
            for (StudentParticipation participation : exercise.getStudentParticipations()) {
                JsonNode response = assertSubmissionsMatchEntityWire(participation.getId());
                assertThat(response.at("/0/participation/exercise/type").asString()).isEqualTo(exercise.getType());
                assertThat(StreamSupport.stream(response.spliterator(), false).anyMatch(submission -> submission.at("/results/0/assessor/login").asString("").equals(TUTOR)))
                        .as("the assessor of a %s result", exercise.getType()).isTrue();
            }
        }
    }

    @Test
    @WithMockUser(username = INSTRUCTOR, roles = "INSTRUCTOR")
    void submissions_teamAndProgrammingParticipations_matchTheEntityWire() throws Exception {
        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        ProgrammingExercise exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise.setMode(ExerciseMode.TEAM);
        exercise = exerciseRepository.save(exercise);
        Team team = teamUtilService.createTeam(Set.of(userUtilService.getUserByLogin(STUDENT)), userUtilService.getUserByLogin(TUTOR), exercise, TEST_PREFIX + "substeam");
        team.setImage("data:image/png;base64,iVBORw0KGgo=");
        team = teamRepository.save(team);
        ProgrammingExerciseStudentParticipation participation = ParticipationFactory.generateTeamProgrammingExerciseStudentParticipation(exercise, team);
        participation.setRepositoryUri("http://localhost:8080/git/TST/tst-team.git");
        participation.setBranch("main");
        participation = studentParticipationRepository.save(participation);
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setCommitHash("0123456789abcdef");
        addSubmissionWithResult(participation, submission, AssessmentType.SEMI_AUTOMATIC);
        addSubmissionWithResult(participation, new ProgrammingSubmission(), AssessmentType.AUTOMATIC);

        JsonNode response = assertSubmissionsMatchEntityWire(participation.getId());
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.at("/0/participation/type").asString()).isEqualTo("programming");
        assertThat(response.at("/0/participation/team/shortName").asString()).isEqualTo(TEST_PREFIX + "substeam");
    }

    @Test
    @WithMockUser(username = INSTRUCTOR, roles = "INSTRUCTOR")
    void submissions_examExerciseParticipation_matchesTheEntityWire() throws Exception {
        TextExercise examExercise = examUtilService.addEnrolledCourseExamExerciseGroupWithOneTextExercise(TEST_PREFIX);
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(examExercise, STUDENT);
        TextSubmission submission = new TextSubmission();
        submission.setText("exam text");
        addSubmissionWithResult(participation, submission, AssessmentType.SEMI_AUTOMATIC);

        JsonNode response = assertSubmissionsMatchEntityWire(participation.getId());
        assertThat(response.at("/0/participation/exercise/type").asString()).isEqualTo("text");
    }

    @Test
    @WithMockUser(username = STUDENT, roles = "USER")
    void details_doesNotQueryPerParticipationOrResult() throws Exception {
        Course course = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, true);
        addRichSubmissions(course);
        addRichSubmissions(course);
        TextExercise textExercise = ExerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        assertThatDb(() -> request.get("/api/exercise/exercises/" + textExercise.getId() + "/details", HttpStatus.OK, String.class)).hasBeenCalledAtMostTimes(11);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR, roles = "INSTRUCTOR")
    void submissions_doNotQueryPerSubmissionOrResult() throws Exception {
        Course course = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, true);
        addRichSubmissions(course);
        addRichSubmissions(course);
        StudentParticipation participation = ExerciseUtilService.getFirstExerciseWithType(course, TextExercise.class).getStudentParticipations().iterator().next();
        assertThatDb(() -> request.get("/api/exercise/participations/" + participation.getId() + "/submissions", HttpStatus.OK, String.class)).hasBeenCalledAtMostTimes(5);
    }

    // ---------------------------------------------------------------- fixtures

    /**
     * Gives every participation of the course a second submission that sets the fields specific to its type, with one
     * completed result by the tutor and one result still in progress, so both the result filter and the assessor
     * filter have something to remove.
     */
    private void addRichSubmissions(Course course) {
        for (Exercise exercise : course.getExercises()) {
            StudentParticipation participation = exercise.getStudentParticipations().iterator().next();
            Submission submission = switch (exercise) {
                case TextExercise ignored -> {
                    TextSubmission text = new TextSubmission();
                    text.setText("second text");
                    text.setLanguage(Language.GERMAN);
                    yield text;
                }
                case ProgrammingExercise ignored -> {
                    ProgrammingSubmission programming = new ProgrammingSubmission();
                    programming.setCommitHash("fedcba9876543210");
                    programming.setBuildFailed(true);
                    yield programming;
                }
                case QuizExercise ignored -> {
                    QuizSubmission quiz = new QuizSubmission();
                    quiz.setScoreInPoints(1.5);
                    yield quiz;
                }
                case FileUploadExercise ignored -> {
                    FileUploadSubmission fileUpload = new FileUploadSubmission();
                    fileUpload.setFilePath("/api/core/files/file-upload-exercises/1/submissions/2/file.png");
                    yield fileUpload;
                }
                default -> {
                    ModelingSubmission modeling = new ModelingSubmission();
                    modeling.setModel("{\"elements\":[]}");
                    modeling.setExplanationText("why");
                    yield modeling;
                }
            };
            Result completed = addSubmissionWithResult(participation, submission, AssessmentType.SEMI_AUTOMATIC);
            completed.setAssessor(userUtilService.getUserByLogin(TUTOR));
            completed.setHasComplaint(false);
            resultRepository.save(completed);
            Result inProgress = new Result();
            inProgress.setSubmission(submission);
            inProgress.setExerciseId(exercise.getId());
            inProgress.setAssessmentType(AssessmentType.MANUAL);
            inProgress.setAssessor(userUtilService.getUserByLogin(TUTOR));
            resultRepository.save(inProgress);
        }
    }

    private Result addSubmissionWithResult(StudentParticipation participation, Submission submission, AssessmentType assessmentType) {
        submission.setSubmitted(true);
        submission.setType(SubmissionType.MANUAL);
        submission.setSubmissionDate(ZonedDateTime.now().minusMinutes(30));
        submission.setParticipation(participation);
        submission = submissionRepository.save(submission);
        Result result = new Result();
        result.setSubmission(submission);
        result.setExerciseId(participation.getExercise().getId());
        result.setAssessmentType(assessmentType);
        result.setCompletionDate(ZonedDateTime.now().minusMinutes(20));
        result.setRated(true);
        result.setSuccessful(true);
        result.setScore(80.0);
        result.setCorrectionRound(1);
        return resultRepository.save(result);
    }

    private PlagiarismCase addNotifiedPlagiarismCase(Exercise exercise) {
        PlagiarismCase plagiarismCase = new PlagiarismCase();
        plagiarismCase.setExercise(exercise);
        plagiarismCase.setStudent(userUtilService.getUserByLogin(STUDENT));
        plagiarismCase.setVerdict(PlagiarismVerdict.WARNING);
        plagiarismCase.setCreatedByContinuousPlagiarismControl(true);
        plagiarismCase = plagiarismCaseRepository.save(plagiarismCase);
        Post post = new Post();
        post.setAuthor(userUtilService.getUserByLogin(INSTRUCTOR));
        post.setTitle("Plagiarism case");
        post.setContent("Plagiarism case");
        post.setVisibleForStudents(true);
        post.setPlagiarismCase(plagiarismCase);
        post = postRepository.save(post);
        plagiarismCase.setPost(post);
        return plagiarismCaseRepository.save(plagiarismCase);
    }

    // ---------------------------------------------------------------- wire comparison

    private JsonNode getJson(String url) throws Exception {
        return objectMapper.readTree(request.get(url, HttpStatus.OK, String.class));
    }

    /**
     * Requests the details of an exercise as the current user and compares the response with the entity wire.
     */
    private JsonNode assertDetailsMatchEntityWire(long exerciseId) throws Exception {
        // the request comes first: for a synchronized quiz it creates the batch the entity side then reads
        JsonNode response = getJson("/api/exercise/exercises/" + exerciseId + "/details");
        assertMatches(response, entityDetailsWire(exerciseId));
        return response;
    }

    private JsonNode assertSubmissionsMatchEntityWire(long participationId) throws Exception {
        JsonNode response = getJson("/api/exercise/participations/" + participationId + "/submissions");
        JsonNode entityWire = objectMapper.valueToTree(submissionRepository.findAllWithResultsAndAssessorByParticipationId(participationId));
        assertMatches(response, entityWire);
        return response;
    }

    /**
     * The details response as the route built it before the migration: the same loads and the same filters, applied to
     * the entity graph, which the application mapper then serialized inside {@code ExerciseDetailsDTO}.
     */
    private JsonNode entityDetailsWire(long exerciseId) {
        User user = userTestRepository.getUserWithAuthorities();
        Exercise exercise = exerciseService.findOneWithDetailsForStudents(exerciseId, user);
        courseAthenaConfigRepository.attachToCourseOf(exercise);
        boolean isAtLeastTeachingAssistant = authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user);
        List<StudentParticipation> participations = participationService.findByExerciseAndStudentIdWithSubmissionsAndResults(exercise, user.getId());
        exercise.setStudentParticipations(new HashSet<>());
        for (StudentParticipation participation : participations) {
            exercise.filterResultsForStudents(participation);
            participation.getSubmissions().stream().flatMap(submission -> submission.getResults().stream().filter(Objects::nonNull)).forEach(Result::filterSensitiveInformation);
            exercise.addParticipation(participation);
        }
        if (exercise instanceof QuizExercise quizExercise) {
            quizExercise.setQuizBatches(quizBatchService.getQuizBatchForStudentByLogin(quizExercise, user.getLogin()).stream().collect(Collectors.toSet()));
        }
        if (!isAtLeastTeachingAssistant) {
            exercise.filterSensitiveInformation();
        }
        ObjectNode wire = objectMapper.createObjectNode();
        wire.set("exercise", objectMapper.valueToTree(exercise));
        plagiarismCaseApi.getPlagiarismCaseInfoForExerciseAndUser(exercise.getId(), user.getId()).ifPresent(info -> wire.set("plagiarismCaseInfo", objectMapper.valueToTree(info)));
        return wire;
    }

    private static void assertMatches(JsonNode response, JsonNode entityWire) {
        List<String> differences = new ArrayList<>();
        collectDifferences("$", entityWire, response, differences);
        assertThat(differences).as("differences between the response and the entity wire").isEmpty();
    }

    /**
     * Walks both documents and records every path at which they differ. Arrays are compared after sorting their elements
     * by canonical text, because the entity side serialized sets whose order is not defined.
     */
    private static void collectDifferences(String path, JsonNode expected, JsonNode actual, List<String> differences) {
        if (expected.isObject() && actual.isObject()) {
            Set<String> names = new TreeSet<>(expected.propertyNames());
            names.addAll(actual.propertyNames());
            for (String name : names) {
                if (!actual.has(name) && isIntentionalDifference(name, expected.get(name))) {
                    continue;
                }
                if (!actual.has(name)) {
                    differences.add(path + "." + name + " is missing, the entity wire had " + expected.get(name));
                }
                else if (!expected.has(name)) {
                    differences.add(path + "." + name + " is new, the entity wire did not have it: " + actual.get(name));
                }
                else {
                    collectDifferences(path + "." + name, expected.get(name), actual.get(name), differences);
                }
            }
        }
        else if (expected.isArray() && actual.isArray()) {
            if (expected.size() != actual.size()) {
                differences.add(path + " has " + actual.size() + " elements, the entity wire had " + expected.size());
                return;
            }
            List<JsonNode> sortedExpected = sortedElements(expected);
            List<JsonNode> sortedActual = sortedElements(actual);
            for (int i = 0; i < sortedExpected.size(); i++) {
                collectDifferences(path + "[" + i + "]", sortedExpected.get(i), sortedActual.get(i), differences);
            }
        }
        else if (expected.isNumber() && actual.isNumber()) {
            if (expected.isIntegralNumber() != actual.isIntegralNumber() || expected.decimalValue().compareTo(actual.decimalValue()) != 0) {
                differences.add(path + " is " + actual + ", the entity wire had " + expected);
            }
        }
        else if (!expected.equals(actual)) {
            differences.add(path + " is " + actual + ", the entity wire had " + expected);
        }
    }

    /**
     * The one place where the response may differ from the entity wire.
     * <p>
     * {@code userIndependentRepositoryUri}: the entity getter carried a bare {@code @JsonInclude}, so a programming
     * participation without a repository wrote the key with {@code null}. The records leave a {@code null} key off, as
     * {@code ProgrammingParticipationLatestResultDTO} already does on the SCORPIO latest-result route, because the
     * architecture rules allow no other inclusion. The only client reader falls back with {@code ?? ''}, which treats a
     * missing key and {@code null} alike. A non-null value is still compared.
     */
    private static boolean isIntentionalDifference(String name, JsonNode expectedValue) {
        return "userIndependentRepositoryUri".equals(name) && expectedValue.isNull();
    }

    private static List<JsonNode> sortedElements(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).sorted(Comparator.comparing(ExerciseDetailsWireContractIntegrationTest::canonical)).toList();
    }

    private static String canonical(JsonNode node) {
        if (node.isObject()) {
            return node.properties().stream().sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + "=" + canonical(entry.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (node.isArray()) {
            return StreamSupport.stream(node.spliterator(), false).map(ExerciseDetailsWireContractIntegrationTest::canonical).sorted().collect(Collectors.joining(",", "[", "]"));
        }
        if (node.isNumber()) {
            return node.decimalValue().stripTrailingZeros().toPlainString();
        }
        return node.toString();
    }
}
