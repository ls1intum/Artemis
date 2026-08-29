package de.tum.cit.aet.artemis.iris.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.Posting;
import de.tum.cit.aet.artemis.communication.domain.UserRole;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationMessageRepository;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.dto.UserRoleDTO;
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

    /** The only variant the Pyris course memory pipelines define. */
    private static final String COURSE_MEMORY_PIPELINE_VARIANT = "default";

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

    private final AnswerPostRepository answerPostRepository;

    private final UserRepository userRepository;

    private final UserAiPreferenceService userAiPreferenceService;

    private final IrisWebsocketService irisWebsocketService;

    @Value("${server.url}")
    private String artemisBaseUrl;

    public CourseMemoryIngestionService(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService, IrisSettingsService irisSettingsService,
            AuthorizationCheckService authCheckService, ConversationMessageRepository conversationMessageRepository, AnswerPostRepository answerPostRepository,
            UserRepository userRepository, IrisWebsocketService irisWebsocketService, UserAiPreferenceService userAiPreferenceService) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.pyrisJobService = pyrisJobService;
        this.irisSettingsService = irisSettingsService;
        this.authCheckService = authCheckService;
        this.conversationMessageRepository = conversationMessageRepository;
        this.answerPostRepository = answerPostRepository;
        this.userRepository = userRepository;
        this.irisWebsocketService = irisWebsocketService;
        this.userAiPreferenceService = userAiPreferenceService;
    }

    /**
     * Trigger A: a tutor approved (optionally after editing) an Iris-generated answer in the
     * verification dashboard. Fires {@code IRIS_CORRECTED} when the tutor edited the draft, otherwise
     * {@code IRIS_AUTO}; the approved text is passed verbatim as {@code existingAnswer} in both cases,
     * because approving a draft unchanged endorses that exact wording just as editing it does.
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
        // Passed whether or not the tutor edited the draft: approving it unchanged is still a sign-off on
        // that exact wording, and letting the extractor re-derive the answer would store — and later
        // re-serve as tutor-verified — a paraphrase the tutor never saw. The extraction still runs; only
        // the question comes out of it.
        String existingAnswer = verifiedAnswer.getContent();
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
     * {@code resolvesPost} flag is gone, and the entry is then rebuilt from it under Trigger A's
     * provenance rather than left holding whatever the retracted staff answer had written.
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
        if (isBotAuthored(resolvingAnswer) && answerPostRepository.hasHumanVerifier(resolvingAnswer.getId())) {
            // Verification (Trigger A) owns Iris answers a tutor approved in the dashboard, so this must not
            // fall through to the LLM extraction below — it would re-derive the answer and silently discard
            // the tutor's verbatim edit. Re-dispatching on Trigger A's terms rather than returning, because
            // the entry is keyed on the thread and may currently hold what a *different* answer put there:
            // a resolving staff answer that has since been un-marked or deleted. Leaving it untouched would
            // keep serving retracted text, and would ignore an edit to the verified answer itself.
            reingestVerifiedIrisAnswer(fullPost, resolvingAnswer, marker, course);
            return;
        }

        if (hasOptedOutOfAi(resolvingAnswer.getAuthor())) {
            log.info("Skipping course memory resolution ingestion for thread {}: the author of answer {} opted out of AI", fullPost.getId(), resolvingAnswer.getId());
            return;
        }

        // The trust tier turns on who endorsed the answer by marking it resolving, not on who wrote it: a
        // student's answer a tutor signs off on is worth as much as a tutor's own, and a tutor's answer a
        // student marks resolving is not tutor-verified.
        boolean tutorEndorsed = isAtLeastTutor(marker, course);
        var source = resolveResolutionSource(resolvingAnswer, tutorEndorsed);
        String verifiedBy = tutorEndorsed && marker != null ? marker.getLogin() : null;
        String verifiedAt = tutorEndorsed ? ZonedDateTime.now().toInstant().toString() : null;
        ingest(fullPost, resolvingAnswer, course, marker, source, verifiedBy, verifiedAt, null);
    }

    /**
     * Re-dispatches a thread whose surviving anchor is a dashboard-verified Iris answer, under the
     * provenance Trigger A would have given it.
     * <p>
     * An edit after creation is the tutor's correction, which only changes the provenance label; the
     * content is passed verbatim as {@code existingAnswer} either way, exactly as
     * {@link #ingestVerifiedAnswer} does. The verifier and verification timestamp are the answer's own,
     * not the user who happened to trigger this run by touching some other message in the thread.
     */
    private void reingestVerifiedIrisAnswer(Post fullPost, AnswerPost verifiedAnswer, @Nullable User actor, Course course) {
        boolean corrected = verifiedAnswer.getUpdatedDate() != null;
        var source = corrected ? PyrisCourseMemorySource.IRIS_CORRECTED : PyrisCourseMemorySource.IRIS_AUTO;
        // Verbatim regardless of correction, for the same reason as in ingestVerifiedAnswer: the answer
        // carries a tutor's sign-off on its exact text either way.
        String existingAnswer = verifiedAnswer.getContent();
        String verifiedBy = answerPostRepository.findVerifierLoginById(verifiedAnswer.getId()).orElse(null);
        String verifiedAt = verifiedAnswer.getVerifiedAt() != null ? verifiedAnswer.getVerifiedAt().toInstant().toString() : null;

        log.info("Restoring course memory of thread {} from tutor-verified Iris answer {} (source={})", fullPost.getId(), verifiedAnswer.getId(), source);
        ingest(fullPost, verifiedAnswer, course, actor, source, verifiedBy, verifiedAt, existingAnswer);
    }

    /**
     * The provenance label for a Trigger B ingestion.
     * <p>
     * Only the anchor being Iris-authored and the endorsement matter here. An Iris answer reaching this point
     * was published automatically on a high confidence score and never reviewed by anyone (the dashboard-verified
     * ones return above), so a tutor marking it resolving is the first and only human sign-off it gets —
     * exactly what {@code IRIS_AUTO} denotes. Without that sign-off nothing here is tutor-verified, whoever
     * wrote it.
     */
    private PyrisCourseMemorySource resolveResolutionSource(AnswerPost resolvingAnswer, boolean tutorEndorsed) {
        if (!tutorEndorsed) {
            return PyrisCourseMemorySource.THREAD_RESOLVED;
        }
        return isBotAuthored(resolvingAnswer) ? PyrisCourseMemorySource.IRIS_AUTO : PyrisCourseMemorySource.TUTOR_WRITTEN;
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
     * resolves the thread, then to the most recent tutor-verified Iris answer (Trigger A's entry).
     * <p>
     * The last fallback exists to <em>restore</em> Trigger A's entry rather than delete it: when a
     * thread's last {@code resolvesPost} flag disappears but a tutor-approved Iris answer still stands,
     * {@link #handleResolutionChange} re-ingests from that answer instead of dropping the thread. It is
     * therefore restricted to dashboard-verified answers — an Iris answer published automatically was
     * never signed off on, so once nothing resolves the thread any more there is nothing left to keep.
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
        return answers.stream().filter(answer -> isBotAuthored(answer) && answer.isVerified()).max(Comparator.comparing(Posting::getCreationDate))
                .filter(answer -> answerPostRepository.hasHumanVerifier(answer.getId()));
    }

    /**
     * Removes the thread's Course Memory entry. Safe to call when no entry exists — Pyris deletes by
     * deterministic id and treats a miss as a no-op.
     */
    private void deleteThreadMemory(Post post, @Nullable User actor, Course course) {
        String conversationId = String.valueOf(post.getConversation().getId());
        String postId = String.valueOf(post.getId());
        String actorLogin = actor != null ? actor.getLogin() : null;

        // Deletion runs no model — Pyris serves it from a deleter that touches only Weaviate — so the
        // selection is immaterial and a local-only deployment retracts entries just as well as a cloud one.
        // It only has to be a valid value.
        String jobToken = pyrisJobService.addCourseMemoryIngestionWebhookJob(course.getId(), conversationId, postId, null, actorLogin, CourseMemoryOperation.DELETE);
        var settings = executionSettings(jobToken, course, AiSelectionDecision.CLOUD_AI);

        log.info("Deleting course memory for thread {} in course {}", postId, course.getId());
        notifyActor(actorLogin, IrisCourseMemoryStatusDTO.triggered(CourseMemoryOperation.DELETE, course.getId(), postId));
        boolean dispatched = pyrisConnectorService.executeCourseMemoryDeletionWebhook(PyrisWebhookCourseMemoryDeletionExecutionDTO.forThread(settings, course.getId(), postId));
        notifyIfDispatchFailed(dispatched, actorLogin, CourseMemoryOperation.DELETE, course, postId);
    }

    /**
     * A channel stopped being a place Course Memory may draw from — it was deleted, or its visibility
     * was narrowed — so every entry mined from it is removed.
     * <p>
     * Necessary because channel eligibility is only evaluated when an entry is <em>written</em>: without
     * this, an answer ingested while the channel was public would keep being served to the whole course
     * after the channel was restricted to a subset of it.
     *
     * @param channel the channel whose entries should be removed
     * @param actor   the user who deleted or restricted the channel, notified about the removal
     * @param course  the course the channel belongs to
     */
    public void handleChannelNoLongerEligible(Channel channel, @Nullable User actor, Course course) {
        if (!irisSettingsService.isEnabledForCourse(course)) {
            return;
        }
        String conversationId = String.valueOf(channel.getId());
        String actorLogin = actor != null ? actor.getLogin() : null;

        String jobToken = pyrisJobService.addCourseMemoryIngestionWebhookJob(course.getId(), conversationId, conversationId, null, actorLogin, CourseMemoryOperation.DELETE);
        var settings = executionSettings(jobToken, course, AiSelectionDecision.CLOUD_AI);

        log.info("Deleting all course memory entries of channel {} in course {}", conversationId, course.getId());
        notifyActor(actorLogin, IrisCourseMemoryStatusDTO.triggered(CourseMemoryOperation.DELETE, course.getId(), conversationId));
        boolean dispatched = pyrisConnectorService
                .executeCourseMemoryDeletionWebhook(PyrisWebhookCourseMemoryDeletionExecutionDTO.forConversation(settings, course.getId(), conversationId));
        notifyIfDispatchFailed(dispatched, actorLogin, CourseMemoryOperation.DELETE, course, conversationId);
    }

    /**
     * The whole course was deleted, so every entry mined from it is removed.
     * <p>
     * Cannot be expressed as a series of channel purges: course deletion drops all of the course's
     * conversations in one bulk statement, so there is no channel id left to purge one by one — and once
     * the course is gone, no Artemis object remains that could ever ask for these entries' removal. They
     * would sit in Weaviate permanently.
     * <p>
     * Gated on Iris being enabled for the course, like every other trigger: a course that never had Iris
     * on can hold no entries, and firing a purge for each of those would put a Pyris request on the
     * critical path of every course deletion in the installation. The residual gap — Iris switched off
     * and only then the course deleted — is the same one the thread and channel triggers carry, and is
     * best closed for all three at once by an operator-facing purge rather than by three exceptions.
     *
     * @param course the course being deleted
     * @param actor  the user who deleted the course, notified about the removal
     */
    public void handleCourseDeleted(Course course, @Nullable User actor) {
        if (!irisSettingsService.isEnabledForCourse(course)) {
            return;
        }
        String courseId = String.valueOf(course.getId());
        String actorLogin = actor != null ? actor.getLogin() : null;

        String jobToken = pyrisJobService.addCourseMemoryIngestionWebhookJob(course.getId(), courseId, courseId, null, actorLogin, CourseMemoryOperation.DELETE);
        var settings = executionSettings(jobToken, course, AiSelectionDecision.CLOUD_AI);

        log.info("Deleting all course memory entries of course {}", courseId);
        notifyActor(actorLogin, IrisCourseMemoryStatusDTO.triggered(CourseMemoryOperation.DELETE, course.getId(), courseId));
        boolean dispatched = pyrisConnectorService.executeCourseMemoryDeletionWebhook(PyrisWebhookCourseMemoryDeletionExecutionDTO.forCourse(settings, course.getId()));
        notifyIfDispatchFailed(dispatched, actorLogin, CourseMemoryOperation.DELETE, course, courseId);
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
        var settings = executionSettings(jobToken, course, resolveThreadAiSelection(fullPost));

        // The real value, not a constant: Pyris fails closed on this flag, and a hardcoded true turns its
        // last line of defence into a no-op the moment a future trigger forgets the eligibility check.
        boolean isPublicChannel = isPublicOrCourseWide(fullPost.getConversation());
        var executionDTO = new PyrisWebhookCourseMemoryIngestionExecutionDTO(settings, course.getId(), conversationId, postId, messageId, source, isPublicChannel, thread,
                verifiedBy, verifiedAt, existingAnswer);

        log.info("Ingesting course memory for thread {} (source={}, anchor={}) in course {}", postId, source, messageId, course.getId());
        notifyActor(actorLogin, IrisCourseMemoryStatusDTO.triggered(CourseMemoryOperation.INGEST, course.getId(), postId));
        boolean dispatched = pyrisConnectorService.executeCourseMemoryIngestionWebhook(executionDTO);
        notifyIfDispatchFailed(dispatched, actorLogin, CourseMemoryOperation.INGEST, course, postId);
    }

    /**
     * Closes out a run the dispatch never started. {@code TRIGGERED} has already been pushed at this
     * point, and a request that never reached Pyris produces no status callback — without this the
     * client would show the run as still in progress for good.
     */
    private void notifyIfDispatchFailed(boolean dispatched, @Nullable String actorLogin, CourseMemoryOperation operation, Course course, String postId) {
        if (dispatched) {
            return;
        }
        notifyActor(actorLogin, IrisCourseMemoryStatusDTO.failed(operation, course.getId(), postId, "Could not reach Pyris"));
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

    private PyrisPipelineExecutionSettingsDTO executionSettings(String jobToken, Course course, AiSelectionDecision aiSelection) {
        // Deliberately not the course's Iris variant: the course memory pipelines define only "default",
        // so a course set to "advanced" would make every run fail Pyris variant validation with a 400 that
        // the connector swallows — course memory would silently never work for that course. Same reason
        // PyrisWebhookService pins "default" for lecture ingestion.
        return new PyrisPipelineExecutionSettingsDTO(jobToken, aiSelection, artemisBaseUrl, COURSE_MEMORY_PIPELINE_VARIANT, IrisSupportLevel.MODERATE.jsonValue());
    }

    /**
     * Resolves which inference environment may see this thread, taking the most restrictive choice of
     * everyone whose content is forwarded: a single {@code LOCAL_AI} participant pins the whole run to
     * on-premise inference.
     * <p>
     * Ingestion sends the whole transcript to an extraction model, so the question "which model may see
     * this thread" is the same one {@code AutonomousTutorForwardingService#resolveThreadAiSelection}
     * answers for a tutor run, and it must be answered the same way. Bot authors carry no student's
     * preference; {@code NO_AI} authors are excluded because their content is redacted before it leaves
     * Artemis (and a {@code NO_AI} question author or resolving author stops ingestion outright), so
     * there is no preference of theirs left to honour.
     * <p>
     * Kept local rather than shared with the tutor path so this PR stays independent of the autonomous
     * tutor branch; the two should be folded into one helper once both are on {@code develop}.
     *
     * @param fullPost the thread root, with all answers loaded
     * @return {@link AiSelectionDecision#LOCAL_AI} if any forwarded author chose it, otherwise {@link AiSelectionDecision#CLOUD_AI}
     */
    private AiSelectionDecision resolveThreadAiSelection(Post fullPost) {
        Set<Long> userIds = Stream.concat(Stream.of(fullPost.getAuthor()), visibleAnswers(fullPost).stream().map(AnswerPost::getAuthor)).filter(Objects::nonNull)
                .filter(author -> !author.isBot()).map(User::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        // One query for the whole thread rather than one per author.
        boolean anyLocal = userAiPreferenceService.findDecisions(userIds).values().stream().anyMatch(AiSelectionDecision.LOCAL_AI::equals);
        return anyLocal ? AiSelectionDecision.LOCAL_AI : AiSelectionDecision.CLOUD_AI;
    }

    /**
     * Whether the thread may be written to Course Memory at all: only public/course-wide channels of
     * Iris-enabled courses (req. 5).
     */
    private boolean isEligible(Post post, Course course) {
        if (!isPublicOrCourseWide(post.getConversation())) {
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
     * Whether a conversation is a channel every course member can read, which is the only kind Course
     * Memory may draw from (req. 5).
     *
     * @param conversation the conversation to check
     * @return {@code true} for a public or course-wide channel
     */
    private boolean isPublicOrCourseWide(@Nullable Conversation conversation) {
        return conversation instanceof Channel channel && (channel.getIsPublic() || channel.getIsCourseWide());
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
     * {@code resolvesPost} is carried by whichever answer holds the flag, whoever wrote it — the trust
     * tier is decided by who <em>endorsed</em> the answer, not by who authored it, and that decision is
     * made once in {@link #handleResolutionChange}. A student's answer therefore does travel flagged, and
     * Pyris merges it into the stored answer, when a tutor marked it as resolving the thread; without that
     * tutor endorsement the entry is only {@code THREAD_RESOLVED} and is never served as tutor-verified.
     */
    private List<PyrisCourseMemoryThreadMessageDTO> buildThread(Post fullPost, Course course, Long anchorAnswerId) {
        List<Posting> postings = new ArrayList<>();
        postings.add(fullPost);
        postings.addAll(visibleAnswers(fullPost));

        Map<Long, UserRole> rolesByUserId = resolveThreadAuthorRoles(postings, course);

        List<PyrisCourseMemoryThreadMessageDTO> thread = new ArrayList<>();
        for (Posting posting : postings) {
            User author = posting.getAuthor();
            boolean isBot = author != null && author.isBot();
            String authorRole = resolveAuthorRole(author, isBot, rolesByUserId);
            String createdAt = posting.getCreationDate() != null ? posting.getCreationDate().toInstant().toString() : null;
            boolean isAnswer = posting instanceof AnswerPost;
            String id = (isAnswer ? ANSWER_ID_PREFIX : POST_ID_PREFIX) + posting.getId();
            boolean isVerifiedAnswer = isAnswer && posting.getId().equals(anchorAnswerId);
            boolean resolvesPost = posting instanceof AnswerPost answerPost && Boolean.TRUE.equals(answerPost.doesResolvePost());

            // A participant who opted out of AI still occupies a slot in the transcript so the thread reads
            // in order, but none of their words travel. Their flags are cleared with their content: Pyris
            // merges every flagged message into the stored answer, and a placeholder must never become
            // part of it. The anchor itself can never be redacted — an opted-out resolving author stops the
            // ingestion outright in handleResolutionChange.
            boolean redacted = hasOptedOutOfAi(author);
            String content = redacted ? "" : posting.getContent();
            if (redacted) {
                isVerifiedAnswer = false;
                resolvesPost = false;
            }
            thread.add(new PyrisCourseMemoryThreadMessageDTO(id, authorRole, content, createdAt, isBot, isVerifiedAnswer, resolvesPost, redacted));
        }
        return thread;
    }

    private String resolveAuthorRole(@Nullable User author, boolean isBot, Map<Long, UserRole> rolesByUserId) {
        if (isBot) {
            return "iris";
        }
        if (author == null) {
            return "student";
        }
        // Instructors and editors reach the extractor as tutors: the prompt's vocabulary is a trust tier
        // (student / tutor / iris), not a course role.
        return switch (rolesByUserId.getOrDefault(author.getId(), UserRole.USER)) {
            case INSTRUCTOR, TUTOR -> "tutor";
            case USER -> "student";
        };
    }

    private boolean isBotAuthored(AnswerPost answerPost) {
        return answerPost.getAuthor() != null && answerPost.getAuthor().isBot();
    }

    /**
     * Whether the user asked for their content not to be used by AI. Mirrors the check
     * {@code AutonomousTutorForwardingService} applies before forwarding a post to Pyris.
     */
    private boolean hasOptedOutOfAi(@Nullable User user) {
        return user != null && user.getId() != null && AiSelectionDecision.NO_AI.equals(userAiPreferenceService.findDecision(user.getId()));
    }

    private boolean isAtLeastTutor(@Nullable User user, Course course) {
        return user != null && !user.isBot() && authCheckService.isAtLeastTeachingAssistantInCourse(user.getLogin(), course.getId());
    }

    /**
     * Resolves the course role of every thread author in a single query, rather than one role lookup per
     * distinct author. The roles come from the database rather than from the author entities, whose course
     * roles are lazily loaded and may be detached once the originating request transaction has closed.
     *
     * @param postings the thread's messages, root post first
     * @param course   the course the thread belongs to
     * @return the course roles keyed by user id; authors without a resolvable role are absent
     */
    private Map<Long, UserRole> resolveThreadAuthorRoles(List<Posting> postings, Course course) {
        Set<Long> userIds = new HashSet<>();
        for (Posting posting : postings) {
            User author = posting.getAuthor();
            if (author != null && !author.isBot()) {
                userIds.add(author.getId());
            }
        }
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findUserRolesInCourse(userIds, course.getId()).stream().filter(userRole -> userRole.role() != null)
                .collect(Collectors.toMap(UserRoleDTO::userId, UserRoleDTO::role, (first, second) -> first));
    }
}
