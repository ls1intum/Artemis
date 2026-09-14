package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
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
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.exercise.web.ParticipationTeamWebsocketService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.test_repository.ModelingSubmissionTestRepository;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.dto.submission.QuizSubmissionFromLiveClientDTO;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizSubmissionTestRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.dto.TextSubmissionRequestDTO;
import de.tum.cit.aet.artemis.text.test_repository.TextSubmissionTestRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * A submission id in a save request may only write a submission the caller owns in the very exercise being saved.
 * These tests pin that scope for the two save paths that accept a submission id from the client: the team websocket
 * save and the REST save, plus the exam quiz endpoint. Each reads the stored row back to assert it was left alone.
 */
class SubmissionUpdateScopeTest extends AbstractSpringIntegrationIndependentBatchTest {

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
    private StudentExamTestRepository studentExamRepository;

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
    private TeamUtilService teamUtilService;

    private Course course;

    private TextExercise textExercise;

    private StudentParticipation ownTeamParticipation;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        ZonedDateTime now = ZonedDateTime.now();
        Course textCourse = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("Text", TEST_PREFIX);
        course = textCourse;
        textExercise = ExerciseUtilService.findTextExerciseWithTitle(textCourse.getExercises(), "Text");
        participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student1");
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
        TextSubmission payload = ParticipationFactory.generateTextSubmission(UPDATE_TEXT, Language.ENGLISH, true);
        payload.setId(submissionId);
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
    void teamUpdateIgnoresTextSubmissionIdOfAnotherStudent() {
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
    void teamUpdateIgnoresExamSubmissionIdOfAnotherStudent() {
        TextSubmission other = saveAnswerInEndedExam("student2");

        sendTextViaWebsocket(ownTeamParticipation.getId(), other.getId());

        assertTextUnchanged(other);
    }

    /**
     * The websocket save applies none of the exam gates, so it must refuse an exam participation outright - otherwise a
     * student can keep writing answers after their exam has ended.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void teamUpdateDoesNotSaveExamSubmissions() {
        TextSubmission ownAnswer = saveAnswerInEndedExam("student1");

        sendTextViaWebsocket(ownAnswer.getParticipation().getId(), ownAnswer.getId());

        assertTextUnchanged(ownAnswer);
    }

    /**
     * Individual course exercises do not sync through the team endpoint at all.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void teamUpdateDoesNotSaveIndividualExercises() {
        TextSubmission own = saveTextSubmission(textExercise, TEST_PREFIX + "student1");

        sendTextViaWebsocket(own.getParticipation().getId(), own.getId());

        assertTextUnchanged(own);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void teamUpdateIgnoresModelingSubmissionIdOfAnotherStudent() {
        Course modelingCourse = modelingExerciseUtilService.addEnrolledCourseWithOneModelingExercise("Modeling", TEST_PREFIX);
        ModelingExercise modelingExercise = ExerciseUtilService.findModelingExerciseWithTitle(modelingCourse.getExercises(), "Modeling");
        StudentParticipation ownParticipation = teamParticipationOfStudent1(modelingExercise);
        ModelingSubmission other = modelingExerciseUtilService.addModelingSubmission(modelingExercise, ParticipationFactory.generateModelingSubmission(EXISTING_TEXT, true),
                TEST_PREFIX + "student2");

        ModelingSubmission payload = ParticipationFactory.generateModelingSubmission(UPDATE_TEXT, true);
        payload.setId(other.getId());
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

    /**
     * The positive control: a submission of the caller's own team participation is still written.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void teamUpdateWritesSubmissionOfItsOwnParticipation() {
        TextSubmission own = (TextSubmission) participationUtilService.addSubmission(ownTeamParticipation,
                ParticipationFactory.generateTextSubmission(EXISTING_TEXT, Language.ENGLISH, true));

        sendTextViaWebsocket(ownTeamParticipation.getId(), own.getId());

        TextSubmission reloaded = textSubmissionRepository.findById(own.getId()).orElseThrow();
        assertThat(reloaded.getText()).isEqualTo(UPDATE_TEXT);
        assertThat(reloaded.getParticipation().getId()).isEqualTo(ownTeamParticipation.getId());
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

    /**
     * An example submission hangs off no student participation, so the owner check used to wave it through.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void restSaveIgnoresExampleSubmissionId() throws Exception {
        ExampleSubmission example = participationUtilService.addExampleSubmission(participationUtilService.generateExampleSubmission(EXISTING_TEXT, textExercise, true));
        TextSubmission exampleSubmission = (TextSubmission) example.getSubmission();

        var payload = new TextSubmissionRequestDTO(exampleSubmission.getId(), UPDATE_TEXT, Language.ENGLISH, true);
        request.put("/api/text/exercises/" + textExercise.getId() + "/text-submissions", payload, HttpStatus.FORBIDDEN);

        assertThat(textSubmissionRepository.findById(exampleSubmission.getId()).orElseThrow().getText()).isEqualTo(EXISTING_TEXT);
    }

    private QuizSubmission saveCourseQuizSubmissionOfStudent2() {
        Course quizCourse = quizExerciseUtilService.addEnrolledCourseWithOneQuizExercise("Quiz", TEST_PREFIX);
        QuizExercise courseQuiz = (QuizExercise) quizCourse.getExercises().iterator().next();
        participationUtilService.createAndSaveParticipationForExercise(courseQuiz, TEST_PREFIX + "student1");
        return quizExerciseUtilService.saveQuizSubmission(courseQuiz, ParticipationFactory.generateQuizSubmission(false), TEST_PREFIX + "student2");
    }

    /**
     * The exam quiz endpoint applies only exam gates, so a course quiz must not reach the save: its submission id would
     * otherwise drive a merge onto whatever row it names.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void examQuizEndpointRejectsCourseExercise() throws Exception {
        QuizSubmission courseSubmission = saveCourseQuizSubmissionOfStudent2();
        long exerciseId = courseSubmission.getParticipation().getExercise().getId();

        int status = request
                .performMvcRequest(MockMvcRequestBuilders.put("/api/quiz/exercises/" + exerciseId + "/submissions/exam").contentType(MediaType.APPLICATION_JSON)
                        .content(request.getObjectMapper().writeValueAsString(new QuizSubmissionFromLiveClientDTO(courseSubmission.getId(), Set.of()))))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(HttpStatus.BAD_REQUEST.value());
        QuizSubmission reloaded = quizSubmissionRepository.findById(courseSubmission.getId()).orElseThrow();
        assertThat(reloaded.getParticipation().getId()).isEqualTo(courseSubmission.getParticipation().getId());
        assertThat(reloaded.isSubmitted()).isFalse();
    }

    /**
     * The participations an exam save resolves are ordered graded first, then newest first, so a second test run of the
     * same instructor does not decide which submission the save writes.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void repeatedTestRunSaveWritesTheNewestParticipation() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addEnrolledExerciseGroupWithExamAndCourse(true, true, TEST_PREFIX);
        TextExercise examExercise = exerciseRepository.save(TextExerciseFactory.generateTextExerciseForExam(exerciseGroup));
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
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
}
