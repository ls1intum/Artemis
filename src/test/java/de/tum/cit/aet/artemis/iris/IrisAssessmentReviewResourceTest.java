package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisAssessment;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdict;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdictReview;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.dto.IrisAssessmentDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisAssessmentProgrammingStudentParticipationDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisAssessmentReviewPageDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisQAExchangeDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisQuizTimerDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisAssessmentRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.util.IrisChatSessionFactory;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * HTTP-contract tests for {@link de.tum.cit.aet.artemis.iris.web.IrisAssessmentReviewResource}.
 */
class IrisAssessmentReviewResourceTest extends AbstractIrisChatSessionTest {

    private static final String TEST_PREFIX = "irisassessmentreview";

    @Autowired
    private IrisAssessmentRepository irisAssessmentRepository;

    @Autowired
    private IrisChatSessionRepository irisChatSessionRepository;

    @Autowired
    private ExamUtilService examUtilService;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    // =========================================================================
    // GET assessments/{assessmentId}/chat
    // =========================================================================

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void shouldReturnForbiddenWhenTutorRequestsAssessmentChat() throws Exception {
        var assessment = createAssessment(programmingExercise, IrisVerdict.SUSPICIOUS, null, List.of("reason"));

        request.getList(chatUrl(assessment), HttpStatus.FORBIDDEN, IrisQAExchangeDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnNotFoundWhenAssessmentForChatDoesNotExist() throws Exception {
        request.getList("/api/iris/assessments/" + NON_EXISTENT_ID + "/chat", HttpStatus.NOT_FOUND, IrisQAExchangeDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnNotFoundWhenNoFinishedAskUserSessionExistsForChat() throws Exception {
        var assessment = createAssessment(programmingExercise, IrisVerdict.SUSPICIOUS, null, List.of("reason"));

        request.getList(chatUrl(assessment), HttpStatus.NOT_FOUND, IrisQAExchangeDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnConflictWhenReasoningIsMissingForChat() throws Exception {
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        createFinishedAskUserSession(student, false, List.of("Question"), List.of());
        var assessment = createAssessment(programmingExercise, IrisVerdict.SUSPICIOUS, null, List.of());

        request.getList(chatUrl(assessment), HttpStatus.CONFLICT, IrisQAExchangeDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnQAExchangesWhenFinishedAskUserSessionAndReasoningExist() throws Exception {
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        // The first and last LLM messages are the quiz-explanation/quiz-finished framing messages and are dropped;
        // only the middle one is a real question.
        createFinishedAskUserSession(student, false, List.of("Explain quiz", "What does this method do?", "Quiz finished"), List.of("It sorts the list"));
        var assessment = createAssessment(programmingExercise, IrisVerdict.SUSPICIOUS, null, List.of("Because the student could explain the sorting logic"));

        var result = request.getList(chatUrl(assessment), HttpStatus.OK, IrisQAExchangeDTO.class);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().question()).isEqualTo("What does this method do?");
        assertThat(result.getFirst().answer()).isEqualTo("It sorts the list");
        assertThat(result.getFirst().reasoning()).isEqualTo("Because the student could explain the sorting logic");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnConflictWhenAssessmentExerciseIsExamExercise() throws Exception {
        ProgrammingExercise examExercise = createExamProgrammingExercise();
        var assessment = createAssessment(examExercise, IrisVerdict.SUSPICIOUS, null, List.of("reason"));

        request.getList(chatUrl(assessment), HttpStatus.CONFLICT, IrisQAExchangeDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnConflictWhenAssessmentExerciseIsTeamExercise() throws Exception {
        programmingExercise.setMode(ExerciseMode.TEAM);
        programmingExerciseRepository.save(programmingExercise);
        var assessment = createAssessment(programmingExercise, IrisVerdict.SUSPICIOUS, null, List.of("reason"));

        try {
            request.getList(chatUrl(assessment), HttpStatus.CONFLICT, IrisQAExchangeDTO.class);
        }
        finally {
            programmingExercise.setMode(ExerciseMode.INDIVIDUAL);
            programmingExerciseRepository.save(programmingExercise);
        }
    }

    // =========================================================================
    // PATCH assessments/{assessmentId}/accept
    // =========================================================================

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void shouldReturnForbiddenWhenTutorAcceptsAnswers() throws Exception {
        var assessment = createAssessment(programmingExercise, IrisVerdict.SUSPICIOUS, null, List.of());

        request.patch("/api/iris/assessments/" + assessment.getId() + "/accept", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnConflictWhenAcceptingAssessmentWithoutVerdict() throws Exception {
        var assessment = createAssessment(programmingExercise, null, null, List.of());

        request.patch("/api/iris/assessments/" + assessment.getId() + "/accept", null, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldSetVerdictReviewAcceptedWhenAcceptingAssessmentWithVerdict() throws Exception {
        var assessment = createAssessment(programmingExercise, IrisVerdict.SUSPICIOUS, null, List.of());

        request.patch("/api/iris/assessments/" + assessment.getId() + "/accept", null, HttpStatus.OK);

        var reloaded = irisAssessmentRepository.findById(assessment.getId()).orElseThrow();
        assertThat(reloaded.getVerdictReview()).isEqualTo(IrisVerdictReview.ACCEPTED);
    }

    // =========================================================================
    // PATCH assessments/{assessmentId}/reject
    // =========================================================================

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void shouldReturnForbiddenWhenTutorRejectsAnswers() throws Exception {
        var assessment = createAssessment(programmingExercise, IrisVerdict.SUSPICIOUS, null, List.of());

        request.patch("/api/iris/assessments/" + assessment.getId() + "/reject", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnConflictWhenRejectingAssessmentWithoutVerdict() throws Exception {
        var assessment = createAssessment(programmingExercise, null, null, List.of());

        request.patch("/api/iris/assessments/" + assessment.getId() + "/reject", null, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldSetVerdictReviewRejectedWhenRejectingAssessmentWithVerdict() throws Exception {
        var assessment = createAssessment(programmingExercise, IrisVerdict.SUSPICIOUS, null, List.of());

        request.patch("/api/iris/assessments/" + assessment.getId() + "/reject", null, HttpStatus.OK);

        var reloaded = irisAssessmentRepository.findById(assessment.getId()).orElseThrow();
        assertThat(reloaded.getVerdictReview()).isEqualTo(IrisVerdictReview.REJECTED);
    }

    // =========================================================================
    // GET assessments/{assessmentId}
    // =========================================================================

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void shouldReturnForbiddenWhenTutorRequestsAssessmentById() throws Exception {
        var assessment = createAssessment(programmingExercise, IrisVerdict.SUSPICIOUS, null, List.of());

        request.get("/api/iris/assessments/" + assessment.getId(), HttpStatus.FORBIDDEN, IrisAssessmentDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnNotFoundWhenAssessmentByIdDoesNotExist() throws Exception {
        request.get("/api/iris/assessments/" + NON_EXISTENT_ID, HttpStatus.NOT_FOUND, IrisAssessmentDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnAssessmentDtoWhenAssessmentExists() throws Exception {
        var assessment = createAssessment(programmingExercise, IrisVerdict.SUSPICIOUS, IrisVerdictReview.ACCEPTED, List.of());

        var dto = request.get("/api/iris/assessments/" + assessment.getId(), HttpStatus.OK, IrisAssessmentDTO.class);

        assertThat(dto.id()).isEqualTo(assessment.getId());
        assertThat(dto.verdict()).isEqualTo(IrisVerdict.SUSPICIOUS);
        assertThat(dto.verdictReview()).isEqualTo(IrisVerdictReview.ACCEPTED);
    }

    // =========================================================================
    // GET programming-exercises/{exerciseId}/participations/non-zero-latest-score
    // =========================================================================

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnForbiddenWhenStudentRequestsNonZeroLatestScoreParticipations() throws Exception {
        request.getSet("/api/iris/programming-exercises/" + programmingExercise.getId() + "/participations/non-zero-latest-score", HttpStatus.FORBIDDEN,
                IrisAssessmentProgrammingStudentParticipationDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void shouldReturnParticipationsWithoutAssessmentWhenNoneHaveBeenAssessedYet() throws Exception {
        // The shared test fixture already gives student1 a graded participation for the programming exercise, so the set is not
        // empty; none of the returned participations has an Iris assessment yet though, since no ask-user quiz has run.
        var result = request.getSet("/api/iris/programming-exercises/" + programmingExercise.getId() + "/participations/non-zero-latest-score", HttpStatus.OK,
                IrisAssessmentProgrammingStudentParticipationDTO.class);

        assertThat(result).isNotEmpty().allSatisfy(dto -> assertThat(dto.irisAssessment()).isNull());
    }

    // =========================================================================
    // GET courses/{courseId}/assessment-review/participations
    // =========================================================================

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnForbiddenWhenStudentRequestsAssessmentReviewParticipations() throws Exception {
        request.get(assessmentReviewParticipationsUrl(), HttpStatus.FORBIDDEN, IrisAssessmentReviewPageDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void shouldReturnMissingAssessmentFilterCountWhenNoAssessmentReviewParticipationsHaveBeenAssessedYet() throws Exception {
        // The shared test fixture already gives student1 a graded participation for the programming exercise, so the page is not
        // empty; it is counted under the "MissingAssessment" filter since no ask-user quiz has run for it yet.
        var result = request.get(assessmentReviewParticipationsUrl(), HttpStatus.OK, IrisAssessmentReviewPageDTO.class);

        assertThat(result.participations()).isNotEmpty();
        assertThat(result.participationsPerFilter()).containsEntry("MissingAssessment", (long) result.participations().size());
    }

    // =========================================================================
    // PATCH programming-exercises/{exerciseId}/ask-user/in-class/available
    // =========================================================================

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void shouldReturnForbiddenWhenTutorMakesInClassQuizAvailable() throws Exception {
        request.patchWithResponseBody(inClassAvailableUrl(), null, IrisQuizTimerDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnForbiddenWhenAskUserModeDisabledForMakeInClassQuizAvailable() throws Exception {
        disableAskUserMode();

        request.patchWithResponseBody(inClassAvailableUrl(), null, IrisQuizTimerDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnConflictWhenMakingInClassQuizAvailableForExamExercise() throws Exception {
        ProgrammingExercise examExercise = createExamProgrammingExercise();

        request.patchWithResponseBody("/api/iris/programming-exercises/" + examExercise.getId() + "/ask-user/in-class/available", null, IrisQuizTimerDTO.class,
                HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldActivateInClassQuizWindowWhenInstructorMakesItAvailable() throws Exception {
        var timer = request.patchWithResponseBody(inClassAvailableUrl(), null, IrisQuizTimerDTO.class, HttpStatus.OK);

        assertThat(timer.timerExpiresAt()).isNotNull();
        assertThat(timer.timeLimit()).isEqualTo(15 * 60);
        var reloaded = programmingExerciseRepository.findById(programmingExercise.getId()).orElseThrow();
        assertThat(reloaded.getIrisInClassQuizTimer()).isNotNull();
    }

    // =========================================================================
    // GET programming-exercises/{exerciseId}/ask-user/in-class
    // =========================================================================

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnNullBodyWhenNoInClassQuizWindowIsActive() throws Exception {
        var timer = request.getNullable(availableInClassUrl(), HttpStatus.OK, IrisQuizTimerDTO.class);

        assertThat(timer).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnTimerWhenInstructorMadeInClassQuizAvailableBeforehand() throws Exception {
        request.patchWithResponseBody(inClassAvailableUrl(), null, IrisQuizTimerDTO.class, HttpStatus.OK);

        var timer = request.getNullable(availableInClassUrl(), HttpStatus.OK, IrisQuizTimerDTO.class);

        assertThat(timer).isNotNull();
        assertThat(timer.timerExpiresAt()).isNotNull();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String chatUrl(IrisAssessment assessment) {
        return "/api/iris/assessments/" + assessment.getId() + "/chat";
    }

    private String assessmentReviewParticipationsUrl() {
        return "/api/iris/courses/" + course.getId() + "/assessment-review/participations?page=0&pageSize=20";
    }

    private String inClassAvailableUrl() {
        return "/api/iris/programming-exercises/" + programmingExercise.getId() + "/ask-user/in-class/available";
    }

    private String availableInClassUrl() {
        return "/api/iris/programming-exercises/" + programmingExercise.getId() + "/ask-user/in-class";
    }

    private IrisAssessment createAssessment(ProgrammingExercise exercise, IrisVerdict verdict, IrisVerdictReview review, List<String> reasoning) {
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var assessment = new IrisAssessment(student, exercise);
        assessment.setVerdict(verdict);
        assessment.setVerdictReview(review);
        assessment.setReasoning(new ArrayList<>(reasoning));
        return irisAssessmentRepository.save(assessment);
    }

    private void createFinishedAskUserSession(User user, boolean inClass, List<String> llmMessageContents, List<String> userMessageContents) {
        IrisChatSession session = IrisChatSessionFactory.createProgrammingExerciseChatSessionForUser(programmingExercise, user);
        session.setInClassQuiz(inClass);
        session.setInAskUserModePipeline(false);

        // Messages must be added through the session's own (Hibernate-managed, @OrderColumn-backed) message list and persisted via
        // a cascading save of the session itself; saving IrisMessages directly through their own repository leaves the order column
        // unset and later reads of session.getMessages() fail with "Illegal null value for list index".
        for (String content : llmMessageContents) {
            session.getMessages().add(createAskUserModeMessage(session, IrisMessageSender.LLM, content));
        }
        for (String content : userMessageContents) {
            session.getMessages().add(createAskUserModeMessage(session, IrisMessageSender.USER, content));
        }

        irisChatSessionRepository.save(session);
    }

    private IrisMessage createAskUserModeMessage(IrisChatSession session, IrisMessageSender sender, String content) {
        IrisMessage message = new IrisMessage();
        message.setSession(session);
        message.setSender(sender);
        message.setInAskUserMode(true);
        message.addContent(new IrisTextMessageContent(content));
        return message;
    }

    private ProgrammingExercise createExamProgrammingExercise() {
        var exam = examUtilService.addExamWithExerciseGroup(course, true);
        exam = examUtilService.addExerciseGroupsAndExercisesToExam(exam, true);
        ProgrammingExercise examProgrammingExercise = exam.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream())
                .filter(ProgrammingExercise.class::isInstance).map(ProgrammingExercise.class::cast).findFirst().orElseThrow();
        activateIrisFor(examProgrammingExercise);
        return examProgrammingExercise;
    }

    private void disableAskUserMode() {
        var current = irisSettingsService.getSettingsForCourse(course);
        irisSettingsService.updateCourseSettings(course.getId(), IrisCourseSettings.of(current.enabled(), false, current.askUserModeSettings(), current.customInstructions(),
                current.variant(), current.supportLevel(), current.rateLimit()), true);
    }
}
