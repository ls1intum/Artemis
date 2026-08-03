package de.tum.cit.aet.artemis.iris.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.Posting;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.repository.ConversationMessageRepository;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.CourseMemoryOperation;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSupportLevel;
import de.tum.cit.aet.artemis.iris.dto.IrisCourseMemoryStatusDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisCourseMemorySource;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisCourseMemoryThreadMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisWebhookCourseMemoryDeletionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisWebhookCourseMemoryIngestionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisWebsocketService;

/**
 * Builds and dispatches Course Memory ingestion and deletion requests to Pyris.
 * <p>
 * Two event-driven triggers feed it:
 * <ul>
 * <li><b>Trigger A</b> ({@link #ingestVerifiedAnswer}) – a tutor approved or edited an Iris draft in
 * the verification dashboard ({@code IRIS_AUTO} / {@code IRIS_CORRECTED}).</li>
 * <li><b>Trigger B</b> ({@link #handleResolutionChange}) – a thread's resolution state changed: an
 * answer was marked resolving ({@code TUTOR_WRITTEN} / {@code THREAD_RESOLVED}), un-marked, or
 * deleted. When nothing memory-worthy remains, the thread's entry is deleted instead.</li>
 * </ul>
 * Ingestion is best-effort and only fires for public/course-wide channels of Iris-enabled courses.
 * Entries are keyed on the thread's root post, so a thread with several resolving answers — or one
 * whose answer is later corrected — yields a single canonical entry rather than near-duplicates.
 */
@Service
@Lazy
@Conditional(IrisEnabled.class)
public class CourseMemoryIngestionService {

    private static final Logger log = LoggerFactory.getLogger(CourseMemoryIngestionService.class);

    /**
     * Prefixes disambiguating the two id namespaces sent to Pyris. Posts and answer posts live in
     * separate tables with independent {@code IDENTITY} sequences, so a root post and one of its
     * answers routinely share a number; unqualified ids made the two indistinguishable in the flat
     * thread list.
     */
    private static final String POST_ID_PREFIX = "post-";

    private static final String ANSWER_ID_PREFIX = "answer-";

    /**
     * Websocket topic suffix; the course id is appended. Resolves to
     * {@code /topic/iris/course-memory/{courseId}}, consumed client-side under {@code /user/...}.
     */
    public static final String COURSE_MEMORY_TOPIC_PREFIX = "course-memory/";

    private final PyrisConnectorService pyrisConnectorService;

    private final PyrisJobService pyrisJobService;

    private final IrisSettingsService irisSettingsService;

    private final AuthorizationCheckService authCheckService;

    private final ConversationMessageRepository conversationMessageRepository;

    private final IrisWebsocketService irisWebsocketService;

    @Value("${server.url}")
    private String artemisBaseUrl;

    public CourseMemoryIngestionService(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService, IrisSettingsService irisSettingsService,
            AuthorizationCheckService authCheckService, ConversationMessageRepository conversationMessageRepository, IrisWebsocketService irisWebsocketService) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.pyrisJobService = pyrisJobService;
        this.irisSettingsService = irisSettingsService;
        this.authCheckService = authCheckService;
        this.conversationMessageRepository = conversationMessageRepository;
        this.irisWebsocketService = irisWebsocketService;
    }

    /**
     * Trigger A: a tutor approved (optionally after editing) an Iris-generated answer in the
     * verification dashboard. Fires {@code IRIS_CORRECTED} when the tutor edited the draft (the edited
     * text is passed verbatim as {@code existingAnswer}), otherwise {@code IRIS_AUTO}.
     *
     * @param verifiedAnswer the now-verified Iris answer post (its content is the final, approved text)
     * @param edited         whether the tutor edited the draft content before approving
     * @param verifier       the tutor who verified the answer
     * @param course         the course the answer belongs to
     */
    public void ingestVerifiedAnswer(AnswerPost verifiedAnswer, boolean edited, @Nullable User verifier, Course course) {
        Post post = verifiedAnswer.getPost();
        if (post == null) {
            return;
        }
        if (!isEligible(post, course)) {
            return;
        }
        var source = edited ? PyrisCourseMemorySource.IRIS_CORRECTED : PyrisCourseMemorySource.IRIS_AUTO;
        String existingAnswer = edited ? verifiedAnswer.getContent() : null;
        String verifiedAt = verifiedAnswer.getVerifiedAt() != null ? verifiedAnswer.getVerifiedAt().toInstant().toString() : null;
        ingest(fetchThread(post), verifiedAnswer, course, verifier, source, verifier != null ? verifier.getLogin() : null, verifiedAt, existingAnswer);
    }

    /**
     * Trigger B: a thread's resolution state changed. Decides between ingesting the thread and
     * deleting its entry, so un-marking or deleting the resolving answer retracts the memory rather
     * than leaving Iris serving an answer nobody stands behind any more.
     * <p>
     * Iris-authored answers still awaiting verification are invisible to students and are excluded
     * from the thread; a verified Iris answer keeps the thread memory-worthy even once every
     * {@code resolvesPost} flag is gone, so Trigger A's entry survives an unrelated un-marking.
     *
     * @param post             the thread's root post
     * @param triggeringAnswer the answer whose flag changed, or {@code null} when it was deleted
     * @param marker           the user who changed the resolution state, if known
     * @param course           the course the thread belongs to
     */
    public void handleResolutionChange(Post post, @Nullable AnswerPost triggeringAnswer, @Nullable User marker, Course course) {
        if (!isEligible(post, course)) {
            return;
        }
        Post fullPost = fetchThread(post);
        List<AnswerPost> answers = visibleAnswers(fullPost);

        Optional<AnswerPost> anchor = selectAnchor(answers, triggeringAnswer);
        if (anchor.isEmpty()) {
            // Nothing resolving and no verified Iris answer left: the thread no longer holds an answer
            // anyone signed off on, so its entry must go.
            deleteThreadMemory(fullPost, marker, course);
            return;
        }

        AnswerPost resolvingAnswer = anchor.get();
        if (isBotAuthored(resolvingAnswer)) {
            // Verification (Trigger A) owns Iris-authored answers; re-ingesting here would relabel a
            // tutor-verified entry as merely community-resolved.
            log.info("Skipping course memory resolution ingestion for Iris-authored answer {} (owned by verification trigger)", resolvingAnswer.getId());
            return;
        }

        // Only staff-written answers may become course memory. A student marking their own or a peer's
        // answer as resolving does not make it correct, and storing it would let unreviewed content be
        // replayed to future students as a prior answer.
        if (!isAtLeastTutor(resolvingAnswer.getAuthor(), course)) {
            log.info("Skipping course memory resolution ingestion for thread {}: resolving answer {} was written by a student", fullPost.getId(), resolvingAnswer.getId());
            return;
        }

        if (hasOptedOutOfAi(resolvingAnswer.getAuthor())) {
            log.info("Skipping course memory resolution ingestion for thread {}: the author of answer {} opted out of AI", fullPost.getId(), resolvingAnswer.getId());
            return;
        }

        // The answer is tutor-written by the check above, so the trust tier turns only on whether a
        // tutor also endorsed it as the resolving one.
        boolean tutorVerified = isAtLeastTutor(marker, course);
        var source = tutorVerified ? PyrisCourseMemorySource.TUTOR_WRITTEN : PyrisCourseMemorySource.THREAD_RESOLVED;
        String verifiedBy = tutorVerified && marker != null ? marker.getLogin() : null;
        String verifiedAt = tutorVerified ? ZonedDateTime.now().toInstant().toString() : null;
        ingest(fullPost, resolvingAnswer, course, marker, source, verifiedBy, verifiedAt, null);
    }

    /**
     * The whole thread was deleted, so its Course Memory entry must go with it — otherwise Iris keeps
     * serving an answer whose source no longer exists and whose backlink is dead.
     *
     * @param post   the thread's root post, before deletion
     * @param actor  the user who deleted the thread, notified about the removal
     * @param course the course the thread belongs to
     */
    public void handleThreadDeleted(Post post, @Nullable User actor, Course course) {
        if (!isEligible(post, course)) {
            return;
        }
        deleteThreadMemory(post, actor, course);
    }

    /**
     * Picks the message the extractor anchors its answer on. Prefers the answer whose flag just
     * changed; when it was deleted or un-marked, falls back to the most recent answer that still
     * resolves the thread, then to the most recent verified Iris answer (Trigger A's entry).
     */
    private Optional<AnswerPost> selectAnchor(List<AnswerPost> answers, @Nullable AnswerPost triggeringAnswer) {
        if (triggeringAnswer != null && Boolean.TRUE.equals(triggeringAnswer.doesResolvePost())
                && answers.stream().anyMatch(answer -> answer.getId().equals(triggeringAnswer.getId()))) {
            return Optional.of(triggeringAnswer);
        }
        Optional<AnswerPost> resolving = answers.stream().filter(answer -> Boolean.TRUE.equals(answer.doesResolvePost())).max(Comparator.comparing(Posting::getCreationDate));
        if (resolving.isPresent()) {
            return resolving;
        }
        return answers.stream().filter(answer -> isBotAuthored(answer) && answer.isVerified()).max(Comparator.comparing(Posting::getCreationDate));
    }

    /**
     * Removes the thread's Course Memory entry. Safe to call when no entry exists — Pyris deletes by
     * deterministic id and treats a miss as a no-op.
     */
    private void deleteThreadMemory(Post post, @Nullable User actor, Course course) {
        String conversationId = String.valueOf(post.getConversation().getId());
        String postId = String.valueOf(post.getId());
        String actorLogin = actor != null ? actor.getLogin() : null;

        String jobToken = pyrisJobService.addCourseMemoryIngestionWebhookJob(course.getId(), conversationId, postId, null, actorLogin, CourseMemoryOperation.DELETE);
        var settings = executionSettings(jobToken, course);

        log.info("Deleting course memory for thread {} in course {}", postId, course.getId());
        notifyActor(actorLogin, IrisCourseMemoryStatusDTO.triggered(CourseMemoryOperation.DELETE, course.getId(), postId));
        pyrisConnectorService.executeCourseMemoryDeletionWebhook(new PyrisWebhookCourseMemoryDeletionExecutionDTO(settings, course.getId(), postId));
    }

    /**
     * Dispatches the ingestion for an already re-fetched thread. Callers are responsible for the
     * eligibility check and for passing a thread loaded via {@link #fetchThread}.
     */
    private void ingest(Post fullPost, AnswerPost anchor, Course course, @Nullable User actor, PyrisCourseMemorySource source, @Nullable String verifiedBy,
            @Nullable String verifiedAt, @Nullable String existingAnswer) {
        // The stored question is derived from the thread root, so a course memory entry would persist
        // and replay the content of a student who asked not to have their messages used by AI. Checked
        // here, on the single ingestion dispatch point, so deletion stays available — an entry written
        // before the author opted out must still be removable.
        if (hasOptedOutOfAi(fullPost.getAuthor())) {
            log.info("Skipping course memory ingestion for thread {}: the question's author opted out of AI", fullPost.getId());
            return;
        }

        List<PyrisCourseMemoryThreadMessageDTO> thread = buildThread(fullPost, course, anchor.getId());

        // Pyris rejects a thread with no verified answer flagged, because an unanchored transcript
        // makes the extractor guess an answer that would still be stored as tutor-verified. Catch it
        // here so the cause is visible in the Artemis log rather than as a remote 422.
        if (thread.stream().noneMatch(message -> message.isVerifiedAnswer() || message.resolvesPost())) {
            log.error("Skipping course memory ingestion for thread {}: answer {} is not present in the thread, so no verified answer could be flagged", fullPost.getId(),
                    anchor.getId());
            return;
        }

        String conversationId = String.valueOf(fullPost.getConversation().getId());
        String postId = String.valueOf(fullPost.getId());
        String messageId = String.valueOf(anchor.getId());
        String actorLogin = actor != null ? actor.getLogin() : null;

        String jobToken = pyrisJobService.addCourseMemoryIngestionWebhookJob(course.getId(), conversationId, postId, messageId, actorLogin, CourseMemoryOperation.INGEST);
        var settings = executionSettings(jobToken, course);

        var executionDTO = new PyrisWebhookCourseMemoryIngestionExecutionDTO(settings, course.getId(), conversationId, postId, messageId, source, true, thread, verifiedBy,
                verifiedAt, existingAnswer);

        log.info("Ingesting course memory for thread {} (source={}, anchor={}) in course {}", postId, source, messageId, course.getId());
        notifyActor(actorLogin, IrisCourseMemoryStatusDTO.triggered(CourseMemoryOperation.INGEST, course.getId(), postId));
        pyrisConnectorService.executeCourseMemoryIngestionWebhook(executionDTO);
    }

    /**
     * Tells the acting user that a run was dispatched. Sent here rather than from the client on the
     * HTTP response, because everything above this point can decide not to dispatch at all — a
     * client-side toast would announce an ingestion that never happened.
     */
    private void notifyActor(@Nullable String actorLogin, IrisCourseMemoryStatusDTO status) {
        if (actorLogin == null) {
            return;
        }
        irisWebsocketService.send(actorLogin, COURSE_MEMORY_TOPIC_PREFIX + status.courseId(), status);
    }

    private PyrisPipelineExecutionSettingsDTO executionSettings(String jobToken, Course course) {
        String variant = irisSettingsService.getSettingsForCourse(course).variant().jsonValue();
        return new PyrisPipelineExecutionSettingsDTO(jobToken, AiSelectionDecision.CLOUD_AI, artemisBaseUrl, variant, IrisSupportLevel.MODERATE.jsonValue());
    }

    /**
     * Whether the thread may be written to Course Memory at all: only public/course-wide channels of
     * Iris-enabled courses (req. 5).
     */
    private boolean isEligible(Post post, Course course) {
        Conversation conversation = post.getConversation();
        if (!(conversation instanceof Channel channel) || !(channel.getIsPublic() || channel.getIsCourseWide())) {
            log.info("Skipping course memory operation for thread {}: not a public/course-wide channel", post.getId());
            return false;
        }
        if (!irisSettingsService.isEnabledForCourse(course)) {
            log.info("Skipping course memory operation for thread {}: Iris is not enabled for course {}", post.getId(), course.getId());
            return false;
        }
        return true;
    }

    /**
     * Re-fetches the thread with authors eagerly joined so role resolution does not rely on
     * lazily-loaded sibling authors (which may be detached once the originating request transaction
     * has closed). Falls back to the passed-in post if the re-fetch yields nothing.
     */
    private Post fetchThread(Post post) {
        Post fullPost = conversationMessageRepository.findByPostIdsWithEagerRelationships(List.of(post.getId())).stream().findFirst().orElse(post);
        if (fullPost.getConversation() == null) {
            fullPost.setConversation(post.getConversation());
        }
        return fullPost;
    }

    /**
     * The thread's answers, oldest&rarr;newest, excluding unverified Iris replies so unapproved AI
     * drafts never enter the memory thread.
     */
    private List<AnswerPost> visibleAnswers(Post post) {
        return post.getAnswers().stream().filter(answerPost -> !answerPost.isUnverifiedIrisReply()).sorted(Comparator.comparing(Posting::getCreationDate)).toList();
    }

    /**
     * Builds the thread (ordered oldest&rarr;newest) for the given root post: the question first, then
     * its visible answers sorted by creation date. Each message states explicitly whether it is the
     * ingestion anchor and whether it resolves the post, so Pyris never has to infer either from ids.
     * <p>
     * Only staff-written answers carry {@code resolvesPost}. Pyris merges every flagged message into the
     * single stored answer, so flagging a student's resolving answer would splice unreviewed text into an
     * entry that is then served as a prior answer. Student messages still travel as untagged context, which
     * the extractor may read but never quote as the answer.
     */
    private List<PyrisCourseMemoryThreadMessageDTO> buildThread(Post fullPost, Course course, Long anchorAnswerId) {
        List<Posting> postings = new ArrayList<>();
        postings.add(fullPost);
        postings.addAll(visibleAnswers(fullPost));

        Map<Long, Boolean> isTutorByUserId = resolveTutorRoles(postings, course);

        List<PyrisCourseMemoryThreadMessageDTO> thread = new ArrayList<>();
        for (Posting posting : postings) {
            User author = posting.getAuthor();
            boolean isBot = author != null && author.isBot();
            String authorRole;
            if (isBot) {
                authorRole = "iris";
            }
            else if (author != null && Boolean.TRUE.equals(isTutorByUserId.get(author.getId()))) {
                authorRole = "tutor";
            }
            else {
                authorRole = "student";
            }
            String createdAt = posting.getCreationDate() != null ? posting.getCreationDate().toInstant().toString() : null;
            boolean isAnswer = posting instanceof AnswerPost;
            String id = (isAnswer ? ANSWER_ID_PREFIX : POST_ID_PREFIX) + posting.getId();
            boolean isVerifiedAnswer = isAnswer && posting.getId().equals(anchorAnswerId);
            boolean isStaffAuthored = isBot || (author != null && Boolean.TRUE.equals(isTutorByUserId.get(author.getId())));
            boolean resolvesPost = isStaffAuthored && posting instanceof AnswerPost answerPost && Boolean.TRUE.equals(answerPost.doesResolvePost());
            thread.add(new PyrisCourseMemoryThreadMessageDTO(id, authorRole, posting.getContent(), createdAt, isBot, isVerifiedAnswer, resolvesPost));
        }
        return thread;
    }

    private boolean isBotAuthored(AnswerPost answerPost) {
        return answerPost.getAuthor() != null && answerPost.getAuthor().isBot();
    }

    /**
     * Whether the user asked for their content not to be used by AI. Mirrors the check
     * {@code AutonomousTutorForwardingService} applies before forwarding a post to Pyris.
     */
    private boolean hasOptedOutOfAi(@Nullable User user) {
        return user != null && AiSelectionDecision.NO_AI.equals(user.getSelectedLLMUsage());
    }

    private boolean isAtLeastTutor(@Nullable User user, Course course) {
        return user != null && !user.isBot() && authCheckService.isAtLeastTeachingAssistantInCourse(user.getLogin(), course.getId());
    }

    /**
     * Resolves which thread authors are at least teaching assistants in the course. The check runs by
     * login so it stays a plain database lookup and never touches lazily-loaded course roles on the
     * (possibly detached) author entities. A thread has only a handful of distinct authors, so the
     * per-author query is cheap and each author is resolved at most once.
     */
    private Map<Long, Boolean> resolveTutorRoles(List<Posting> postings, Course course) {
        Map<Long, Boolean> isTutorByUserId = new HashMap<>();
        for (Posting posting : postings) {
            User author = posting.getAuthor();
            if (author == null || author.isBot() || isTutorByUserId.containsKey(author.getId())) {
                continue;
            }
            isTutorByUserId.put(author.getId(), authCheckService.isAtLeastTeachingAssistantInCourse(author.getLogin(), course.getId()));
        }
        return isTutorByUserId;
    }
}
