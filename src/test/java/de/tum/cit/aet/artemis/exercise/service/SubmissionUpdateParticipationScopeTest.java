package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.StudentParticipationSubmitTargetDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamModelingSubmissionUpdateDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamTextSubmissionUpdateDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionVersionRepository;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.exercise.web.ParticipationTeamWebsocketService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.dto.ModelingSubmissionRequestDTO;
import de.tum.cit.aet.artemis.modeling.test_repository.ModelingSubmissionTestRepository;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.dto.submission.QuizSubmissionFromLiveClientDTO;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizSubmissionTestRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.dto.TextSubmissionRequestDTO;
import de.tum.cit.aet.artemis.text.test_repository.TextSubmissionTestRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * A submission id in a save request is only applied to a submission of the participation the save resolves for the caller.
 * These tests pin that scope for the three save paths that accept a submission id from the client: the team websocket save,
 * the REST save, and the exam quiz save. Each reads the stored row back through a fresh persistence context.
 */
class SubmissionUpdateParticipationScopeTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "submissionupdatescope";

    private static final String EXISTING_TEXT = "EXISTING";

    private static final String UPDATE_TEXT = "UPDATE";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ParticipationTeamWebsocketService participationTeamWebsocketService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ExerciseTestRepository exerciseRepository;

    @Autowired
    private TextSubmissionTestRepository textSubmissionRepository;

    @Autowired
    private ModelingSubmissionTestRepository modelingSubmissionRepository;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private QuizSubmissionTestRepository quizSubmissionRepository;

    @Autowired
    private StudentExamTestRepository studentExamRepository;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private QuizExerciseTestRepository quizExerciseRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private SubmissionVersionRepository submissionVersionRepository;

    private Course course;

    private TextExercise textExercise;

    private StudentParticipation ownTeamParticipation;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        course = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("Text", TEST_PREFIX);
        textExercise = ExerciseUtilService.findTextExerciseWithTitle(course.getExercises(), "Text");
        participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student1");
        ZonedDateTime now = ZonedDateTime.now();
        TextExercise teamTextExercise = textExerciseUtilService.createTeamTextExercise(course, now.minusDays(1), now.plusDays(1), now.plusDays(2));
        ownTeamParticipation = teamParticipationOfStudent1(teamTextExercise);
    }

    /**
     * The team websocket only saves team exercises, so student1 sends from a team participation of their own.
     */
    private StudentParticipation teamParticipationOfStudent1(Exercise exercise) {
        exercise.setMode(ExerciseMode.TEAM);
        exerciseRepository.save(exercise);
        Team team = teamUtilService.createTeam(Set.of(userUtilService.getUserByLogin(TEST_PREFIX + "student1")), userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"), exercise,
                "team" + exercise.getId());
        return participationUtilService.addTeamParticipationForExercise(exercise, team.getId());
    }

    private TextSubmission saveTextSubmission(TextExercise exercise, String login) {
        TextSubmission submission = ParticipationFactory.generateTextSubmission(EXISTING_TEXT, Language.ENGLISH, true);
        return textExerciseUtilService.saveTextSubmission(exercise, submission, login);
    }

    private static Principal principal(String login) {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(TEST_PREFIX + login);
        return principal;
    }

    private void sendTextViaWebsocket(long participationId, long submissionId) {
        var payload = new TeamTextSubmissionUpdateDTO(submissionId, UPDATE_TEXT, Language.ENGLISH, true);
        try {
            participationTeamWebsocketService.updateTextSubmission(participationId, payload, principal("student1"));
        }
        catch (AccessForbiddenException ignored) {
            // a refused save is a valid outcome, the row is asserted by the caller
        }
    }

    private void assertTextUnchanged(TextSubmission submission) {
        TextSubmission reloaded = textSubmissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(reloaded.getText()).isEqualTo(EXISTING_TEXT);
        assertThat(reloaded.getParticipation().getId()).isEqualTo(submission.getParticipation().getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void teamUpdateIgnoresSubmissionIdOfAnotherParticipation() {
        TextSubmission other = saveTextSubmission(textExercise, TEST_PREFIX + "student2");

        sendTextViaWebsocket(ownTeamParticipation.getId(), other.getId());

        assertTextUnchanged(other);
    }

    /**
     * Builds an exam text exercise whose exam has already ended, with a submitted answer of the given student in it.
     */
    private TextSubmission saveAnswerInEndedExam(String login) {
        ExerciseGroup exerciseGroup = examUtilService.addEnrolledExerciseGroupWithExamAndCourse(true, true, TEST_PREFIX);
        Exam exam = exerciseGroup.getExam();
        TextExercise examExercise = exerciseRepository.save(TextExerciseFactory.generateTextExerciseForExam(exerciseGroup));
        examUtilService.addExerciseToStudentExam(examUtilService.addStudentExamWithUser(exam, TEST_PREFIX + login), examExercise);
        TextSubmission answer = saveTextSubmission(examExercise, TEST_PREFIX + login);
        ZonedDateTime now = ZonedDateTime.now();
        examUtilService.setVisibleStartAndEndDateOfExam(exam, now.minusHours(3), now.minusHours(2), now.minusHours(1));
        examRepository.save(exam);
        return answer;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void teamUpdateIgnoresExamSubmissionIdOfAnotherParticipation() {
        TextSubmission other = saveAnswerInEndedExam("student2");

        sendTextViaWebsocket(ownTeamParticipation.getId(), other.getId());

        assertTextUnchanged(other);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void teamUpdateDoesNotSaveExamSubmissions() {
        TextSubmission ownAnswer = saveAnswerInEndedExam("student1");

        sendTextViaWebsocket(ownAnswer.getParticipation().getId(), ownAnswer.getId());

        assertTextUnchanged(ownAnswer);
    }

    private QuizSubmission saveCourseQuizSubmissionOfStudent2() {
        Course quizCourse = quizExerciseUtilService.addEnrolledCourseWithOneQuizExercise("Quiz", TEST_PREFIX);
        QuizExercise courseQuiz = (QuizExercise) quizCourse.getExercises().iterator().next();
        participationUtilService.createAndSaveParticipationForExercise(courseQuiz, TEST_PREFIX + "student1");
        return quizExerciseUtilService.saveQuizSubmission(courseQuiz, ParticipationFactory.generateQuizSubmission(false), TEST_PREFIX + "student2");
    }

    private int sendQuizForExam(long exerciseId, long submissionId) throws Exception {
        return request.performMvcRequest(MockMvcRequestBuilders.put("/api/quiz/exercises/" + exerciseId + "/submissions/exam").contentType(MediaType.APPLICATION_JSON)
                .content(request.getObjectMapper().writeValueAsString(new QuizSubmissionFromLiveClientDTO(submissionId, Set.of())))).andReturn().getResponse().getStatus();
    }

    private void assertQuizUnchanged(QuizSubmission submission) {
        QuizSubmission reloaded = quizSubmissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(reloaded.getParticipation().getId()).isEqualTo(submission.getParticipation().getId());
        assertThat(reloaded.isSubmitted()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void examQuizEndpointRejectsCourseExercise() throws Exception {
        QuizSubmission courseSubmission = saveCourseQuizSubmissionOfStudent2();

        int status = sendQuizForExam(courseSubmission.getParticipation().getExercise().getId(), courseSubmission.getId());

        assertQuizUnchanged(courseSubmission);
        assertThat(status).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void examQuizSaveIgnoresSubmissionIdOfAnotherParticipation() throws Exception {
        QuizSubmission other = saveCourseQuizSubmissionOfStudent2();
        ExerciseGroup exerciseGroup = examUtilService.addEnrolledExerciseGroupWithExamAndCourse(true, true, TEST_PREFIX);
        QuizExercise examQuiz = quizExerciseRepository.save(QuizExerciseFactory.createQuizForExam(exerciseGroup));
        examUtilService.addExerciseToStudentExam(examUtilService.addStudentExamWithUser(exerciseGroup.getExam(), TEST_PREFIX + "student1"), examQuiz);
        participationUtilService.createAndSaveParticipationForExercise(examQuiz, TEST_PREFIX + "student1");

        // A real exam resolves the participation through the gate, which replaces the client id with the student's own
        // submission (or clears it), so the id from another participation never reaches the save and a new row is created.
        int status = sendQuizForExam(examQuiz.getId(), other.getId());

        assertQuizUnchanged(other);
        assertThat(status).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void repeatedTestRunSaveWritesTheNewestParticipation() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addEnrolledExerciseGroupWithExamAndCourse(true, true, TEST_PREFIX);
        TextExercise examExercise = exerciseRepository.save(TextExerciseFactory.generateTextExerciseForExam(exerciseGroup));
        User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        studentExamRepository.save(examUtilService.generateTestRunForInstructor(exerciseGroup.getExam(), instructor, List.of(examExercise)));
        studentExamRepository.save(examUtilService.generateTestRunForInstructor(exerciseGroup.getExam(), instructor, List.of(examExercise)));
        List<TextSubmission> testRunSubmissions = textSubmissionRepository.findAll().stream()
                .filter(submission -> submission.getParticipation() != null && submission.getParticipation().getExercise().getId().equals(examExercise.getId())).toList();
        assertThat(testRunSubmissions).hasSize(2);
        TextSubmission newestTestRun = testRunSubmissions.stream().max((a, b) -> Long.compare(a.getId(), b.getId())).orElseThrow();

        var payload = new TextSubmissionRequestDTO(newestTestRun.getId(), UPDATE_TEXT, Language.ENGLISH, true);
        request.put("/api/text/exercises/" + examExercise.getId() + "/text-submissions", payload, HttpStatus.OK);

        assertThat(textSubmissionRepository.findById(newestTestRun.getId()).orElseThrow().getText()).isEqualTo(UPDATE_TEXT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void teamUpdateIgnoresModelingSubmissionIdOfAnotherParticipation() {
        Course modelingCourse = modelingExerciseUtilService.addEnrolledCourseWithOneModelingExercise("Modeling", TEST_PREFIX);
        ModelingExercise modelingExercise = ExerciseUtilService.findModelingExerciseWithTitle(modelingCourse.getExercises(), "Modeling");
        StudentParticipation ownParticipation = teamParticipationOfStudent1(modelingExercise);
        ModelingSubmission other = modelingExerciseUtilService.addModelingSubmission(modelingExercise, ParticipationFactory.generateModelingSubmission(EXISTING_TEXT, true),
                TEST_PREFIX + "student2");

        var payload = new TeamModelingSubmissionUpdateDTO(other.getId(), UPDATE_TEXT, null, true);
        try {
            participationTeamWebsocketService.updateModelingSubmission(ownParticipation.getId(), payload, principal("student1"));
        }
        catch (AccessForbiddenException ignored) {
            // a refused save is a valid outcome, the row is asserted below
        }

        ModelingSubmission reloaded = modelingSubmissionRepository.findById(other.getId()).orElseThrow();
        assertThat(reloaded.getModel()).isEqualTo(EXISTING_TEXT);
        assertThat(reloaded.getParticipation().getId()).isEqualTo(other.getParticipation().getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void restSaveIgnoresSubmissionIdFromAnotherExercise() throws Exception {
        ZonedDateTime now = ZonedDateTime.now();
        TextExercise closedExercise = textExerciseUtilService.createIndividualTextExercise(course, now.minusDays(3), now.minusDays(1), now.plusDays(1));
        TextSubmission ownClosedSubmission = saveTextSubmission(closedExercise, TEST_PREFIX + "student1");

        var payload = new TextSubmissionRequestDTO(ownClosedSubmission.getId(), UPDATE_TEXT, Language.ENGLISH, true);
        request.put("/api/text/exercises/" + textExercise.getId() + "/text-submissions", payload, HttpStatus.FORBIDDEN);

        assertTextUnchanged(ownClosedSubmission);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void restSaveIgnoresExampleSubmissionId() throws Exception {
        ExampleSubmission example = participationUtilService.addExampleSubmission(participationUtilService.generateExampleSubmission(EXISTING_TEXT, textExercise, true));
        TextSubmission exampleSubmission = (TextSubmission) example.getSubmission();

        var payload = new TextSubmissionRequestDTO(exampleSubmission.getId(), UPDATE_TEXT, Language.ENGLISH, true);
        request.put("/api/text/exercises/" + textExercise.getId() + "/text-submissions", payload, HttpStatus.FORBIDDEN);

        assertThat(textSubmissionRepository.findById(exampleSubmission.getId()).orElseThrow().getText()).isEqualTo(EXISTING_TEXT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void teamUpdateWritesSubmissionOfItsOwnParticipation() {
        TextSubmission own = (TextSubmission) participationUtilService.addSubmission(ownTeamParticipation,
                ParticipationFactory.generateTextSubmission(EXISTING_TEXT, Language.ENGLISH, true));

        sendTextViaWebsocket(ownTeamParticipation.getId(), own.getId());

        assertThat(textSubmissionRepository.findById(own.getId()).orElseThrow().getText()).isEqualTo(UPDATE_TEXT);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void textSubmissionValidationPreservesParticipationState(boolean existingSubmission) throws Exception {
        TextSubmission stored = saveTextSubmission(textExercise, TEST_PREFIX + "student2");
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student1");
        studentParticipationRepository.updateInitializationState(participation.getId(), InitializationState.INITIALIZED);
        long count = textSubmissionRepository.count();
        long submissionId = existingSubmission ? stored.getId() : Long.MAX_VALUE;
        clearInvocations(exerciseDateService);

        var payload = new TextSubmissionRequestDTO(submissionId, UPDATE_TEXT, Language.ENGLISH, true);
        request.put("/api/text/exercises/" + textExercise.getId() + "/text-submissions", payload, HttpStatus.FORBIDDEN);

        assertThat(studentParticipationRepository.findByIdElseThrow(participation.getId()).getInitializationState()).isEqualTo(InitializationState.INITIALIZED);
        assertThat(textSubmissionRepository.count()).isEqualTo(count);
        verify(exerciseDateService, never()).isBeforeDueDate(any(TextExercise.class), any(StudentParticipationSubmitTargetDTO.class), any(User.class));
        assertTextUnchanged(stored);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void modelingSubmissionValidationPreservesParticipationState(boolean existingSubmission) throws Exception {
        Course modelingCourse = modelingExerciseUtilService.addEnrolledCourseWithOneModelingExercise("Modeling", TEST_PREFIX);
        ModelingExercise exercise = ExerciseUtilService.findModelingExerciseWithTitle(modelingCourse.getExercises(), "Modeling");
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
        ModelingSubmission stored = modelingExerciseUtilService.addModelingSubmission(exercise, ParticipationFactory.generateModelingSubmission(EXISTING_TEXT, true),
                TEST_PREFIX + "student2");
        studentParticipationRepository.updateInitializationState(participation.getId(), InitializationState.INITIALIZED);
        long count = modelingSubmissionRepository.count();
        long submissionId = existingSubmission ? stored.getId() : Long.MAX_VALUE;
        clearInvocations(exerciseDateService);

        var payload = new ModelingSubmissionRequestDTO(submissionId, UPDATE_TEXT, null, true);
        request.put("/api/modeling/exercises/" + exercise.getId() + "/modeling-submissions", payload, HttpStatus.FORBIDDEN);

        assertThat(studentParticipationRepository.findByIdElseThrow(participation.getId()).getInitializationState()).isEqualTo(InitializationState.INITIALIZED);
        assertThat(modelingSubmissionRepository.count()).isEqualTo(count);
        verify(exerciseDateService, never()).isBeforeDueDate(any(ModelingExercise.class), any(StudentParticipationSubmitTargetDTO.class), any(User.class));
        ModelingSubmission reloaded = modelingSubmissionRepository.findByIdElseThrow(stored.getId());
        assertThat(reloaded.getModel()).isEqualTo(EXISTING_TEXT);
        assertThat(reloaded.getParticipation().getId()).isEqualTo(stored.getParticipation().getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void textSubmissionSaveConflictPreservesParticipationState(boolean websocket) throws Exception {
        TextSubmission stored = (TextSubmission) participationUtilService.addSubmission(ownTeamParticipation,
                ParticipationFactory.generateTextSubmission(EXISTING_TEXT, Language.ENGLISH, true));
        long participationId = ownTeamParticipation.getId();
        long submissionId = stored.getId();
        studentParticipationRepository.updateInitializationState(participationId, InitializationState.INITIALIZED);
        doAnswer(invocation -> {
            boolean beforeDueDate = (boolean) invocation.callRealMethod();
            textSubmissionRepository.deleteById(submissionId);
            return beforeDueDate;
        }).when(exerciseDateService).isBeforeDueDate(any(TextExercise.class), any(StudentParticipationSubmitTargetDTO.class), any(User.class));
        long versionCount = submissionVersionRepository.count();
        clearInvocations(websocketMessagingService);

        if (websocket) {
            var payload = new TeamTextSubmissionUpdateDTO(submissionId, UPDATE_TEXT, Language.ENGLISH, true);
            assertThatExceptionOfType(ResponseStatusException.class)
                    .isThrownBy(() -> participationTeamWebsocketService.updateTextSubmission(participationId, payload, principal("student1")))
                    .satisfies(exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        }
        else {
            var payload = new TextSubmissionRequestDTO(submissionId, UPDATE_TEXT, Language.ENGLISH, true);
            request.put("/api/text/exercises/" + ownTeamParticipation.getExercise().getId() + "/text-submissions", payload, HttpStatus.CONFLICT);
        }

        assertThat(textSubmissionRepository.findById(submissionId)).isEmpty();
        assertThat(studentParticipationRepository.findByIdElseThrow(participationId).getInitializationState()).isEqualTo(InitializationState.INITIALIZED);
        assertThat(submissionVersionRepository.count()).isEqualTo(versionCount);
        verify(websocketMessagingService, never()).sendMessage(eq("/topic/participations/" + participationId + "/team/text-submissions"), any(Object.class));
    }

    @Test
    void textSubmissionUpdateRejectsResultCreatedAfterPrecheck() {
        TextSubmission stored = (TextSubmission) participationUtilService.addSubmission(ownTeamParticipation,
                ParticipationFactory.generateTextSubmission(EXISTING_TEXT, Language.ENGLISH, true));
        assertThat(resultRepository.existsBySubmissionId(stored.getId())).isFalse();

        TextSubmission assessed = (TextSubmission) participationUtilService.addResultToSubmission(stored, AssessmentType.MANUAL);
        Long resultId = assessed.getLatestResult().getId();

        int updatedRows = textSubmissionRepository.updateExistingSubmission(stored.getId(), ownTeamParticipation.getId(), UPDATE_TEXT, Language.ENGLISH, true,
                ZonedDateTime.parse("2026-01-01T00:00:00Z"), stored.getType());

        assertThat(updatedRows).as("the atomic update notices the result inserted after the precheck").isZero();
        TextSubmission preserved = textSubmissionRepository.findByIdWithParticipationExerciseResultAssessorElseThrow(stored.getId());
        assertThat(preserved.getText()).isEqualTo(EXISTING_TEXT);
        assertThat(preserved.getResults()).singleElement().extracting(result -> result.getId()).isEqualTo(resultId);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void modelingSubmissionSaveConflictPreservesParticipationState(boolean websocket) throws Exception {
        Course modelingCourse = modelingExerciseUtilService.addEnrolledCourseWithOneModelingExercise("Modeling", TEST_PREFIX);
        ModelingExercise exercise = ExerciseUtilService.findModelingExerciseWithTitle(modelingCourse.getExercises(), "Modeling");
        StudentParticipation participation = teamParticipationOfStudent1(exercise);
        ModelingSubmission stored = (ModelingSubmission) participationUtilService.addSubmission(participation,
                ParticipationFactory.generateModelingSubmission(EXISTING_TEXT, true));
        long participationId = participation.getId();
        long submissionId = stored.getId();
        studentParticipationRepository.updateInitializationState(participationId, InitializationState.INITIALIZED);
        doAnswer(invocation -> {
            boolean beforeDueDate = (boolean) invocation.callRealMethod();
            modelingSubmissionRepository.deleteById(submissionId);
            return beforeDueDate;
        }).when(exerciseDateService).isBeforeDueDate(any(ModelingExercise.class), any(StudentParticipationSubmitTargetDTO.class), any(User.class));
        long versionCount = submissionVersionRepository.count();

        if (websocket) {
            var payload = new TeamModelingSubmissionUpdateDTO(submissionId, UPDATE_TEXT, null, true);
            assertThatExceptionOfType(ResponseStatusException.class)
                    .isThrownBy(() -> participationTeamWebsocketService.updateModelingSubmission(participationId, payload, principal("student1")))
                    .satisfies(exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        }
        else {
            var payload = new ModelingSubmissionRequestDTO(submissionId, UPDATE_TEXT, null, true);
            request.put("/api/modeling/exercises/" + exercise.getId() + "/modeling-submissions", payload, HttpStatus.CONFLICT);
        }

        assertThat(modelingSubmissionRepository.findById(submissionId)).isEmpty();
        assertThat(studentParticipationRepository.findByIdElseThrow(participationId).getInitializationState()).isEqualTo(InitializationState.INITIALIZED);
        assertThat(submissionVersionRepository.count()).isEqualTo(versionCount);
    }

}
