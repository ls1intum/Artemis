package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.dto.UpdatePostingDTO;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationMessageRepository;
import de.tum.cit.aet.artemis.communication.service.AnswerMessageService;
import de.tum.cit.aet.artemis.communication.service.ConversationMessagingService;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationService;
import de.tum.cit.aet.artemis.communication.test_repository.ConversationTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.domain.CourseMemoryOperation;
import de.tum.cit.aet.artemis.iris.domain.CourseMemoryStage;
import de.tum.cit.aet.artemis.iris.dto.IrisCourseMemoryStatusDTO;
import de.tum.cit.aet.artemis.iris.service.CourseMemoryIngestionService;
import de.tum.cit.aet.artemis.iris.service.IrisBotUserService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisStatusUpdateService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisCourseMemoryIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisCourseMemorySource;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisCourseMemoryThreadMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisWebhookCourseMemoryDeletionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisWebhookCourseMemoryIngestionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStatusErrorDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.CourseMemoryIngestionWebhookJob;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class CourseMemoryIngestionIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "coursememingest";

    @Autowired
    private CourseMemoryIngestionService courseMemoryIngestionService;

    @Autowired
    private AnswerMessageService answerMessageService;

    @Autowired
    private ConversationMessagingService conversationMessagingService;

    @Autowired
    private IrisBotUserService irisBotUserService;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private ConversationMessageRepository conversationMessageRepository;

    @Autowired
    private ConversationTestRepository conversationRepository;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ChannelService channelService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private PyrisJobService pyrisJobService;

    @Autowired
    private PyrisStatusUpdateService pyrisStatusUpdateService;

    private Course course;

    private Channel channel;

    private User student;

    private User tutor;

    private User botUser;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        course = courseUtilService.createEnrolledCourseWithMessagingEnabled(TEST_PREFIX);
        channel = conversationUtilService.createCourseWideChannel(course, "general");
        irisBotUserService.ensureIrisBotUserExists();
        botUser = irisBotUserService.getIrisBotUser();
        student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        // The opt-out and AI-selection tests below persist this decision and users are shared across
        // methods in the class, so reset it here to keep the tests order-independent.
        userUtilService.clearAiSelectionDecision(student);
        userUtilService.clearAiSelectionDecision(tutor);
        userUtilService.clearAiSelectionDecision(userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
        enableIrisFor(course);
    }

    private Post createQuestion(String content) {
        Post post = new Post();
        post.setAuthor(student);
        post.setContent(content);
        post.setConversation(channel);
        post.setVisibleForStudents(true);
        return conversationMessageRepository.save(post);
    }

    private AnswerPost saveAnswer(Post post, User author, String content, boolean verified) {
        return saveAnswer(post, author, content, verified, false);
    }

    private AnswerPost saveAnswer(Post post, User author, String content, boolean verified, boolean resolvesPost) {
        AnswerPost answer = new AnswerPost();
        answer.setPost(post);
        answer.setAuthor(author);
        answer.setContent(content);
        answer.setVerified(verified);
        answer.setResolvesPost(resolvesPost);
        if (verified) {
            answer.setVerifiedAt(ZonedDateTime.now());
        }
        return answerPostRepository.save(answer);
    }

    /**
     * An answer marked as resolving its post by {@code endorser}, as {@code AnswerMessageService} records it. The
     * endorsement is what Course Memory derives the trust tier from, so a test asserting a tier has to state it;
     * {@link #saveAnswer(Post, User, String, boolean, boolean)} with {@code resolvesPost} leaves it unrecorded,
     * which is the state of every answer resolved before endorsers existed.
     */
    private AnswerPost saveResolvingAnswer(Post post, User author, String content, boolean verified, User endorser) {
        AnswerPost answer = new AnswerPost();
        answer.setPost(post);
        answer.setAuthor(author);
        answer.setContent(content);
        answer.setVerified(verified);
        answer.setResolution(true, endorser);
        if (verified) {
            answer.setVerifiedAt(ZonedDateTime.now());
        }
        return answerPostRepository.save(answer);
    }

    /**
     * An Iris answer a tutor approved in the verification dashboard, as opposed to one published
     * automatically on a high confidence score. The two are stored identically except for
     * {@code verifiedBy}, which only the dashboard flow records — see
     * {@code AutonomousTutorService#createAndSaveAnswerPost}, which leaves it null because no human
     * reviewed the answer.
     */
    private AnswerPost saveDashboardVerifiedIrisAnswer(Post post, String content, boolean resolvesPost) {
        AnswerPost answer = saveAnswer(post, botUser, content, true, resolvesPost);
        answer.setVerifiedBy(tutor);
        return answerPostRepository.save(answer);
    }

    /**
     * Reloads the answer through its parent post so the eagerly-fetched thread (siblings + conversation)
     * matches what the production triggers operate on.
     */
    private AnswerPost reloadManagedAnswer(Post post, Long answerId) {
        Post reloaded = conversationMessageRepository.findMessagePostByIdElseThrow(post.getId());
        return reloaded.getAnswers().stream().filter(answer -> answer.getId().equals(answerId)).findFirst().orElseThrow();
    }

    private Post reloadPost(Post post) {
        return conversationMessageRepository.findMessagePostByIdElseThrow(post.getId());
    }

    private static PyrisCourseMemoryThreadMessageDTO messageWithId(List<PyrisCourseMemoryThreadMessageDTO> thread, String id) {
        return thread.stream().filter(message -> message.id().equals(id)).findFirst().orElseThrow();
    }

    // --- Service level: Trigger A (verified answer) ---

    @Test
    void ingestVerifiedAnswer_approvedAsIs_firesIrisAuto() {
        Post post = createQuestion("How do I submit the exercise?");
        AnswerPost answer = saveAnswer(post, botUser, "Push to your repo before the deadline.", true);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.ingestVerifiedAnswer(managed, false, tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.IRIS_AUTO);
        // Approving a draft unchanged still endorses that exact wording, so it travels verbatim rather than
        // being re-derived by the extraction model into a paraphrase the tutor never saw.
        assertThat(dto.existingAnswer()).isEqualTo("Push to your repo before the deadline.");
        assertThat(dto.courseId()).isEqualTo(course.getId());
        assertThat(dto.conversationId()).isEqualTo(String.valueOf(channel.getId()));
        assertThat(dto.postId()).isEqualTo(String.valueOf(post.getId()));
        assertThat(dto.messageId()).isEqualTo(String.valueOf(answer.getId()));
        assertThat(dto.isPublicChannel()).isTrue();
        assertThat(dto.verifiedBy()).isEqualTo(tutor.getLogin());
        assertThat(dto.settings().authenticationToken()).isNotNull();

        // thread is ordered oldest->newest: question first (student), then the verified Iris answer marked as draft.
        assertThat(dto.thread()).hasSize(2);
        var question = dto.thread().get(0);
        assertThat(question.id()).isEqualTo("post-" + post.getId());
        assertThat(question.authorRole()).isEqualTo("student");
        assertThat(question.isIrisDraft()).isFalse();
        assertThat(question.isVerifiedAnswer()).isFalse();
        var irisAnswer = dto.thread().get(1);
        assertThat(irisAnswer.id()).isEqualTo("answer-" + answer.getId());
        assertThat(irisAnswer.authorRole()).isEqualTo("iris");
        assertThat(irisAnswer.isIrisDraft()).isTrue();
        // Verification does not set resolvesPost, so the anchor comes solely from isVerifiedAnswer.
        assertThat(irisAnswer.isVerifiedAnswer()).isTrue();
        assertThat(irisAnswer.resolvesPost()).isFalse();
    }

    @Test
    void ingestVerifiedAnswer_edited_firesIrisCorrectedWithExistingAnswer() {
        Post post = createQuestion("When is the deadline?");
        AnswerPost answer = saveAnswer(post, botUser, "Corrected: only commits before 23:59 are graded.", true);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.ingestVerifiedAnswer(managed, true, tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.IRIS_CORRECTED);
        assertThat(dto.existingAnswer()).isEqualTo("Corrected: only commits before 23:59 are graded.");
        assertThat(dto.messageId()).isEqualTo(String.valueOf(answer.getId()));
    }

    // --- Service level: Trigger B (resolution changed) ---

    @Test
    void resolutionChanged_tutorAnswerMarkedByTutor_firesTutorWritten() {
        Post post = createQuestion("How is the exercise graded?");
        AnswerPost answer = saveResolvingAnswer(post, tutor, "The latest push before the deadline is graded.", true, tutor);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), managed, tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        // A tutor wrote the answer AND a tutor marked it resolving, so it earns the tutor-verified tier.
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.TUTOR_WRITTEN);
        assertThat(dto.postId()).isEqualTo(String.valueOf(post.getId()));
        assertThat(dto.messageId()).isEqualTo(String.valueOf(answer.getId()));
        assertThat(dto.verifiedBy()).isEqualTo(tutor.getLogin());
        assertThat(dto.existingAnswer()).isNull();
        assertThat(dto.thread()).hasSize(2);
        var tutorAnswer = dto.thread().get(1);
        assertThat(tutorAnswer.id()).isEqualTo("answer-" + answer.getId());
        assertThat(tutorAnswer.authorRole()).isEqualTo("tutor");
        assertThat(tutorAnswer.isIrisDraft()).isFalse();
        assertThat(tutorAnswer.isVerifiedAnswer()).isTrue();
        assertThat(tutorAnswer.resolvesPost()).isTrue();
    }

    @Test
    void resolutionChanged_tutorAnswerMarkedByStudent_staysThreadResolved() {
        Post post = createQuestion("Which Java version should I use?");
        AnswerPost answer = saveResolvingAnswer(post, tutor, "Java 25.", true, student);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        // The post author (a student) may mark any answer resolving, but that is not a tutor endorsement.
        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), managed, student, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.THREAD_RESOLVED);
        assertThat(dto.verifiedBy()).isNull();
        assertThat(dto.verifiedAt()).isNull();
    }

    @Test
    void resolutionChanged_studentAuthoredAnswer_isIngestedAsCommunityResolved() {
        Post post = createQuestion("Where do I find the slides?");
        AnswerPost answer = saveResolvingAnswer(post, student, "They are on the lecture page.", true, student);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), managed, student, course);

        // A peer answer that resolved the thread is worth remembering, but nobody with authority signed
        // off on it: it is stored, and labelled so retrieval can weight it as a hint rather than fact.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.THREAD_RESOLVED);
        assertThat(dto.verifiedBy()).isNull();
        assertThat(dto.verifiedAt()).isNull();
        assertThat(messageWithId(dto.thread(), "answer-" + answer.getId()).isVerifiedAnswer()).isTrue();
    }

    @Test
    void resolutionChanged_studentAnswerMarkedByTutor_isTutorEndorsed() {
        Post post = createQuestion("Is the exam open book?");
        AnswerPost answer = saveResolvingAnswer(post, student, "Yes, one A4 sheet is allowed.", true, tutor);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), managed, tutor, course);

        // The trust tier follows the endorsement, not the authorship: a tutor marking a student's answer
        // as the resolving one vouches for it just as much as writing it themselves.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.TUTOR_WRITTEN);
        assertThat(dto.verifiedBy()).isEqualTo(tutor.getLogin());
        assertThat(dto.verifiedAt()).isNotNull();
    }

    @Test
    void resolutionChanged_studentResolvingAnswerIsFlaggedInTheThread() {
        Post post = createQuestion("How do I set up the project?");
        AnswerPost studentAnswer = saveAnswer(post, student, "Clone it and open it in IntelliJ.", true, true);
        AnswerPost tutorAnswer = saveAnswer(post, tutor, "Clone it and run ./gradlew build.", true, true);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, tutorAnswer.getId()), tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        // Pyris merges every flagged message into the one stored answer. Both answers resolved the
        // thread, so both belong in it — a resolving answer is not worth less for being a peer's.
        assertThat(messageWithId(dto.thread(), "answer-" + studentAnswer.getId()).resolvesPost()).isTrue();
        assertThat(messageWithId(dto.thread(), "answer-" + tutorAnswer.getId()).resolvesPost()).isTrue();
        assertThat(messageWithId(dto.thread(), "answer-" + studentAnswer.getId()).content()).isEqualTo("Clone it and open it in IntelliJ.");
    }

    @Test
    void ingestion_isSkippedWhenTheQuestionAuthorOptedOutOfAi() {
        userUtilService.setAiSelectionDecision(student, AiSelectionDecision.NO_AI);
        userTestRepository.save(student);
        Post post = createQuestion("Please keep my question away from AI.");
        AnswerPost answer = saveAnswer(post, tutor, "Understood.", true, true);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        // The stored question is derived from the thread root, so the opt-out has to block the whole
        // entry, not just redact one message.
        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), managed, tutor, course);

        assertThat(captured.get()).isNull();
    }

    @Test
    void resolutionChanged_onlyResolvingAnswerIsOptedOut_retractsInsteadOfIngesting() {
        userUtilService.setAiSelectionDecision(tutor, AiSelectionDecision.NO_AI);
        userTestRepository.save(tutor);
        Post post = createQuestion("Who wrote this answer?");
        AnswerPost answer = saveAnswer(post, tutor, "A tutor who opted out.", true, true);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> ingested = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(ingested::set);
        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> retracted = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(retracted::set);

        // The answer itself would become the stored text, so redacting it is not an option and nothing may be
        // ingested. Doing nothing is not an option either: whatever the entry holds was written by an answer
        // that no longer resolves this thread, so it has to go.
        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), managed, tutor, course);

        assertThat(ingested.get()).isNull();
        assertThat(retracted.get()).isNotNull();
        assertThat(retracted.get().postId()).isEqualTo(String.valueOf(post.getId()));
    }

    @Test
    void ingestion_redactsAParticipantWhoOptedOutMidThread() {
        User bystander = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        userUtilService.setAiSelectionDecision(bystander, AiSelectionDecision.NO_AI);
        bystander = userTestRepository.save(bystander);

        Post post = createQuestion("Why does the build fail?");
        AnswerPost bystanderAnswer = saveAnswer(post, bystander, "I had the same problem in my private repo.", true, false);
        AnswerPost tutorAnswer = saveAnswer(post, tutor, "Run ./gradlew clean first.", true, true);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, tutorAnswer.getId()), tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        // A participant who is neither the question author nor the resolving author cannot block the
        // thread, but not one word of theirs may leave Artemis. The slot stays so the thread reads in
        // order — the same treatment PyrisPostDTO gives them on the autonomous tutor path.
        var redactedMessage = messageWithId(dto.thread(), "answer-" + bystanderAnswer.getId());
        assertThat(redactedMessage.redacted()).isTrue();
        // Asserted on the deserialized payload, so this is what Pyris actually receives: NON_EMPTY drops
        // the empty content from the JSON altogether, which is why the field is optional on the Pyris DTO.
        assertThat(redactedMessage.content()).isNullOrEmpty();
        assertThat(redactedMessage.resolvesPost()).isFalse();
        assertThat(redactedMessage.isVerifiedAnswer()).isFalse();
        // Everyone else is untouched, and the thread still carries its anchor.
        assertThat(messageWithId(dto.thread(), "answer-" + tutorAnswer.getId()).content()).isEqualTo("Run ./gradlew clean first.");
        assertThat(messageWithId(dto.thread(), "answer-" + tutorAnswer.getId()).isVerifiedAnswer()).isTrue();
        assertThat(messageWithId(dto.thread(), "post-" + post.getId()).content()).isEqualTo("Why does the build fail?");
    }

    // --- An edit to the text an entry was built from must reach Course Memory ---

    @Test
    void editingAResolvingAnswer_reingestsTheThread() {
        Post post = createQuestion("What is the deadline?");
        AnswerPost answer = saveAnswer(post, tutor, "Friday.", true, true);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        // resolvesPost unchanged, so this takes the content-only branch: before, nothing was dispatched
        // and the entry kept serving the wording the tutor had just corrected.
        userUtilService.changeUser(tutor.getLogin());
        answerMessageService.updateAnswerMessage(course.getId(), answer.getId(), new UpdatePostingDTO(answer.getId(), "Friday, 23:59 CET.", null, true));

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.postId()).isEqualTo(String.valueOf(post.getId()));
        assertThat(messageWithId(dto.thread(), "answer-" + answer.getId()).content()).isEqualTo("Friday, 23:59 CET.");
    }

    @Test
    void editingANonContributingAnswer_doesNotReingest() {
        Post post = createQuestion("Any tips for the exercise?");
        saveAnswer(post, tutor, "Read the task description carefully.", true, true);
        AnswerPost chatter = saveAnswer(post, tutor, "Good luck everyone.", true, false);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set, ExpectedCount.max(1));

        // The edited answer neither resolves the thread nor is a verified Iris answer, so no entry was
        // built from it and nothing should be dispatched.
        userUtilService.changeUser(tutor.getLogin());
        answerMessageService.updateAnswerMessage(course.getId(), chatter.getId(), new UpdatePostingDTO(chatter.getId(), "Good luck to everyone.", null, false));

        // Asserted on the captured request rather than left to the mock server: updateAnswerMessage logs
        // and swallows webhook failures, so an unexpected dispatch would never surface as a test failure.
        assertThat(captured.get()).isNull();
    }

    @Test
    void editingTheQuestionOfAResolvedThread_reingestsTheThread() {
        Post post = createQuestion("How do I run it?");
        saveAnswer(post, tutor, "Use ./gradlew bootRun.", true, true);
        post.setResolved(true);
        Post resolvedPost = conversationMessageRepository.save(post);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        // The stored question is derived from the root post, so editing it has to re-extract the entry.
        userUtilService.changeUser(student.getLogin());
        conversationMessagingService.updateMessage(course.getId(), resolvedPost.getId(),
                new UpdatePostingDTO(resolvedPost.getId(), "How do I run the server locally?", null, false));

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(messageWithId(dto.thread(), "post-" + resolvedPost.getId()).content()).isEqualTo("How do I run the server locally?");
    }

    @Test
    void editingTheQuestionOfAnUnresolvedThread_doesNotReingest() {
        Post post = createQuestion("Still unanswered?");
        saveAnswer(post, tutor, "Looking into it.", true, false);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set, ExpectedCount.max(1));

        // Nothing resolves the thread, so it has no entry to refresh — no webhook expected.
        userUtilService.changeUser(student.getLogin());
        conversationMessagingService.updateMessage(course.getId(), post.getId(), new UpdatePostingDTO(post.getId(), "Still unanswered, sorry for the bump.", null, false));

        // updateMessage swallows webhook failures too, so the absence of a dispatch has to be asserted.
        assertThat(captured.get()).isNull();
    }

    // --- A channel that stops being an eligible source must take its entries with it ---

    @Test
    void togglingPrivacyOfACourseWideChannel_keepsItsEntries() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(captured::set);

        request.postWithoutResponseBody("/api/communication/courses/" + course.getId() + "/channels/" + channel.getId() + "/toggle-privacy", HttpStatus.OK,
                new LinkedMultiValueMap<>());

        // Eligibility is isPublic OR isCourseWide, so a course-wide channel stays readable by the whole
        // course whatever this flag says — retracting its entries here would drop valid memory.
        assertThat(conversationRepository.findById(channel.getId())).get().asInstanceOf(type(Channel.class)).extracting(Channel::getIsPublic).isEqualTo(false);
        assertThat(captured.get()).isNull();
    }

    @Test
    void togglingPrivacyOfAPlainPublicChannel_deletesItsEntries() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        Channel publicChannel = conversationUtilService.createPublicChannel(course, "public-for-privacy-toggle");

        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(captured::set);

        request.postWithoutResponseBody("/api/communication/courses/" + course.getId() + "/channels/" + publicChannel.getId() + "/toggle-privacy", HttpStatus.OK,
                new LinkedMultiValueMap<>());

        // Nothing else keeps this one readable, so everything mined from it while it was public has to go.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.conversationId()).isEqualTo(String.valueOf(publicChannel.getId()));
    }

    @Test
    void channelNoLongerEligible_deletesEveryEntryOfThatChannel() {
        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleChannelNoLongerEligible(channel, tutor, course);

        // Scoped to the channel, not a single thread: eligibility is only evaluated when an entry is
        // written, so everything mined from this channel has to go at once.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.conversationId()).isEqualTo(String.valueOf(channel.getId()));
        assertThat(dto.postId()).isNull();
        assertThat(dto.courseId()).isEqualTo(course.getId());
        // Spans threads whose ids are not known up front, so there is no per-thread version to send.
        assertThat(dto.version()).isNull();
    }

    @Test
    void channelNoLongerEligible_isSkippedWhenIrisIsDisabled() {
        disableIrisFor(course);

        // No webhook mock registered: a request would fail the MockRestServiceServer.
        courseMemoryIngestionService.handleChannelNoLongerEligible(channel, tutor, course);
    }

    @Test
    void deletingAConversationPurgesItsEntries() {
        Channel doomed = conversationUtilService.createPublicChannel(course, "channel-deleted-directly");

        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(captured::set);

        // The purge hangs off ConversationService rather than off any one caller, so every route that
        // deletes a channel — a plain channel delete, an exercise's channel going away with its exercise,
        // a tutorial group's channel — retracts its entries without having to remember to.
        conversationService.deleteConversation(doomed.getId());

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.conversationId()).isEqualTo(String.valueOf(doomed.getId()));
        assertThat(dto.postId()).isNull();
        assertThat(dto.wholeCourse()).isFalse();
    }

    @Test
    void deletingAnExercisesChannelPurgesItsEntries() {
        TextExercise exercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1),
                ZonedDateTime.now().plusDays(2));
        Channel exerciseChannel = conversationUtilService.addChannelToExercise(exercise);

        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(captured::set);

        // The sharp case: the course keeps running, so without this Iris goes on serving answers mined
        // from a channel that no longer exists, citing links that 404.
        channelService.deleteChannelForExerciseId(exercise.getId());

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.conversationId()).isEqualTo(String.valueOf(exerciseChannel.getId()));
    }

    @Test
    void courseDeletion_purgesEveryEntryOfTheCourse() {
        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleCourseDeleted(course, tutor);

        // Course deletion drops every conversation in one bulk statement, so there is no channel id left to
        // purge one by one — and afterwards no Artemis object survives that could ever ask for these
        // entries' removal.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.courseId()).isEqualTo(course.getId());
        assertThat(dto.wholeCourse()).isTrue();
        assertThat(dto.postId()).isNull();
        assertThat(dto.conversationId()).isNull();
        assertThat(dto.version()).isNull();
    }

    @Test
    void threadDeletion_stillTargetsASingleThread() {
        Post post = createQuestion("Does a thread deletion stay narrow?");
        saveAnswer(post, tutor, "It should.", true, false);

        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleThreadDeleted(reloadPost(post), tutor, course);

        // Pyris rejects a deletion carrying both scopes, so the thread path must leave conversationId unset.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.postId()).isEqualTo(String.valueOf(post.getId()));
        assertThat(dto.conversationId()).isNull();
    }

    @Test
    void ingestion_downgradesToLocalWhenAnyParticipantChoseLocal() {
        User secondStudent = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        userUtilService.setAiSelectionDecision(secondStudent, AiSelectionDecision.LOCAL_AI);
        secondStudent = userTestRepository.save(secondStudent);

        Post post = createQuestion("Does the extractor run on-premise?");
        saveAnswer(post, secondStudent, "I would like it to.", true, false);
        AnswerPost tutorAnswer = saveAnswer(post, tutor, "It depends on the thread.", true, true);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, tutorAnswer.getId()), tutor, course);

        // Ingestion sends the whole transcript to an extraction model, so it answers "which model may
        // see this thread" exactly as the autonomous tutor run does: one local participant pins it.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.settings().selection()).isEqualTo(AiSelectionDecision.LOCAL_AI);
    }

    @Test
    void ingestion_staysCloudWhenNoParticipantChoseLocal() {
        Post post = createQuestion("Any preference here?");
        AnswerPost tutorAnswer = saveAnswer(post, tutor, "None recorded.", true, true);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, tutorAnswer.getId()), tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.settings().selection()).isEqualTo(AiSelectionDecision.CLOUD_AI);
    }

    @Test
    void ingestion_clearsResolvingFlagOfARedactedAnswer() {
        User bystander = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        userUtilService.setAiSelectionDecision(bystander, AiSelectionDecision.NO_AI);
        bystander = userTestRepository.save(bystander);

        Post post = createQuestion("Which Java version do we use?");
        AnswerPost redactedResolver = saveAnswer(post, bystander, "Java 25, see the README.", true, true);
        AnswerPost tutorAnswer = saveAnswer(post, tutor, "Java 25.", true, true);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, tutorAnswer.getId()), tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        // Pyris merges every flagged message into the single stored answer. Leaving the flag on a
        // redacted message would splice the placeholder into the answer served to future students.
        assertThat(messageWithId(dto.thread(), "answer-" + redactedResolver.getId()).resolvesPost()).isFalse();
        assertThat(messageWithId(dto.thread(), "answer-" + tutorAnswer.getId()).resolvesPost()).isTrue();
    }

    @Test
    void retraction_stillWorksForAnOptedOutQuestionAuthor() {
        userUtilService.setAiSelectionDecision(student, AiSelectionDecision.NO_AI);
        userTestRepository.save(student);
        Post post = createQuestion("Opted out after an entry already existed.");
        AnswerPost answer = saveAnswer(post, tutor, "No longer resolving.", true, false);

        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, answer.getId()), tutor, course);

        // Deletion must stay reachable: an entry written before the author opted out has to be removable.
        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().postId()).isEqualTo(String.valueOf(post.getId()));
    }

    @Test
    void resolutionChanged_severalResolvingAnswers_flagsAllAndKeepsOnePostId() {
        Post post = createQuestion("How do I run the tests locally?");
        AnswerPost first = saveAnswer(post, tutor, "Use ./gradlew test.", true, true);
        AnswerPost second = saveAnswer(post, tutor, "You also need Docker running.", true, true);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, second.getId()), tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        // Both resolving answers are flagged, so the extractor merges them instead of being told to
        // ignore all but one; the entry is keyed on the thread so there is still only one of them.
        assertThat(dto.postId()).isEqualTo(String.valueOf(post.getId()));
        assertThat(messageWithId(dto.thread(), "answer-" + first.getId()).resolvesPost()).isTrue();
        assertThat(messageWithId(dto.thread(), "answer-" + second.getId()).resolvesPost()).isTrue();
        // Exactly one anchor: the answer whose flag just changed.
        assertThat(dto.thread().stream().filter(PyrisCourseMemoryThreadMessageDTO::isVerifiedAnswer).toList()).singleElement()
                .satisfies(message -> assertThat(message.id()).isEqualTo("answer-" + second.getId()));
    }

    @Test
    void resolutionChanged_lastResolvingAnswerUnmarked_deletesThreadEntry() {
        Post post = createQuestion("Is attendance mandatory?");
        // Already un-marked in the database, mirroring the state after the flag was toggled back off.
        AnswerPost answer = saveAnswer(post, tutor, "No, it is optional.", true, false);

        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, answer.getId()), tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.postId()).isEqualTo(String.valueOf(post.getId()));
        assertThat(dto.courseId()).isEqualTo(course.getId());
        assertThat(dto.settings().authenticationToken()).isNotNull();
        // The retraction is ordered against the thread's ingestions by the version minted for it.
        assertThat(dto.version()).isEqualTo(conversationMessageRepository.findCourseMemoryVersion(post.getId()).orElseThrow());
    }

    @Test
    void resolutionChanged_oneOfTwoResolversUnmarked_reingestsRatherThanDeletes() {
        Post post = createQuestion("Do I need to register for the exam?");
        AnswerPost stillResolving = saveAnswer(post, tutor, "Yes, via TUMonline.", true, true);
        AnswerPost unmarked = saveAnswer(post, tutor, "Ignore my earlier note.", true, false);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, unmarked.getId()), tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        // The thread still holds a resolving answer, so the entry is refreshed from it, not deleted.
        assertThat(dto.thread().stream().filter(PyrisCourseMemoryThreadMessageDTO::isVerifiedAnswer).toList()).singleElement()
                .satisfies(message -> assertThat(message.id()).isEqualTo("answer-" + stillResolving.getId()));
    }

    @Test
    void resolutionChanged_verifiedIrisAnswerSurvivesUnmarking_isNotDeleted() {
        Post post = createQuestion("What does CI stand for?");
        AnswerPost irisAnswer = saveDashboardVerifiedIrisAnswer(post, "Continuous Integration.", false);
        AnswerPost humanAnswer = saveAnswer(post, student, "Also see the glossary.", true, false);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        // A tutor-verified Iris answer keeps the thread memory-worthy, so the entry must not be retracted
        // just because an unrelated answer was un-marked.
        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, humanAnswer.getId()), student, course);

        // Rebuilt from the Iris answer rather than left as it stood: the entry is keyed on the thread, so
        // it may hold what a since-retracted staff answer wrote.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.IRIS_AUTO);
        assertThat(dto.messageId()).isEqualTo(String.valueOf(irisAnswer.getId()));
    }

    @Test
    void resolutionChanged_anAutoPublishedIrisAnswerDoesNotHideAnOlderVerifiedOne() {
        Post post = createQuestion("What does CD stand for?");
        AnswerPost verifiedIrisAnswer = saveDashboardVerifiedIrisAnswer(post, "Continuous Delivery.", false);
        // Auto-published: isVerified() like the one above, but with no human verifier behind it — and newer.
        // Testing only the newest bot answer for a verifier would make the anchor come out empty and the
        // caller retract an entry the tutor-approved answer above still owns.
        saveAnswer(post, botUser, "It can also mean Continuous Deployment.", true, false);
        AnswerPost humanAnswer = saveAnswer(post, student, "See the glossary.", false, false);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, humanAnswer.getId()), student, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.messageId()).isEqualTo(String.valueOf(verifiedIrisAnswer.getId()));
    }

    @Test
    void resolutionChanged_anOptedOutResolverDoesNotDisplaceAnOlderUsableOne() {
        // selectAnchor takes the newest resolving answer, so an opted-out author answering after a tutor used to
        // win the anchor — and the run then stopped, leaving the tutor's entry standing for an answer that no
        // longer owned it. The opted-out answer is not a candidate at all, so the tutor's still is.
        User bystander = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        userUtilService.setAiSelectionDecision(bystander, AiSelectionDecision.NO_AI);
        bystander = userTestRepository.save(bystander);

        Post post = createQuestion("Which branch do I base my work on?");
        AnswerPost tutorAnswer = saveAnswer(post, tutor, "Always branch off develop.", true, true);
        saveAnswer(post, bystander, "That matches what I did.", true, true);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), null, tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.messageId()).isEqualTo(String.valueOf(tutorAnswer.getId()));
    }

    @Test
    void resolutionChanged_dashboardVerifiedIrisAnswer_isReingestedUnderVerificationProvenance() {
        Post post = createQuestion("What is a merge conflict?");
        AnswerPost answer = saveDashboardVerifiedIrisAnswer(post, "It happens when two branches change the same lines.", true);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), managed, tutor, course);

        // Trigger A owns this answer, so the run keeps its provenance and its verifier instead of being
        // relabelled after whoever triggered this pass — but it still has to be dispatched, or a stale
        // entry written by another answer would survive untouched.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.IRIS_AUTO);
        assertThat(dto.verifiedBy()).isEqualTo(tutor.getLogin());
        // Uncorrected, but still a tutor's sign-off on this exact text, so it travels verbatim.
        assertThat(dto.existingAnswer()).isEqualTo("It happens when two branches change the same lines.");
    }

    @Test
    void resolutionChanged_editedVerifiedIrisAnswer_isReingestedAsCorrectedWithTheEditVerbatim() {
        Post post = createQuestion("When is the exam?");
        AnswerPost answer = saveDashboardVerifiedIrisAnswer(post, "Some time in March.", true);
        // The tutor corrected the draft, which is what an update after creation means for a bot answer.
        answer.setContent("On 14 March, 10:00, in MW 2001.");
        answer.setUpdatedDate(ZonedDateTime.now());
        answerPostRepository.save(answer);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, answer.getId()), tutor, course);

        // The corrected text travels as existingAnswer so extraction cannot paraphrase the tutor's wording
        // away, exactly as Trigger A passes it.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.IRIS_CORRECTED);
        assertThat(dto.existingAnswer()).isEqualTo("On 14 March, 10:00, in MW 2001.");
    }

    @Test
    void resolutionChanged_autoPostedIrisAnswerMarkedByTutor_isIngestedAsIrisAuto() {
        Post post = createQuestion("What is a merge conflict?");
        // Published automatically on a high confidence score: verified, but with no human reviewer, so it
        // never passed through the dashboard and Trigger A never fired for it.
        AnswerPost answer = saveResolvingAnswer(post, botUser, "It happens when two branches change the same lines.", true, tutor);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), managed, tutor, course);

        // The tutor marking it resolving is the first and only sign-off the answer ever gets; without
        // this path an auto-posted answer could never reach course memory at all.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.IRIS_AUTO);
        assertThat(dto.verifiedBy()).isEqualTo(tutor.getLogin());
        assertThat(messageWithId(dto.thread(), "answer-" + answer.getId()).isIrisDraft()).isTrue();
        // The tutor signed off on exactly the text they read, so it travels verbatim like a dashboard approval;
        // Pyris rejects IRIS_AUTO without it rather than store an extractor's paraphrase as tutor-approved.
        assertThat(dto.existingAnswer()).isEqualTo("It happens when two branches change the same lines.");
    }

    @Test
    void resolutionChanged_autoPostedIrisAnswerMarkedByStudent_isCommunityResolved() {
        Post post = createQuestion("What does a rebase do?");
        AnswerPost answer = saveResolvingAnswer(post, botUser, "It replays your commits on top of another branch.", true, student);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), managed, student, course);

        // A student accepting an AI answer is not a tutor endorsement, so it must not be labelled as one.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.THREAD_RESOLVED);
        assertThat(dto.verifiedBy()).isNull();
    }

    @Test
    void resolutionChanged_unverifiedIrisDraftIsExcludedFromThread() {
        Post post = createQuestion("How do I reset my password?");
        saveAnswer(post, botUser, "Unapproved draft that students cannot see.", false, false);
        AnswerPost tutorAnswer = saveAnswer(post, tutor, "Use the forgot-password link.", true, true);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, tutorAnswer.getId()), tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.thread()).hasSize(2);
        assertThat(dto.thread()).noneMatch(PyrisCourseMemoryThreadMessageDTO::isIrisDraft);
    }

    @Test
    void threadDeleted_deletesThreadEntry() {
        Post post = createQuestion("Will this thread be removed?");
        saveAnswer(post, tutor, "Yes it will.", true, true);

        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleThreadDeleted(reloadPost(post), tutor, course);

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().postId()).isEqualTo(String.valueOf(post.getId()));
    }

    // --- Websocket status reported to the acting user ---

    private String courseMemoryTopic() {
        return CourseMemoryIngestionService.COURSE_MEMORY_TOPIC_PREFIX + course.getId();
    }

    private ArgumentMatcher<Object> status(CourseMemoryOperation operation, CourseMemoryStage stage, Post post) {
        return payload -> payload instanceof IrisCourseMemoryStatusDTO dto && dto.operation() == operation && dto.stage() == stage
                && dto.postId().equals(String.valueOf(post.getId()));
    }

    @Test
    void resolutionChanged_pushesTriggeredToTheMarker() {
        Post post = createQuestion("Does resolving notify me?");
        AnswerPost answer = saveAnswer(post, tutor, "It should.", true, true);

        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(dto -> {
        });

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, answer.getId()), tutor, course);

        verifyMessageWasSentOverWebsocket(tutor.getLogin(), courseMemoryTopic(), status(CourseMemoryOperation.INGEST, CourseMemoryStage.TRIGGERED, post));
    }

    @Test
    void retraction_pushesDeleteTriggeredToTheMarker() {
        Post post = createQuestion("Does un-resolving notify me?");
        AnswerPost answer = saveAnswer(post, tutor, "No longer resolving.", true, false);

        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(dto -> {
        });

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, answer.getId()), tutor, course);

        verifyMessageWasSentOverWebsocket(tutor.getLogin(), courseMemoryTopic(), status(CourseMemoryOperation.DELETE, CourseMemoryStage.TRIGGERED, post));
    }

    @Test
    void skippedIngestion_pushesNothing() {
        // The whole reason TRIGGERED is server-pushed: a client-side toast would announce an ingestion
        // in a case like this one, which dispatches nothing at all.
        Channel privateChannel = conversationUtilService.createPublicChannel(course, "private-for-status");
        privateChannel.setIsPublic(false);
        privateChannel = conversationRepository.save(privateChannel);
        Post privatePost = new Post();
        privatePost.setAuthor(student);
        privatePost.setContent("Private thread");
        privatePost.setConversation(privateChannel);
        privatePost.setVisibleForStudents(true);
        Post savedPrivatePost = conversationMessageRepository.save(privatePost);
        AnswerPost privateAnswer = saveAnswer(savedPrivatePost, tutor, "Not ingested.", true, true);
        courseMemoryIngestionService.handleResolutionChange(reloadPost(savedPrivatePost), reloadManagedAnswer(savedPrivatePost, privateAnswer.getId()), tutor, course);

        verifyNumberOfCallsToWebsocket(tutor.getLogin(), courseMemoryTopic(), 0);
    }

    @Test
    void statusUpdate_finishedPushesCompleted_runningPushesNothing() {
        Post post = createQuestion("Does completion notify me?");
        String jobToken = pyrisJobService.addCourseMemoryIngestionWebhookJob(course.getId(), String.valueOf(channel.getId()), String.valueOf(post.getId()), "answer-1",
                tutor.getLogin(), CourseMemoryOperation.INGEST);
        var job = (CourseMemoryIngestionWebhookJob) pyrisJobService.getJob(jobToken);

        // Pyris emits several RUNNING updates per run; each would otherwise raise its own toast.
        pyrisStatusUpdateService.handleStatusUpdate(job, new PyrisCourseMemoryIngestionStatusUpdateDTO(null, PyrisRunState.RUNNING, null, null));
        verifyNumberOfCallsToWebsocket(tutor.getLogin(), courseMemoryTopic(), 0);

        pyrisStatusUpdateService.handleStatusUpdate(job, new PyrisCourseMemoryIngestionStatusUpdateDTO(null, PyrisRunState.FINISHED, null, null));
        verifyMessageWasSentOverWebsocket(tutor.getLogin(), courseMemoryTopic(), status(CourseMemoryOperation.INGEST, CourseMemoryStage.COMPLETED, post));
    }

    @Test
    void dispatchFailure_pushesFailedAndReleasesTheJob() {
        Post post = createQuestion("What if Pyris cannot be reached?");
        AnswerPost answer = saveAnswer(post, tutor, "An answer nobody will store.", true, true);
        AtomicReference<String> jobToken = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunError(dto -> jobToken.set(dto.settings().authenticationToken()), HttpStatus.INTERNAL_SERVER_ERROR.value());

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, answer.getId()), tutor, course);

        // TRIGGERED was already pushed, and Pyris never took the request, so nothing else will ever close this run
        // out: Artemis has to report the failure itself.
        verifyMessageWasSentOverWebsocket(tutor.getLogin(), courseMemoryTopic(),
                payload -> payload instanceof IrisCourseMemoryStatusDTO dto && dto.stage() == CourseMemoryStage.FAILED);
        // And drop the job rather than leaving a token nothing can redeem to sit out the ingestion TTL.
        assertThat(jobToken.get()).isNotNull();
        assertThat(pyrisJobService.getJob(jobToken.get())).isNull();
    }

    @Test
    void statusUpdate_failedPushesFailedWithMessage() {
        Post post = createQuestion("Does failure notify me?");
        String jobToken = pyrisJobService.addCourseMemoryIngestionWebhookJob(course.getId(), String.valueOf(channel.getId()), String.valueOf(post.getId()), "answer-1",
                tutor.getLogin(), CourseMemoryOperation.DELETE);
        var job = (CourseMemoryIngestionWebhookJob) pyrisJobService.getJob(jobToken);

        pyrisStatusUpdateService.handleStatusUpdate(job, new PyrisCourseMemoryIngestionStatusUpdateDTO(null, PyrisRunState.FAILED, new PyrisStatusErrorDTO("boom", null), null));

        verifyMessageWasSentOverWebsocket(tutor.getLogin(), courseMemoryTopic(), payload -> payload instanceof IrisCourseMemoryStatusDTO dto
                && dto.operation() == CourseMemoryOperation.DELETE && dto.stage() == CourseMemoryStage.FAILED && "boom".equals(dto.errorMessage()));
    }

    @Test
    void statusUpdate_withoutActorPushesNothing() {
        Post post = createQuestion("Anonymous trigger");
        String jobToken = pyrisJobService.addCourseMemoryIngestionWebhookJob(course.getId(), String.valueOf(channel.getId()), String.valueOf(post.getId()), "answer-1", null,
                CourseMemoryOperation.INGEST);
        var job = (CourseMemoryIngestionWebhookJob) pyrisJobService.getJob(jobToken);

        // A run without a known actor must still complete; it just reports to nobody.
        pyrisStatusUpdateService.handleStatusUpdate(job, new PyrisCourseMemoryIngestionStatusUpdateDTO(null, PyrisRunState.FINISHED, null, null));

        verifyNumberOfCallsToWebsocket(tutor.getLogin(), courseMemoryTopic(), 0);
    }

    @Test
    void resolutionChanged_privateChannel_isSkipped() {
        Channel privateChannel = conversationUtilService.createPublicChannel(course, "private-ish");
        privateChannel.setIsPublic(false);
        privateChannel = conversationRepository.save(privateChannel);
        Post post = new Post();
        post.setAuthor(student);
        post.setContent("Is this private thread ingested?");
        post.setConversation(privateChannel);
        post.setVisibleForStudents(true);
        Post savedPost = conversationMessageRepository.save(post);
        AnswerPost answer = saveAnswer(savedPost, tutor, "It should not be ingested.", true, true);
        AnswerPost managed = reloadManagedAnswer(savedPost, answer.getId());

        // Not a public/course-wide channel -> no webhook expected (a stray request would fail the mock server)
        courseMemoryIngestionService.handleResolutionChange(reloadPost(savedPost), managed, tutor, course);
    }

    // --- The trust tier follows the endorser recorded on the anchoring answer, not the acting user ---

    @Test
    void resolutionChanged_tutorUnmarksWhileAStudentEndorsedAnswerStands_staysCommunityResolved() {
        Post post = createQuestion("Is the retake exam open book?");
        // Endorsed by the post author, a student: nobody with authority has checked it.
        AnswerPost studentEndorsed = saveResolvingAnswer(post, student, "I think so, a friend told me.", true, student);
        // The tutor's own answer, already un-marked in the database, mirroring the state after the toggle.
        AnswerPost unmarked = saveAnswer(post, tutor, "Retracting this, I was wrong.", true, false);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, unmarked.getId()), tutor, course);

        // The tutor is the actor of this refresh, not the endorser of the answer that now anchors the entry.
        // Labelling it after the actor would store a student's guess about the exam as tutor-verified.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.messageId()).isEqualTo(String.valueOf(studentEndorsed.getId()));
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.THREAD_RESOLVED);
        assertThat(dto.verifiedBy()).isNull();
        assertThat(dto.verifiedAt()).isNull();
    }

    @Test
    void resolutionChanged_studentUnmarksWhileATutorEndorsedAnswerStands_keepsTheEndorsersProvenance() {
        Post post = createQuestion("Which deadline counts, the calendar or the exercise page?");
        AnswerPost tutorEndorsed = saveResolvingAnswer(post, student, "The exercise page.", true, tutor);
        AnswerPost unmarked = saveAnswer(post, student, "Never mind, found it.", true, false);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, unmarked.getId()), student, course);

        // The student is merely the actor; the surviving answer carries a tutor's endorsement and keeps it, with
        // the verifier and timestamp of that endorsement rather than of this refresh.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.messageId()).isEqualTo(String.valueOf(tutorEndorsed.getId()));
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.TUTOR_WRITTEN);
        assertThat(dto.verifiedBy()).isEqualTo(tutor.getLogin());
        ZonedDateTime endorsedAt = answerPostRepository.findById(tutorEndorsed.getId()).orElseThrow().getResolvedAt();
        assertThat(dto.verifiedAt()).isEqualTo(endorsedAt.toInstant().toString());
    }

    @Test
    void editingTheQuestion_doesNotUpgradeAStudentResolvedThread() {
        Post post = createQuestion("Whre are the slides?");
        saveResolvingAnswer(post, student, "On the lecture page.", true, student);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        // What the question-edit path calls: no triggering answer, and the editor — here a tutor fixing a typo —
        // as the actor. Fixing a typo is not an endorsement of the answer below it.
        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), null, tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.THREAD_RESOLVED);
        assertThat(dto.verifiedBy()).isNull();
    }

    @Test
    void resolutionChanged_studentMarksASecondAnswer_keepsTheTutorEndorsedAnchor() {
        Post post = createQuestion("Do we need to register for the exam separately?");
        AnswerPost tutorEndorsed = saveResolvingAnswer(post, tutor, "Yes, via TUMonline until the 15th.", true, tutor);
        AnswerPost studentEndorsed = saveResolvingAnswer(post, student, "I did not have to.", true, student);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, studentEndorsed.getId()), student, course);

        // Pyris applies the latest state Artemis sends, so the anchor has to be chosen by trust tier first: the
        // newer student-endorsed answer must not demote the thread's tutor-verified entry, even though it is the
        // one whose flag just changed. It still travels flagged, so the extractor sees it.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.messageId()).isEqualTo(String.valueOf(tutorEndorsed.getId()));
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.TUTOR_WRITTEN);
        assertThat(dto.verifiedBy()).isEqualTo(tutor.getLogin());
        assertThat(messageWithId(dto.thread(), "answer-" + tutorEndorsed.getId()).isVerifiedAnswer()).isTrue();
        assertThat(messageWithId(dto.thread(), "answer-" + studentEndorsed.getId()).resolvesPost()).isTrue();
    }

    @Test
    void resolutionChanged_resolvingAnswerWithoutARecordedEndorser_isCommunityResolved() {
        Post post = createQuestion("Resolved before endorsers were recorded?");
        // resolvesPost without resolvedBy: the state of every answer resolved before the endorsement columns existed.
        AnswerPost legacy = saveAnswer(post, tutor, "Yes.", true, true);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, legacy.getId()), tutor, course);

        // Nobody is on record as having endorsed it, so it fails closed into the community tier — even though a
        // tutor wrote it and a tutor triggered this refresh.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.THREAD_RESOLVED);
        assertThat(dto.verifiedBy()).isNull();
    }

    @Test
    void markingAnAnswerResolving_recordsTheEndorserAndUnmarkingClearsIt() {
        Post post = createQuestion("Who endorsed this?");
        AnswerPost answer = saveAnswer(post, student, "Me, apparently.", true, false);
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(dto -> {
        });
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(dto -> {
        });

        userUtilService.changeUser(tutor.getLogin());
        answerMessageService.updateAnswerMessage(course.getId(), answer.getId(), new UpdatePostingDTO(answer.getId(), "Me, apparently.", null, true));

        assertThat(answerPostRepository.findResolvingAnswerEndorsersByPostId(post.getId())).singleElement()
                .satisfies(endorser -> assertThat(endorser.endorserLogin()).isEqualTo(tutor.getLogin()));
        assertThat(answerPostRepository.findById(answer.getId()).orElseThrow().getResolvedAt()).isNotNull();

        answerMessageService.updateAnswerMessage(course.getId(), answer.getId(), new UpdatePostingDTO(answer.getId(), "Me, apparently.", null, false));

        // Un-marking clears the endorsement: a later re-mark is a fresh endorsement by whoever makes it.
        assertThat(answerPostRepository.findResolvingAnswerEndorsersByPostId(post.getId())).isEmpty();
        assertThat(answerPostRepository.findById(answer.getId()).orElseThrow().getResolvedAt()).isNull();
    }

    // --- Every thread-scoped operation carries a version Pyris orders it by ---

    @Test
    void successiveOperationsOnAThread_carryStrictlyIncreasingVersions() {
        Post post = createQuestion("Is attendance mandatory?");
        AnswerPost answer = saveResolvingAnswer(post, tutor, "No.", true, tutor);

        // Both expectations up front: the mock server refuses new ones once a request has been made.
        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> ingested = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(ingested::set);
        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> retracted = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(retracted::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, answer.getId()), tutor, course);

        // The answer is un-marked, so the next event retracts the entry.
        answer.setResolution(false, null);
        answerPostRepository.save(answer);
        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, answer.getId()), tutor, course);

        // Pyris keeps the highest version per thread and drops anything older, so the retraction has to outrank
        // the ingestion it supersedes — and the counter it was minted from has to say the same.
        assertThat(ingested.get().version()).isPositive();
        assertThat(retracted.get().version()).isEqualTo(ingested.get().version() + 1);
        assertThat(conversationMessageRepository.findCourseMemoryVersion(post.getId())).contains(retracted.get().version());
    }

    @Test
    void verificationAndResolution_shareOneVersionCounterPerThread() {
        Post post = createQuestion("Does Trigger A share the counter?");
        AnswerPost irisAnswer = saveDashboardVerifiedIrisAnswer(post, "It has to.", false);
        AnswerPost humanAnswer = saveResolvingAnswer(post, tutor, "Confirmed.", true, tutor);

        List<Long> versions = new ArrayList<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(dto -> versions.add(dto.version()), ExpectedCount.times(2));
        courseMemoryIngestionService.ingestVerifiedAnswer(reloadManagedAnswer(post, irisAnswer.getId()), false, tutor, course);
        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, humanAnswer.getId()), tutor, course);

        // Both triggers write the same thread entry, so they must be ordered against each other, not each on its own.
        assertThat(versions).hasSize(2);
        assertThat(versions.get(1)).isEqualTo(versions.get(0) + 1);
    }

    @Test
    void threadDeletion_carriesTheFinalVersion() {
        Post post = createQuestion("Will anything follow my deletion?");
        saveResolvingAnswer(post, tutor, "Nothing can.", true, tutor);

        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleThreadDeleted(reloadPost(post), tutor, course);

        // The row is gone by the time this fires in production, so no version can be minted for it; the maximum
        // value is a tombstone no ingestion still in flight can ever outrank.
        assertThat(captured.get().version()).isEqualTo(Long.MAX_VALUE);
    }
}
