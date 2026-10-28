package de.tum.cit.aet.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.AdditionalAnswers;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.metis.domain.MetisCheck;
import de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus;
import de.tum.cit.aet.artemis.metis.domain.MetisQuestion;
import de.tum.cit.aet.artemis.metis.dto.MetisQuestionDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisQuestionTextRequestDTO;
import de.tum.cit.aet.artemis.metis.repository.MetisCheckRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisQuestionRepository;
import de.tum.cit.aet.artemis.metis.service.MetisQuestionGenerationService;
import de.tum.cit.aet.artemis.metis.service.MetisQuestionReviewService;
import de.tum.cit.aet.artemis.metis.util.MetisUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * The review of the generated questions of an exercise through its REST endpoints: approving one main question or all of them, changing the text of a main
 * question, and the checks a regeneration makes before it asks the language model, each against the roles that may and may not use them and against questions
 * of another exercise.
 * <p>
 * The clock is frozen per method, so that the times of the approvals are known. A regeneration that passes every check needs the student's repository, which
 * this bucket cannot read, so it ends with the conflict of an unreadable repository here; {@code MetisGenerationIntegrationTest} covers the regeneration
 * itself against real repositories. No method switches the check of an exercise on, so no check of this class is ever claimed by a dispatch elsewhere.
 */
class MetisQuestionReviewIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "metisquestionreview";

    /** The frozen time of every method. Every other time is an offset from it, in whole seconds, which the database keeps exactly. */
    private static final ZonedDateTime NOW = ZonedDateTime.parse("2024-04-08T09:00:00Z");

    private static final ZonedDateTime DUE = NOW.minusDays(1);

    private static final String COMMIT_HASH = "c".repeat(40);

    /** The error key of a request that bean validation rejects. */
    private static final String VALIDATION = "validation";

    private static final String NEW_TEXT = "Which case does the second branch of compare handle?";

    @Autowired
    private MetisUtilService metisUtilService;

    @Autowired
    private MetisCheckRepository checkRepository;

    @Autowired
    private MetisQuestionRepository questionRepository;

    @Autowired
    private MetisQuestionGenerationService generationService;

    private Course course;

    private ProgrammingExercise exercise;

    private long exerciseId;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 3, 1, 1, 1);
        course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exerciseId = exercise.getId();
        Exercise stored = exerciseRepository.findByIdElseThrow(exerciseId);
        stored.setReleaseDate(DUE.minusDays(7));
        exerciseRepository.save(stored);
        exerciseUtilService.updateExerciseDueDate(exerciseId, DUE);
        // The fixture seeds cloud AI consent only for accounts without any decision; the regeneration checks the consent before it reads the repository
        for (String student : List.of("student1", "student2", "student3")) {
            userUtilService.setAiSelectionDecision(user(student), AiSelectionDecision.CLOUD_AI);
        }
        freezeClockAt(NOW);
    }

    @AfterEach
    void tearDown() {
        TimeUtil.resetClock();
    }

    @Nested
    class ApproveQuestion {

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldApproveAMainQuestionAndReleaseTheCheckWithTheLastOne() throws Exception {
            MetisCheck check = check(exercise, "student1", MetisCheckStatus.IN_REVIEW);
            MetisQuestion first = mainQuestion(check, 1);
            MetisQuestion second = mainQuestion(check, 2);

            MetisQuestionDTO approvedFirst = approve(first);

            assertThat(approvedFirst.id()).isEqualTo(first.getId());
            assertThat(approvedFirst.questionText()).isEqualTo(first.getQuestionText());
            assertThat(approvedFirst.approvedAt().toInstant()).isEqualTo(NOW.toInstant());
            assertThat(statusOf(check)).as("one main question is still unapproved").isEqualTo(MetisCheckStatus.IN_REVIEW);

            freezeClockAt(NOW.plusMinutes(5));
            MetisQuestionDTO approvedSecond = approve(second);

            assertThat(approvedSecond.approvedAt().toInstant()).isEqualTo(NOW.plusMinutes(5).toInstant());
            assertThat(statusOf(check)).isEqualTo(MetisCheckStatus.READY);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldKeepTheFirstApprovalWhenAQuestionIsApprovedAgain() throws Exception {
            MetisCheck check = check(exercise, "student1", MetisCheckStatus.IN_REVIEW);
            MetisQuestion question = mainQuestion(check, 1);
            approve(question);
            freezeClockAt(NOW.plusHours(1));

            MetisQuestionDTO approvedAgain = approve(question);

            assertThat(approvedAgain.approvedAt().toInstant()).isEqualTo(NOW.toInstant());
            assertThat(statusOf(check)).isEqualTo(MetisCheckStatus.READY);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldReleaseACheckWhoseQuestionsAreApprovedWhenAnApprovalIsRepeated() throws Exception {
            // A request that approved the last question and was interrupted before it released the check
            MetisCheck check = check(exercise, "student1", MetisCheckStatus.IN_REVIEW);
            MetisQuestion question = approved(mainQuestion(check, 1), NOW.minusHours(1));

            approve(question);

            assertThat(statusOf(check)).isEqualTo(MetisCheckStatus.READY);
            assertThat(reload(question).getApprovedAt().toInstant()).isEqualTo(NOW.minusHours(1).toInstant());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldRefuseToApproveAFollowUpQuestion() throws Exception {
            MetisCheck check = check(exercise, "student1", MetisCheckStatus.IN_PROGRESS);
            MetisQuestion main = shown(mainQuestion(check, 1), NOW.minusMinutes(5));
            MetisQuestion followUp = metisUtilService.saveFollowUp(check.getId(), 2, main.getId(), "Why is the copy needed?", 60);

            request.postAndExpectError(approvePath(followUp), null, HttpStatus.BAD_REQUEST, "metis.notMainQuestion");

            assertThat(reload(followUp).getApprovedAt()).isNull();
        }
    }

    @Nested
    class ApproveAllQuestions {

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldApproveEveryOpenMainQuestionAndReleaseTheChecksInReview() throws Exception {
            MetisCheck unapproved = check(exercise, "student1", MetisCheckStatus.IN_REVIEW);
            MetisQuestion firstOfUnapproved = mainQuestion(unapproved, 1);
            MetisQuestion secondOfUnapproved = mainQuestion(unapproved, 2);
            MetisCheck partlyApproved = check(exercise, "student2", MetisCheckStatus.IN_REVIEW);
            MetisQuestion approvedBefore = approved(mainQuestion(partlyApproved, 1), NOW.minusDays(1));
            MetisQuestion openOfPartlyApproved = mainQuestion(partlyApproved, 2);
            MetisCheck started = check(exercise, "student3", MetisCheckStatus.IN_PROGRESS);
            MetisQuestion mainOfStarted = shown(mainQuestion(started, 1), NOW.minusMinutes(5));
            MetisQuestion followUp = metisUtilService.saveFollowUp(started.getId(), 2, mainOfStarted.getId(), "Why is the copy needed?", 60);
            MetisCheck ofOtherExercise = check(otherExercise(), "student1", MetisCheckStatus.IN_REVIEW);
            MetisQuestion questionOfOtherExercise = mainQuestion(ofOtherExercise, 1);

            request.postWithoutResponseBody(approveAllPath(), HttpStatus.OK, new LinkedMultiValueMap<>());

            for (MetisQuestion approvedNow : List.of(firstOfUnapproved, secondOfUnapproved, openOfPartlyApproved, mainOfStarted)) {
                ZonedDateTime approvedAt = reload(approvedNow).getApprovedAt();
                assertThat(approvedAt).as("approval of question %d", approvedNow.getId()).isNotNull();
                assertThat(approvedAt.toInstant()).isEqualTo(NOW.toInstant());
            }
            assertThat(reload(approvedBefore).getApprovedAt().toInstant()).isEqualTo(NOW.minusDays(1).toInstant());
            assertThat(reload(followUp).getApprovedAt()).as("follow-ups are never reviewed").isNull();
            assertThat(statusOf(unapproved)).isEqualTo(MetisCheckStatus.READY);
            assertThat(statusOf(partlyApproved)).isEqualTo(MetisCheckStatus.READY);
            assertThat(statusOf(started)).isEqualTo(MetisCheckStatus.IN_PROGRESS);
            assertThat(reload(questionOfOtherExercise).getApprovedAt()).isNull();
            assertThat(statusOf(ofOtherExercise)).isEqualTo(MetisCheckStatus.IN_REVIEW);
        }
    }

    @Nested
    class UpdateQuestionText {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = MetisCheckStatus.class, names = { "READY", "IN_REVIEW" })
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldReplaceTheTextAndKeepTheApproval(MetisCheckStatus status) throws Exception {
            MetisCheck check = check(exercise, "student1", status);
            MetisQuestion question = approved(mainQuestion(check, 1), NOW.minusHours(1));

            MetisQuestionDTO changed = updateText(question, "  " + NEW_TEXT + "\n");

            assertThat(changed.questionText()).isEqualTo(NEW_TEXT);
            assertThat(changed.approvedAt().toInstant()).isEqualTo(NOW.minusHours(1).toInstant());
            assertThat(changed.regenerationCount()).isZero();
            assertThat(reload(question).getQuestionText()).isEqualTo(NEW_TEXT);
            assertThat(statusOf(check)).isEqualTo(status);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldRefuseToChangeAShownQuestion() throws Exception {
            MetisCheck check = check(exercise, "student1", MetisCheckStatus.IN_PROGRESS);
            MetisQuestion question = shown(mainQuestion(check, 1), NOW.minusMinutes(1));

            request.putAndExpectError(textPath(question), new MetisQuestionTextRequestDTO(NEW_TEXT), HttpStatus.CONFLICT, "metis.questionShown");

            assertThat(reload(question).getQuestionText()).isEqualTo(question.getQuestionText());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldRefuseToChangeAnUnshownQuestionOfAStartedCheck() throws Exception {
            MetisCheck check = check(exercise, "student1", MetisCheckStatus.IN_PROGRESS);
            shown(mainQuestion(check, 1), NOW.minusMinutes(1));
            MetisQuestion unshown = mainQuestion(check, 2);

            request.putAndExpectError(textPath(unshown), new MetisQuestionTextRequestDTO(NEW_TEXT), HttpStatus.CONFLICT, "metis.notEditable");

            assertThat(reload(unshown).getQuestionText()).isEqualTo(unshown.getQuestionText());
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = MetisCheckStatus.class, names = { "PENDING", "GENERATING", "SKIPPED", "FAILED" })
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldRefuseToChangeAQuestionLeftOverFromAnEarlierGeneration(MetisCheckStatus status) throws Exception {
            // A check that went back into the queue, or ended without questions, keeps the questions of its earlier run until it is generated again
            MetisCheck check = check(exercise, "student1", status);
            MetisQuestion leftover = mainQuestion(check, 1);

            request.putAndExpectError(textPath(leftover), new MetisQuestionTextRequestDTO(NEW_TEXT), HttpStatus.CONFLICT, "metis.notEditable");

            assertThat(reload(leftover).getQuestionText()).isEqualTo(leftover.getQuestionText());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldRefuseToChangeAFollowUpQuestion() throws Exception {
            MetisCheck check = check(exercise, "student1", MetisCheckStatus.IN_PROGRESS);
            MetisQuestion main = shown(mainQuestion(check, 1), NOW.minusMinutes(5));
            MetisQuestion followUp = metisUtilService.saveFollowUp(check.getId(), 2, main.getId(), "Why is the copy needed?", 60);

            request.putAndExpectError(textPath(followUp), new MetisQuestionTextRequestDTO(NEW_TEXT), HttpStatus.BAD_REQUEST, "metis.notMainQuestion");

            assertThat(reload(followUp).getQuestionText()).isEqualTo("Why is the copy needed?");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldRejectABlankOrTooLongText() throws Exception {
            MetisCheck check = check(exercise, "student1", MetisCheckStatus.READY);
            MetisQuestion question = mainQuestion(check, 1);

            request.putAndExpectError(textPath(question), new MetisQuestionTextRequestDTO(" \n "), HttpStatus.BAD_REQUEST, VALIDATION);
            request.putAndExpectError(textPath(question), new MetisQuestionTextRequestDTO("x".repeat(2001)), HttpStatus.BAD_REQUEST, VALIDATION);

            assertThat(reload(question).getQuestionText()).isEqualTo(question.getQuestionText());
        }

        @Test
        void shouldRefuseTheChangeWhenTheStudentIsShownTheQuestionWhileTheRequestRuns() {
            MetisCheck check = check(exercise, "student1", MetisCheckStatus.READY);
            MetisQuestion question = mainQuestion(check, 1);
            // The student starts and sees the question after every read of the request, right before its write
            MetisQuestionReviewService service = serviceWithAWriteThatRuns(() -> {
                checkRepository.markStarted(check.getId(), NOW);
                questionRepository.markShown(question.getId(), NOW);
            });

            assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> service.updateQuestionText(exerciseId, question.getId(), NEW_TEXT))
                    .extracting(ConflictException::getErrorKey).isEqualTo("metis.questionShown");
            assertThat(reload(question).getQuestionText()).isEqualTo(question.getQuestionText());
        }

        @Test
        void shouldRefuseTheChangeWhenTheStudentStartsWhileTheRequestRuns() {
            MetisCheck check = check(exercise, "student1", MetisCheckStatus.IN_REVIEW);
            MetisQuestion question = mainQuestion(check, 2);
            // The student's window opened and the student started after every read of the request; the first question is shown, this one is not
            MetisQuestionReviewService service = serviceWithAWriteThatRuns(() -> checkRepository.markStarted(check.getId(), NOW));

            assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> service.updateQuestionText(exerciseId, question.getId(), NEW_TEXT))
                    .extracting(ConflictException::getErrorKey).isEqualTo("metis.notEditable");
            assertThat(reload(question).getQuestionText()).isEqualTo(question.getQuestionText());
        }

        /**
         * The review service with a question repository whose statements all run against the database, but which lets a parallel action happen right before
         * the statement that writes the text.
         */
        private MetisQuestionReviewService serviceWithAWriteThatRuns(Runnable parallelAction) {
            Answer<Object> realStatement = AdditionalAnswers.delegatesTo(questionRepository);
            MetisQuestionRepository racingRepository = mock(MetisQuestionRepository.class, invocation -> {
                if ("updateTextIfNotShown".equals(invocation.getMethod().getName())) {
                    parallelAction.run();
                }
                return realStatement.answer(invocation);
            });
            return new MetisQuestionReviewService(racingRepository, checkRepository, generationService);
        }
    }

    @Nested
    class RegenerateQuestion {

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldRefuseToRegenerateAFollowUpQuestion() throws Exception {
            MetisCheck check = check(exercise, "student1", MetisCheckStatus.IN_PROGRESS);
            MetisQuestion main = shown(mainQuestion(check, 1), NOW.minusMinutes(5));
            MetisQuestion followUp = metisUtilService.saveFollowUp(check.getId(), 2, main.getId(), "Why is the copy needed?", 60);

            request.postAndExpectError(regeneratePath(followUp), null, HttpStatus.BAD_REQUEST, "metis.notMainQuestion");

            verifyNoInteractions(metisLlmService);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldRefuseToRegenerateAShownQuestion() throws Exception {
            MetisCheck check = check(exercise, "student1", MetisCheckStatus.IN_PROGRESS);
            MetisQuestion question = shown(mainQuestion(check, 1), NOW.minusMinutes(1));

            request.postAndExpectError(regeneratePath(question), null, HttpStatus.CONFLICT, "metis.questionShown");

            verifyNoInteractions(metisLlmService);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldRefuseToRegenerateAnUnshownQuestionOfAStartedCheck() throws Exception {
            MetisCheck check = check(exercise, "student1", MetisCheckStatus.IN_PROGRESS);
            shown(mainQuestion(check, 1), NOW.minusMinutes(1));
            MetisQuestion unshown = mainQuestion(check, 2);

            request.postAndExpectError(regeneratePath(unshown), null, HttpStatus.CONFLICT, "metis.notEditable");

            assertThat(reload(unshown).getRegenerationCount()).isZero();
            verifyNoInteractions(metisLlmService);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldAnswerConflictWithoutCallingTheModelWhenTheRepositoryCannotBeRead() throws Exception {
            metisUtilService.saveConfig(exerciseId, false, true, null, null);
            MetisCheck check = check(exercise, "student1", MetisCheckStatus.READY);
            MetisQuestion question = approved(mainQuestion(check, 1), NOW.minusHours(1));

            request.postAndExpectError(regeneratePath(question), null, HttpStatus.CONFLICT, "metis.repositoryUnavailable");

            // The repository is read before the check moves into review for the call, so nothing changed
            MetisQuestion unchanged = reload(question);
            assertThat(unchanged.getQuestionText()).isEqualTo(question.getQuestionText());
            assertThat(unchanged.getRegenerationCount()).isZero();
            assertThat(unchanged.getApprovedAt().toInstant()).isEqualTo(NOW.minusHours(1).toInstant());
            assertThat(statusOf(check)).isEqualTo(MetisCheckStatus.READY);
            verifyNoInteractions(metisLlmService);
        }
    }

    @Nested
    class Authorization {

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldNotFindAQuestionOfAnotherExercise() throws Exception {
            MetisCheck check = check(otherExercise(), "student1", MetisCheckStatus.IN_REVIEW);
            MetisQuestion question = mainQuestion(check, 1);

            request.postWithResponseBody(approvePath(question), null, MetisQuestionDTO.class, HttpStatus.NOT_FOUND);
            request.put(textPath(question), new MetisQuestionTextRequestDTO(NEW_TEXT), HttpStatus.NOT_FOUND);
            request.postWithResponseBody(regeneratePath(question), null, MetisQuestionDTO.class, HttpStatus.NOT_FOUND);

            assertUnchanged(question, check);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void shouldForbidATutorEveryReviewAction() throws Exception {
            assertEveryReviewActionIsForbidden();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldForbidAStudentEveryReviewAction() throws Exception {
            assertEveryReviewActionIsForbidden();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldLetAnInstructorReviewTheQuestions() throws Exception {
            MetisCheck check = check(exercise, "student1", MetisCheckStatus.IN_REVIEW);
            MetisQuestion first = mainQuestion(check, 1);
            MetisQuestion second = mainQuestion(check, 2);

            assertThat(updateText(first, NEW_TEXT).questionText()).isEqualTo(NEW_TEXT);
            assertThat(approve(first).approvedAt()).isNotNull();
            request.postWithoutResponseBody(approveAllPath(), HttpStatus.OK, new LinkedMultiValueMap<>());

            assertThat(reload(second).getApprovedAt()).isNotNull();
            assertThat(statusOf(check)).isEqualTo(MetisCheckStatus.READY);
        }

        private void assertEveryReviewActionIsForbidden() throws Exception {
            MetisCheck check = check(exercise, "student1", MetisCheckStatus.IN_REVIEW);
            MetisQuestion question = mainQuestion(check, 1);

            request.postWithResponseBody(approvePath(question), null, MetisQuestionDTO.class, HttpStatus.FORBIDDEN);
            request.postWithoutResponseBody(approveAllPath(), HttpStatus.FORBIDDEN, new LinkedMultiValueMap<>());
            request.put(textPath(question), new MetisQuestionTextRequestDTO(NEW_TEXT), HttpStatus.FORBIDDEN);
            request.postWithResponseBody(regeneratePath(question), null, MetisQuestionDTO.class, HttpStatus.FORBIDDEN);

            assertUnchanged(question, check);
            verifyNoInteractions(metisLlmService);
        }

        private void assertUnchanged(MetisQuestion question, MetisCheck check) {
            MetisQuestion stored = reload(question);
            assertThat(stored.getApprovedAt()).isNull();
            assertThat(stored.getQuestionText()).isEqualTo(question.getQuestionText());
            assertThat(stored.getRegenerationCount()).isZero();
            assertThat(statusOf(check)).isEqualTo(MetisCheckStatus.IN_REVIEW);
        }
    }

    /**
     * A second programming exercise of the course, whose questions the review of the fixture exercise must neither find nor change.
     */
    private ProgrammingExercise otherExercise() {
        return programmingExerciseUtilService.addProgrammingExerciseToCourse(course, false, ProgrammingLanguage.JAVA, "Metis other", "METISOTHER", null);
    }

    private ProgrammingExerciseStudentParticipation participation(ProgrammingExercise participationExercise, String userSuffix) {
        var participation = ParticipationFactory.generateIndividualProgrammingExerciseStudentParticipation(participationExercise, user(userSuffix));
        String repositoryName = (participationExercise.getProjectKey() + "-" + TEST_PREFIX + userSuffix).toLowerCase(Locale.ROOT);
        participation.setRepositoryUri("http://localhost/git/" + participationExercise.getProjectKey() + "/" + repositoryName + ".git");
        return programmingExerciseStudentParticipationRepository.save(participation);
    }

    private MetisCheck check(ProgrammingExercise checkExercise, String userSuffix, MetisCheckStatus status) {
        return metisUtilService.saveGeneratedCheck(participation(checkExercise, userSuffix).getId(), status, COMMIT_HASH);
    }

    private MetisQuestion mainQuestion(MetisCheck check, int order) {
        return metisUtilService.saveMainQuestion(check.getId(), order, "src/Sorter" + order + ".java", 10 + order, "What does part " + order + " of the sorter do?", 120);
    }

    private MetisQuestion approved(MetisQuestion question, ZonedDateTime approvedAt) {
        return update(question, stored -> stored.setApprovedAt(approvedAt));
    }

    private MetisQuestion shown(MetisQuestion question, ZonedDateTime shownAt) {
        return update(question, stored -> stored.setShownAt(shownAt));
    }

    private MetisQuestion update(MetisQuestion question, Consumer<MetisQuestion> change) {
        MetisQuestion stored = reload(question);
        change.accept(stored);
        return questionRepository.save(stored);
    }

    private MetisQuestion reload(MetisQuestion question) {
        return questionRepository.findByIdElseThrow(question.getId());
    }

    private MetisCheckStatus statusOf(MetisCheck check) {
        return checkRepository.findByIdElseThrow(check.getId()).getStatus();
    }

    private MetisQuestionDTO approve(MetisQuestion question) throws Exception {
        return request.postWithResponseBody(approvePath(question), null, MetisQuestionDTO.class, HttpStatus.OK);
    }

    private MetisQuestionDTO updateText(MetisQuestion question, String text) throws Exception {
        return request.putWithResponseBody(textPath(question), new MetisQuestionTextRequestDTO(text), MetisQuestionDTO.class, HttpStatus.OK);
    }

    private User user(String suffix) {
        return userUtilService.getUserByLogin(TEST_PREFIX + suffix);
    }

    private static void freezeClockAt(ZonedDateTime time) {
        TimeUtil.setClock(Clock.fixed(time.toInstant(), ZoneOffset.UTC));
    }

    private String questionsPath() {
        return "/api/metis/exercises/" + exerciseId + "/questions";
    }

    private String approvePath(MetisQuestion question) {
        return questionsPath() + "/" + question.getId() + "/approve";
    }

    private String approveAllPath() {
        return questionsPath() + "/approve-all";
    }

    private String textPath(MetisQuestion question) {
        return questionsPath() + "/" + question.getId() + "/text";
    }

    private String regeneratePath(MetisQuestion question) {
        return questionsPath() + "/" + question.getId() + "/regenerate";
    }
}
