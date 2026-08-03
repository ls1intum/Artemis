package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationMessageRepository;
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

class CourseMemoryIngestionIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "coursememingest";

    @Autowired
    private CourseMemoryIngestionService courseMemoryIngestionService;

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
        // The opt-out tests below persist this flag and users are shared across methods in the class,
        // so reset it here to keep the tests order-independent.
        student.setSelectedLLMUsage(null);
        tutor.setSelectedLLMUsage(null);
        student = userTestRepository.save(student);
        tutor = userTestRepository.save(tutor);
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
        assertThat(dto.existingAnswer()).isNull();
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
        AnswerPost answer = saveAnswer(post, tutor, "The latest push before the deadline is graded.", true, true);
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
        AnswerPost answer = saveAnswer(post, tutor, "Java 25.", true, true);
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
    void resolutionChanged_studentAuthoredAnswer_isSkipped() {
        Post post = createQuestion("Where do I find the slides?");
        AnswerPost answer = saveAnswer(post, student, "They are on the lecture page.", true, true);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        // A student marking an answer resolving does not make it correct, so no webhook is expected
        // (a stray request would fail the mock server).
        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), managed, student, course);
    }

    @Test
    void resolutionChanged_studentAnswerMarkedByTutor_isStillSkipped() {
        Post post = createQuestion("Is the exam open book?");
        AnswerPost answer = saveAnswer(post, student, "I think so.", true, true);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        // Even a tutor endorsing it does not turn a student's text into staff-written content; the
        // tutor is expected to write their own answer instead.
        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), managed, tutor, course);
    }

    @Test
    void resolutionChanged_studentResolvingAnswerIsNotFlaggedInTheThread() {
        Post post = createQuestion("How do I set up the project?");
        AnswerPost studentAnswer = saveAnswer(post, student, "Just clone it, I guess.", true, true);
        AnswerPost tutorAnswer = saveAnswer(post, tutor, "Clone it and run ./gradlew build.", true, true);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, tutorAnswer.getId()), tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        // Pyris merges every flagged message into the one stored answer, so leaving the student's
        // resolving answer flagged would splice unreviewed text into a tutor-verified entry.
        assertThat(messageWithId(dto.thread(), "answer-" + studentAnswer.getId()).resolvesPost()).isFalse();
        assertThat(messageWithId(dto.thread(), "answer-" + tutorAnswer.getId()).resolvesPost()).isTrue();
        // It still travels as context the extractor may read but never quote as the answer.
        assertThat(messageWithId(dto.thread(), "answer-" + studentAnswer.getId()).content()).isEqualTo("Just clone it, I guess.");
    }

    @Test
    void ingestion_isSkippedWhenTheQuestionAuthorOptedOutOfAi() {
        student.setSelectedLLMUsage(AiSelectionDecision.NO_AI);
        userTestRepository.save(student);
        Post post = createQuestion("Please keep my question away from AI.");
        AnswerPost answer = saveAnswer(post, tutor, "Understood.", true, true);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        // The stored question is derived from the thread root, so the opt-out has to block the whole
        // entry, not just redact one message.
        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), managed, tutor, course);
    }

    @Test
    void ingestion_isSkippedWhenTheAnswerAuthorOptedOutOfAi() {
        tutor.setSelectedLLMUsage(AiSelectionDecision.NO_AI);
        userTestRepository.save(tutor);
        Post post = createQuestion("Who wrote this answer?");
        AnswerPost answer = saveAnswer(post, tutor, "A tutor who opted out.", true, true);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), managed, tutor, course);
    }

    @Test
    void retraction_stillWorksForAnOptedOutQuestionAuthor() {
        student.setSelectedLLMUsage(AiSelectionDecision.NO_AI);
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
        saveAnswer(post, botUser, "Continuous Integration.", true, false);
        AnswerPost humanAnswer = saveAnswer(post, student, "Also see the glossary.", true, false);

        // A verified Iris answer keeps the thread memory-worthy, so Trigger A's entry must not be
        // retracted just because an unrelated answer was un-marked. Iris-authored anchors belong to
        // the verification trigger, so nothing is sent at all here.
        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), reloadManagedAnswer(post, humanAnswer.getId()), student, course);
    }

    @Test
    void resolutionChanged_irisAnswer_isSkipped() {
        Post post = createQuestion("What is a merge conflict?");
        AnswerPost answer = saveAnswer(post, botUser, "It happens when two branches change the same lines.", true, true);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        // No webhook mock registered: a request would fail the MockRestServiceServer, asserting nothing is sent.
        courseMemoryIngestionService.handleResolutionChange(reloadPost(post), managed, tutor, course);
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
        // in each of these cases, none of which dispatch anything.
        Post botPost = createQuestion("Bot-authored resolving answer?");
        AnswerPost botAnswer = saveAnswer(botPost, botUser, "Owned by the verification trigger.", true, true);
        courseMemoryIngestionService.handleResolutionChange(reloadPost(botPost), reloadManagedAnswer(botPost, botAnswer.getId()), tutor, course);

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
}
