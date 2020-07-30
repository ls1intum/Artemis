package de.tum.in.www1.artemis;

import static de.tum.in.www1.artemis.util.TestConstants.COMMIT_HASH_OBJECT_ID;
import static de.tum.in.www1.artemis.util.TestConstants.COMMIT_HASH_STRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.LocalRepository;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class StudentExamIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    ExamRepository examRepository;

    @Autowired
    StudentExamRepository studentExamRepository;

    @Autowired
    ExamSessionRepository examSessionRepository;

    @Autowired
    ProgrammingSubmissionRepository programmingSubmissionRepository;

    @Autowired
    ExerciseRepository exerciseRepository;

    @Autowired
    SubmissionVersionRepository submissionVersionRepository;

    @Autowired
    ObjectMapper objectMapper;

    private List<User> users;

    private Course course1;

    private Course course2;

    private Exam exam1;

    private Exam exam2;

    private StudentExam studentExam1;

    private LocalRepository exerciseRepo = new LocalRepository();

    private LocalRepository testRepo = new LocalRepository();

    private LocalRepository solutionRepo = new LocalRepository();

    private List<LocalRepository> studentRepos = new ArrayList<>();

    @BeforeEach
    public void initTestCase() throws Exception {
        users = database.addUsers(10, 1, 1);
        users.remove(database.getUserByLogin("admin")); // the admin is not registered for the course and therefore cannot access the student exam so we need to remove it
        course1 = database.addEmptyCourse();
        exam1 = database.addActiveExamWithRegisteredUser(course1, users.get(0));
        Exam exam2 = database.addExam(course1);
        studentExam1 = database.addStudentExam(exam1);
        studentExam1.setWorkingTime(7200);
        studentExam1.setUser(users.get(0));
        studentExamRepository.save(studentExam1);
        database.addStudentExam(exam2);
    }

    @AfterEach
    public void resetDatabase() throws Exception {

        // change back to instructor user
        database.changeUser("instructor1");
        // Clean up to prevent exceptions during reset database
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.OK);

        database.resetDatabase();

        bitbucketRequestMockProvider.reset();
        bambooRequestMockProvider.reset();

        exerciseRepo.resetLocalRepo();
        testRepo.resetLocalRepo();
        solutionRepo.resetLocalRepo();
        for (var repo : studentRepos) {
            repo.resetLocalRepo();
        }
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    private void testAllPreAuthorize() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams/" + studentExam1.getId(), HttpStatus.FORBIDDEN, StudentExam.class);
        request.getList("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams", HttpStatus.FORBIDDEN, StudentExam.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetStudentExam_asInstructor() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams/" + studentExam1.getId(), HttpStatus.OK, StudentExam.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetStudentExamsForExam_asInstructor() throws Exception {
        List<StudentExam> studentExams = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExams.size()).isEqualTo(2);
    }

    private List<StudentExam> prepareStudentExamsForConduction() throws Exception {

        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(1);
        var examEndDate = ZonedDateTime.now().plusMinutes(3);
        // --> 2 min = 120s working time

        bambooRequestMockProvider.enableMockingOfRequests(true);
        bitbucketRequestMockProvider.enableMockingOfRequests(true);

        course2 = database.addEmptyCourse();
        exam2 = database.addExam(course2, examVisibleDate, examStartDate, examEndDate);
        exam2 = database.addExerciseGroupsAndExercisesToExam(exam2, examStartDate, examEndDate, true);

        // register user
        var student1 = database.getUserByLogin("student1");
        exam2.setRegisteredUsers(new HashSet<>(users));
        exam2.setRandomizeExerciseOrder(false);
        exam2 = examRepository.save(exam2);

        // generate individual student exams
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/generate-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);
        assertThat(studentExams).hasSize(exam2.getRegisteredUsers().size());
        assertThat(studentExamRepository.findAll()).hasSize(users.size() + 3); // we generate three additional student exams in the @Before method

        // start exercises
        exerciseRepo.configureRepos("exerciseLocalRepo", "exerciseOriginRepo");
        testRepo.configureRepos("testLocalRepo", "testOriginRepo");
        solutionRepo.configureRepos("solutionLocalRepo", "solutionOriginRepo");

        List<ProgrammingExercise> programmingExercises = new ArrayList<>();
        for (var exercise : exam2.getExerciseGroups().get(4).getExercises()) {
            var programmingExercise = (ProgrammingExercise) exercise;
            programmingExercises.add(programmingExercise);

            setupRepositoryMocks(programmingExercise, exerciseRepo, solutionRepo, testRepo);
            for (var user : exam2.getRegisteredUsers()) {
                var repo = new LocalRepository();
                repo.configureRepos("studentRepo", "studentOriginRepo");
                setupRepositoryMocksParticipant(programmingExercise, user.getLogin(), repo);
                studentRepos.add(repo);
            }
        }

        for (var programmingExercise : programmingExercises) {
            for (var user : users) {
                mockConnectorRequestsForStartParticipation(programmingExercise, user.getParticipantIdentifier(), Set.of(user));
            }
        }

        Integer noGeneratedParticipations = request.postWithResponseBody("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/start-exercises",
                Optional.empty(), Integer.class, HttpStatus.OK);

        assertThat(noGeneratedParticipations).isEqualTo(users.size() * exam2.getExerciseGroups().size());

        // simulate "wait" for exam to start
        exam2.setStartDate(ZonedDateTime.now());
        exam2.setEndDate(ZonedDateTime.now().plusMinutes(2));
        examRepository.save(exam2);

        return studentExams;
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetStudentExamForConduction() throws Exception {

        List<StudentExam> studentExams = prepareStudentExamsForConduction();
        // TODO: also write a 2nd test where the submission already contains some content

        for (var studentExam : studentExams) {
            var user = studentExam.getUser();
            database.changeUser(user.getLogin());
            final HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "foo");
            headers.set("X-Artemis-Client-Fingerprint", "bar");
            headers.set("X-Forwarded-For", "10.0." + studentExam.getId() + ".1");
            var response = request.get("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/studentExams/conduction", HttpStatus.OK, StudentExam.class, headers);
            assertThat(response).isEqualTo(studentExam);
            assertThat(response.isStarted()).isTrue();
            assertThat(response.getExercises().size()).isEqualTo(exam2.getNumberOfExercisesInExam());
            var textExercise = (TextExercise) response.getExercises().get(0);
            var quizExercise = (QuizExercise) response.getExercises().get(1);
            assertThat(textExercise.getStudentParticipations().size()).isEqualTo(1);
            var participation1 = textExercise.getStudentParticipations().iterator().next();
            assertThat(participation1.getParticipant()).isEqualTo(user);
            assertThat(participation1.getSubmissions()).hasSize(1);
            assertThat(quizExercise.getStudentParticipations().size()).isEqualTo(1);
            var participation2 = quizExercise.getStudentParticipations().iterator().next();
            assertThat(participation2.getParticipant()).isEqualTo(user);
            assertThat(participation2.getSubmissions()).hasSize(1);

            // Ensure that student exam was marked as started
            assertThat(studentExamRepository.findById(studentExam.getId()).get().isStarted()).isTrue();

            // Check that sensitive information has been removed
            assertThat(textExercise.getGradingCriteria()).isEmpty();
            assertThat(textExercise.getGradingInstructions()).isEqualTo(null);
            assertThat(textExercise.getSampleSolution()).isEqualTo(null);

            // Check that sensitive information has been removed
            assertThat(quizExercise.getGradingCriteria()).isEmpty();
            assertThat(quizExercise.getGradingInstructions()).isEqualTo(null);
            assertThat(quizExercise.getQuizQuestions().size()).isEqualTo(3);

            for (QuizQuestion question : quizExercise.getQuizQuestions()) {
                if (question instanceof MultipleChoiceQuestion) {
                    assertThat(((MultipleChoiceQuestion) question).getAnswerOptions()).hasSize(2);
                    for (AnswerOption answerOption : ((MultipleChoiceQuestion) question).getAnswerOptions()) {
                        assertThat(answerOption.getExplanation()).isNull();
                        assertThat(answerOption.isIsCorrect()).isNull();
                    }
                }
                else if (question instanceof DragAndDropQuestion) {
                    assertThat(((DragAndDropQuestion) question).getCorrectMappings()).hasSize(0);
                }
                else if (question instanceof ShortAnswerQuestion) {
                    assertThat(((ShortAnswerQuestion) question).getCorrectMappings()).hasSize(0);
                }
            }

            assertThat(response.getExamSessions()).hasSize(1);
            var examSession = response.getExamSessions().iterator().next();
            final var optionalExamSession = examSessionRepository.findById(examSession.getId());
            assertThat(optionalExamSession).isPresent();

            assertThat(examSession.getSessionToken()).isNotNull();
            assertThat(examSession.getUserAgent()).isNull();
            assertThat(examSession.getBrowserFingerprintHash()).isNull();
            assertThat(examSession.getIpAddress()).isNull();
            assertThat(optionalExamSession.get().getUserAgent()).isEqualTo("foo");
            assertThat(optionalExamSession.get().getBrowserFingerprintHash()).isEqualTo("bar");
            assertThat(optionalExamSession.get().getIpAddress().toNormalizedString()).isEqualTo("10.0." + studentExam.getId() + ".1");
        }

        // change back to instructor user
        database.changeUser("instructor1");
        // Make sure delete also works if so many objects have been created before
        request.delete("/api/courses/" + course2.getId() + "/exams/" + exam2.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetWorkingTimesNoStudentExams() throws Exception {
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(5);
        var examEndDate = ZonedDateTime.now().plusMinutes(20);

        Course course = database.addEmptyCourse();
        Exam exam = database.addExam(course, examVisibleDate, examStartDate, examEndDate);
        exam = database.addExerciseGroupsAndExercisesToExam(exam, examStartDate, examEndDate, true);

        // register user
        exam.setRegisteredUsers(new HashSet<>(users));
        exam.setNumberOfExercisesInExam(2);
        exam.setRandomizeExerciseOrder(false);
        exam = examRepository.save(exam);

        /*
         * don't generate individual student exams
         */

        assertThat(studentExamRepository.findMaxWorkingTimeByExamId(exam.getId())).isEmpty();
        assertThat(studentExamRepository.findAllDistinctWorkingTimesByExamId(exam.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetWorkingTimesDifferentStudentExams() throws Exception {
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(5);
        var examEndDate = ZonedDateTime.now().plusMinutes(20);

        Course course = database.addEmptyCourse();
        Exam exam = database.addExam(course, examVisibleDate, examStartDate, examEndDate);
        exam = database.addExerciseGroupsAndExercisesToExam(exam, examStartDate, examEndDate, true);

        // register user
        exam.setRegisteredUsers(new HashSet<>(users));
        exam.setRandomizeExerciseOrder(false);
        exam = examRepository.save(exam);

        // generate individual student exams
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(),
                StudentExam.class, HttpStatus.OK);

        // Modify working times

        var expectedWorkingTimes = new HashSet<Integer>();
        int maxWorkingTime = (int) Duration.between(examStartDate, examEndDate).getSeconds();

        for (int i = 0; i < studentExams.size(); i++) {
            if (i % 2 == 0)
                maxWorkingTime += 35;
            expectedWorkingTimes.add(maxWorkingTime);

            var studentExam = studentExams.get(i);
            studentExam.setWorkingTime(maxWorkingTime);
            studentExamRepository.save(studentExam);
        }

        SecurityUtils.setAuthorizationObject(); // TODO why do we get an exception here without that?
        assertThat(studentExamRepository.findMaxWorkingTimeByExamId(exam.getId())).contains(maxWorkingTime);
        assertThat(studentExamRepository.findAllDistinctWorkingTimesByExamId(exam.getId())).containsExactlyInAnyOrderElementsOf(expectedWorkingTimes);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateWorkingTime() throws Exception {
        int newWorkingTime = 180 * 60;
        exam1.setVisibleDate(ZonedDateTime.now().plusMinutes(5));
        exam1 = examRepository.save(exam1);
        StudentExam result = request.patchWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams/" + studentExam1.getId() + "/workingTime",
                newWorkingTime, StudentExam.class, HttpStatus.OK);
        assertThat(result.getWorkingTime()).isEqualTo(newWorkingTime);
        assertThat(studentExamRepository.findById(studentExam1.getId()).get().getWorkingTime()).isEqualTo(newWorkingTime);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateWorkingTimeInvalid() throws Exception {
        int newWorkingTime = 0;
        exam1.setVisibleDate(ZonedDateTime.now().plusMinutes(5));
        exam1 = examRepository.save(exam1);
        request.patchWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams/" + studentExam1.getId() + "/workingTime", newWorkingTime,
                StudentExam.class, HttpStatus.BAD_REQUEST);
        // working time did not change
        var studentExamDB = studentExamRepository.findById(studentExam1.getId()).get();
        assertThat(studentExamDB.getWorkingTime()).isEqualTo(studentExam1.getWorkingTime());

        newWorkingTime = -10;
        request.patchWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams/" + studentExam1.getId() + "/workingTime", newWorkingTime,
                StudentExam.class, HttpStatus.BAD_REQUEST);
        // working time did not change
        studentExamDB = studentExamRepository.findById(studentExam1.getId()).get();
        assertThat(studentExamDB.getWorkingTime()).isEqualTo(studentExam1.getWorkingTime());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateWorkingTimeLate() throws Exception {
        int newWorkingTime = 180 * 60;
        exam1.setVisibleDate(ZonedDateTime.now());
        exam1 = examRepository.save(exam1);
        request.patchWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams/" + studentExam1.getId() + "/workingTime", newWorkingTime,
                StudentExam.class, HttpStatus.BAD_REQUEST);
        // working time did not change
        var studentExamDB = studentExamRepository.findById(studentExam1.getId()).get();
        assertThat(studentExamDB.getWorkingTime()).isEqualTo(studentExam1.getWorkingTime());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testSubmitStudentExam_alreadySubmitted() throws Exception {
        studentExam1.setSubmitted(true);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams/submit", studentExam1, HttpStatus.CONFLICT);
        studentExamRepository.save(studentExam1);
        studentExam1.setSubmitted(false);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams/submit", studentExam1, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testSubmitStudentExam_notInTime() throws Exception {
        studentExam1.setSubmitted(false);
        studentExamRepository.save(studentExam1);
        // Forbidden because user tried to submit before start
        exam1.setStartDate(ZonedDateTime.now().plusHours(1));
        examRepository.save(exam1);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams/submit", studentExam1, HttpStatus.FORBIDDEN);
        // Forbidden because user tried to submit after end
        exam1.setStartDate(ZonedDateTime.now().minusHours(5));
        examRepository.save(exam1);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams/submit", studentExam1, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testSubmitStudentExam() throws Exception {
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams/submit", studentExam1, HttpStatus.OK, null);
        StudentExam submittedStudentExam = studentExamRepository.findById(studentExam1.getId()).get();
        // Ensure that student exam has been marked as submitted
        assertThat(submittedStudentExam.isSubmitted()).isTrue();
        // Ensure that student exam has been set
        assertThat(submittedStudentExam.getSubmissionDate()).isNotNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSubmitExamOtherUser_forbidden() throws Exception {
        List<StudentExam> studentExams = prepareStudentExamsForConduction();
        database.changeUser(studentExams.get(0).getUser().getLogin());
        var studentExamResponse = request.get("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/studentExams/conduction", HttpStatus.OK, StudentExam.class);
        studentExamResponse.setExercises(null);
        database.changeUser("student2");
        request.post("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/studentExams/submit", studentExamResponse, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSubmitExamWithSubmissionResult_forbidden() throws Exception {
        List<StudentExam> studentExams = prepareStudentExamsForConduction();
        database.changeUser(studentExams.get(0).getUser().getLogin());
        var studentExamResponse = request.get("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/studentExams/conduction", HttpStatus.OK, StudentExam.class);
        for (var exercise : studentExamResponse.getExercises()) {
            var participation = exercise.getStudentParticipations().iterator().next();
            Submission submission = null;
            if (exercise instanceof ProgrammingExercise) {
                submission = new ProgrammingSubmission();
            }
            else if (exercise instanceof TextExercise) {
                submission = new TextSubmission();
            }
            else if (exercise instanceof ModelingExercise) {
                submission = new ModelingSubmission();
            }
            else if (exercise instanceof QuizExercise) {
                submission = new QuizSubmission();
            }
            if (submission != null) {
                submission.setResult(new Result());
                Set<Submission> submissions = new HashSet<>();
                submissions.add(submission);
                participation.setSubmissions(submissions);
            }

        }
        request.post("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/studentExams/submit", studentExamResponse, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSubmitStudentExam_early() throws Exception {
        List<StudentExam> studentExams = prepareStudentExamsForConduction();

        // we have to reset the mock provider and enable it again so that we can mock additional requests below
        bitbucketRequestMockProvider.reset();
        bitbucketRequestMockProvider.enableMockingOfRequests(true);

        database.changeUser(studentExams.get(0).getUser().getLogin());
        var studentExamResponse = request.get("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/studentExams/conduction", HttpStatus.OK, StudentExam.class);
        final List<ProgrammingExercise> exercisesToBeLocked = new ArrayList<>();
        final List<ProgrammingExerciseStudentParticipation> studentProgrammingParticipations = new ArrayList<>();

        for (var exercise : studentExamResponse.getExercises()) {
            var participation = exercise.getStudentParticipations().iterator().next();
            if (exercise instanceof ProgrammingExercise) {
                studentProgrammingParticipations.add((ProgrammingExerciseStudentParticipation) participation);
                var programmingExercise = (ProgrammingExercise) exercise;
                exercisesToBeLocked.add(programmingExercise);
                final var repositorySlug = (programmingExercise.getProjectKey() + "-" + participation.getParticipantIdentifier()).toLowerCase();
                bitbucketRequestMockProvider.mockSetRepositoryPermissionsToReadOnly(repositorySlug, programmingExercise.getProjectKey(), participation.getStudents());
            }
        }

        // submit early
        var submittedStudentExam = request.postWithResponseBody("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/studentExams/submit", studentExamResponse,
                StudentExam.class, HttpStatus.OK);
        assertThat(submittedStudentExam.isSubmitted()).isTrue();
        assertThat(submittedStudentExam.getSubmissionDate()).isNotNull();

        // assert that the user cannot submit again
        request.post("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/studentExams/submit", studentExamResponse, HttpStatus.CONFLICT);

        // assert that all repositories of programming exercises have been locked
        assert exercisesToBeLocked.size() == studentProgrammingParticipations.size();
        for (int i = 0; i < exercisesToBeLocked.size(); i++) {
            verify(programmingExerciseParticipationServiceSpy, atLeastOnce()).lockStudentRepository(exercisesToBeLocked.get(i), studentProgrammingParticipations.get(i));
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSubmitStudentExam_realistic() throws Exception {

        List<StudentExam> studentExams = prepareStudentExamsForConduction();

        List<StudentExam> studentExamsAfterStart = new ArrayList<>();
        for (var studentExam : studentExams) {
            database.changeUser(studentExam.getUser().getLogin());
            var studentExamResponse = request.get("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/studentExams/conduction", HttpStatus.OK, StudentExam.class);

            for (var exercise : studentExamResponse.getExercises()) {
                var participation = exercise.getStudentParticipations().iterator().next();
                if (exercise instanceof ProgrammingExercise) {
                    doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());
                    bambooRequestMockProvider.reset();
                    bambooRequestMockProvider.enableMockingOfRequests(true);
                    bambooRequestMockProvider.mockTriggerBuild((ProgrammingExerciseParticipation) participation);
                    request.postWithoutLocation("/api/programming-submissions/" + participation.getId() + "/trigger-build", null, HttpStatus.OK, new HttpHeaders());
                    Optional<ProgrammingSubmission> programmingSubmission = programmingSubmissionRepository
                            .findFirstByParticipationIdOrderBySubmissionDateDesc(participation.getId());
                    assertThat(programmingSubmission.isPresent());
                    participation.getSubmissions().add(programmingSubmission.get());
                    continue;
                }
                var submission = participation.getSubmissions().iterator().next();
                if (exercise instanceof ModelingExercise) {
                    // check that the submission was saved and that a submitted version was created
                    String newModel = "This is a new model";
                    var modelingSubmission = (ModelingSubmission) submission;
                    modelingSubmission.setModel(newModel);
                    request.put("/api/exercises/" + exercise.getId() + "/modeling-submissions", modelingSubmission, HttpStatus.OK);
                    var savedModelingSubmission = request.get(
                            "/api/participations/" + exercise.getStudentParticipations().iterator().next().getId() + "/latest-modeling-submission", HttpStatus.OK,
                            ModelingSubmission.class);
                    // check that the submission was saved
                    assertThat(newModel).isEqualTo(savedModelingSubmission.getModel());
                    // check that a submitted version was created
                    assertVersionedSubmission(modelingSubmission);
                }
                else if (exercise instanceof TextExercise) {
                    var textSubmission = (TextSubmission) submission;
                    final var newText = "New Text";
                    textSubmission.setText(newText);
                    request.put("/api/exercises/" + exercise.getId() + "/text-submissions", textSubmission, HttpStatus.OK);

                    var savedTextSubmission = request.get("/api/text-submissions/" + textSubmission.getId(), HttpStatus.OK, TextSubmission.class);
                    // check that the submission was saved
                    assertThat(newText).isEqualTo(savedTextSubmission.getText());
                    // check that a submitted version was created
                    assertVersionedSubmission(textSubmission);
                }
                else if (exercise instanceof QuizExercise) {
                    // check that the submission was saved and that a submitted version was created
                    var quizSubmission = (QuizSubmission) submission;
                    int dragAndDropDragItemIndex = 1;
                    int dragAndDropLocationIndex = 2;
                    String shortAnswerText = "New Short Answer Text";
                    int shortAnswerSpotIndex = 1;
                    int multipleChoiceSelectedOptionIndex = 0;
                    ((QuizExercise) exercise).getQuizQuestions().forEach(quizQuestion -> {
                        if (quizQuestion instanceof DragAndDropQuestion) {
                            DragAndDropSubmittedAnswer dndSubmittedAnswer = new DragAndDropSubmittedAnswer();
                            DragAndDropMapping dndMapping = new DragAndDropMapping();
                            dndMapping.setDragItemIndex(dragAndDropDragItemIndex);
                            dndMapping.setDropLocationIndex(dragAndDropLocationIndex);
                            dndSubmittedAnswer.getMappings().add(dndMapping);
                            quizSubmission.getSubmittedAnswers().add(dndSubmittedAnswer);
                        }
                        else if (quizQuestion instanceof ShortAnswerQuestion) {
                            ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer = new ShortAnswerSubmittedAnswer();
                            ShortAnswerSubmittedText shortAnswerSubmittedText = new ShortAnswerSubmittedText();
                            shortAnswerSubmittedText.setText(shortAnswerText);
                            shortAnswerSubmittedText.setSpot(((ShortAnswerQuestion) quizQuestion).getSpots().get(shortAnswerSpotIndex));
                            shortAnswerSubmittedAnswer.getSubmittedTexts().add(shortAnswerSubmittedText);
                            quizSubmission.getSubmittedAnswers().add(shortAnswerSubmittedAnswer);
                        }
                        else if (quizQuestion instanceof MultipleChoiceQuestion) {
                            var answerOptions = ((MultipleChoiceQuestion) quizQuestion).getAnswerOptions();
                            MultipleChoiceSubmittedAnswer multipleChoiceSubmittedAnswer = new MultipleChoiceSubmittedAnswer();
                            multipleChoiceSubmittedAnswer.addSelectedOptions(answerOptions.get(multipleChoiceSelectedOptionIndex));
                            quizSubmission.getSubmittedAnswers().add(multipleChoiceSubmittedAnswer);
                        }
                    });
                    QuizSubmission savedQuizSubmission = request.putWithResponseBody("/api/exercises/" + exercise.getId() + "/submissions/exam", quizSubmission,
                            QuizSubmission.class, HttpStatus.OK);
                    // check the submission
                    assertThat(savedQuizSubmission.getSubmittedAnswers()).isNotNull();
                    assertThat(savedQuizSubmission.getSubmittedAnswers().size()).isGreaterThan(0);
                    ((QuizExercise) exercise).getQuizQuestions().forEach(quizQuestion -> {
                        SubmittedAnswer submittedAnswer = savedQuizSubmission.getSubmittedAnswerForQuestion(quizQuestion);
                        if (submittedAnswer instanceof MultipleChoiceSubmittedAnswer) {
                            var multipleChoiceSubmittedAnswer = (MultipleChoiceSubmittedAnswer) submittedAnswer;
                            assertThat(multipleChoiceSubmittedAnswer.getSelectedOptions()).isNotNull();
                            assertThat(multipleChoiceSubmittedAnswer.getSelectedOptions().size()).isGreaterThan(0);
                            assertThat(multipleChoiceSubmittedAnswer.getSelectedOptions().iterator().next()).isNotNull();
                            assertThat(multipleChoiceSubmittedAnswer.getSelectedOptions().iterator().next())
                                    .isEqualTo(((MultipleChoiceQuestion) quizQuestion).getAnswerOptions().get(multipleChoiceSelectedOptionIndex));
                        }
                        else if (submittedAnswer instanceof ShortAnswerSubmittedAnswer) {
                            var shortAnswerSubmittedAnswer = (ShortAnswerSubmittedAnswer) submittedAnswer;
                            assertThat(shortAnswerSubmittedAnswer.getSubmittedTexts()).isNotNull();
                            assertThat(shortAnswerSubmittedAnswer.getSubmittedTexts().size()).isGreaterThan(0);
                            assertThat(shortAnswerSubmittedAnswer.getSubmittedTexts().iterator().next()).isNotNull();
                            assertThat(shortAnswerSubmittedAnswer.getSubmittedTexts().iterator().next().getText()).isEqualTo(shortAnswerText);
                            assertThat(shortAnswerSubmittedAnswer.getSubmittedTexts().iterator().next().getSpot())
                                    .isEqualTo(((ShortAnswerQuestion) quizQuestion).getSpots().get(shortAnswerSpotIndex));
                        }
                        else if (submittedAnswer instanceof DragAndDropSubmittedAnswer) {
                            var dragAndDropSubmittedAnswer = (DragAndDropSubmittedAnswer) submittedAnswer;
                            assertThat(dragAndDropSubmittedAnswer.getMappings()).isNotNull();
                            assertThat(dragAndDropSubmittedAnswer.getMappings().size()).isGreaterThan(0);
                            assertThat(dragAndDropSubmittedAnswer.getMappings().iterator().next()).isNotNull();
                            assertThat(dragAndDropSubmittedAnswer.getMappings().iterator().next().getDragItem())
                                    .isEqualTo(((DragAndDropQuestion) quizQuestion).getDragItems().get(dragAndDropDragItemIndex));
                            assertThat(dragAndDropSubmittedAnswer.getMappings().iterator().next().getDropLocation())
                                    .isEqualTo(((DragAndDropQuestion) quizQuestion).getDropLocations().get(dragAndDropLocationIndex));
                        }
                    });
                    assertVersionedSubmission(quizSubmission);
                }
            }

            studentExamsAfterStart.add(studentExamResponse);
        }

        // now we change to the point of time when the student exam needs to be submitted
        // IMPORTANT NOTE: this needs to be configured in a way that the individual student exam ended, but we are still in the grace period time
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(3));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(1));
        exam2 = examRepository.save(exam2);

        // reset to exchange expectation for newCommitHash
        bambooRequestMockProvider.reset();
        bambooRequestMockProvider.enableMockingOfRequests(true);
        final String newCommitHash = "2ec6050142b9c187909abede819c083c8745c19b";
        final ObjectId newCommitHashObjectId = ObjectId.fromString(newCommitHash);

        for (var studentExam : studentExamsAfterStart) {
            for (var exercise : studentExam.getExercises()) {
                var participation = exercise.getStudentParticipations().iterator().next();
                if (exercise instanceof ProgrammingExercise) {
                    // do another programming submission to check if the StudentExam after submit contains the new commit hash
                    doReturn(newCommitHashObjectId).when(gitService).getLastCommitHash(any());
                    bambooRequestMockProvider.reset();
                    bambooRequestMockProvider.enableMockingOfRequests(true);
                    bambooRequestMockProvider.mockTriggerBuild((ProgrammingExerciseParticipation) participation);
                    database.changeUser(studentExam.getUser().getLogin());
                    request.postWithoutLocation("/api/programming-submissions/" + participation.getId() + "/trigger-build", null, HttpStatus.OK, new HttpHeaders());
                    // do not add programming submission to participation, because we want to simulate, that the latest submission is not present
                }
            }
        }

        List<StudentExam> studentExamsAfterFinish = new ArrayList<>();
        for (var studentExamAfterStart : studentExamsAfterStart) {
            database.changeUser(studentExamAfterStart.getUser().getLogin());
            var studentExamFinished = request.postWithResponseBody("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/studentExams/submit", studentExamAfterStart,
                    StudentExam.class, HttpStatus.OK);
            // Check that all text/quiz/modeling submissions were saved and that submitted versions were created
            for (var exercise : studentExamFinished.getExercises()) {
                var participationAfterFinish = exercise.getStudentParticipations().iterator().next();
                var submissionAfterFinish = participationAfterFinish.getSubmissions().iterator().next();

                var exerciseAfterStart = studentExamAfterStart.getExercises().stream().filter(exAfterStart -> exAfterStart.getId().equals(exercise.getId())).findFirst().get();
                var participationAfterStart = exerciseAfterStart.getStudentParticipations().iterator().next();
                var submissionAfterStart = participationAfterStart.getSubmissions().iterator().next();

                if (exercise instanceof ModelingExercise) {
                    var modelingSubmissionAfterFinish = (ModelingSubmission) submissionAfterFinish;
                    var modelingSubmissionAfterStart = (ModelingSubmission) submissionAfterStart;
                    assertThat(modelingSubmissionAfterFinish).isEqualTo(modelingSubmissionAfterStart);
                    assertVersionedSubmission(modelingSubmissionAfterStart);
                    assertVersionedSubmission(modelingSubmissionAfterFinish);
                }
                else if (exercise instanceof TextExercise) {
                    var textSubmissionAfterFinish = (TextSubmission) submissionAfterFinish;
                    var textSubmissionAfterStart = (TextSubmission) submissionAfterStart;
                    assertThat(textSubmissionAfterFinish).isEqualTo(textSubmissionAfterStart);
                    assertVersionedSubmission(textSubmissionAfterStart);
                    assertVersionedSubmission(textSubmissionAfterFinish);
                }
                else if (exercise instanceof QuizExercise) {
                    var quizSubmissionAfterFinish = (QuizSubmission) submissionAfterFinish;
                    var quizSubmissionAfterStart = (QuizSubmission) submissionAfterStart;
                    assertThat(quizSubmissionAfterFinish).isEqualTo(quizSubmissionAfterStart);
                    assertVersionedSubmission(quizSubmissionAfterStart);
                    assertVersionedSubmission(quizSubmissionAfterFinish);
                }
                else if (exercise instanceof ProgrammingExercise) {
                    var programmingSubmissionAfterStart = (ProgrammingSubmission) submissionAfterStart;
                    var programmingSubmissionAfterFinish = (ProgrammingSubmission) submissionAfterFinish;
                    // assert that we did not update the submission prematurely
                    assertThat(programmingSubmissionAfterStart.getCommitHash()).isEqualTo(COMMIT_HASH_STRING);
                    // assert that we get the correct commit hash after submit
                    assertThat(programmingSubmissionAfterFinish.getCommitHash()).isEqualTo(newCommitHash);
                }

            }

            studentExamsAfterFinish.add(studentExamFinished);

            assertThat(studentExamFinished.isSubmitted()).isTrue();
            assertThat(studentExamFinished.getSubmissionDate()).isNotNull();
        }
        // The method lockStudentRepository will only be called if the student hands in early (see separate test)
        verify(programmingExerciseParticipationServiceSpy, never()).lockStudentRepository(any(), any());
        assertThat(studentExamsAfterFinish).hasSize(studentExamsAfterStart.size());
    }

    private void assertVersionedSubmission(Submission submission) {
        SecurityContextHolder.setContext(TestSecurityContextHolder.getContext());
        var versionedSubmission = submissionVersionRepository.findLatestVersion(submission.getId());
        assert versionedSubmission.isPresent();
        if (submission instanceof TextSubmission) {
            assertThat(((TextSubmission) submission).getText()).isEqualTo(versionedSubmission.get().getContent());
        }
        else if (submission instanceof ModelingSubmission) {
            assertThat(((ModelingSubmission) submission).getModel()).isEqualTo(versionedSubmission.get().getContent());
        }
        else if (submission instanceof FileUploadSubmission) {
            assertThat(((FileUploadSubmission) submission).getFilePath()).isEqualTo(versionedSubmission.get().getContent());
        }
        else {
            assert submission instanceof QuizSubmission;
            String submittedAnswersAsString;
            try {
                submittedAnswersAsString = objectMapper.writeValueAsString(((QuizSubmission) submission).getSubmittedAnswers());
            }
            catch (Exception e) {
                submittedAnswersAsString = submission.toString();
            }
            assertThat(submittedAnswersAsString).isEqualTo(versionedSubmission.get().getContent());
            assertThat(submission).isEqualTo(versionedSubmission.get().getSubmission());
        }
    }
}
