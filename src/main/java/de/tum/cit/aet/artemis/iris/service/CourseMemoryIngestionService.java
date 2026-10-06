package de.tum.cit.aet.artemis.iris.service;

import static de.tum.cit.aet.artemis.iris.web.IrisWebsocketTopics.COURSE_MEMORY;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.Posting;
import de.tum.cit.aet.artemis.communication.domain.UserRole;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.dto.CourseMemoryThreadDTO;
import de.tum.cit.aet.artemis.communication.dto.ResolvingAnswerEndorserDTO;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationMessageRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.dto.UserRoleDTO;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
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
 * Builds and dispatches Course Memory ingestions and retractions to Pyris.
 * <p>
 * Every change that can affect a thread's entry ends in {@link #refreshThread}: a tutor approved an Iris draft in the
 * verification dashboard, an answer was marked or un-marked as resolving, a message of the thread was edited or
 * deleted, a participant opted out of AI or lost their account. The refresh decides from the current database state
 * alone, so it gives the same result whichever event triggered it:
 * <ol>
 * <li>mint the next version of the thread ({@code Post#courseMemoryVersion}),</li>
 * <li>read the thread fresh,</li>
 * <li>ingest it under that version, or retract its entry with a tombstone carrying that version when nothing
 * memory-worthy remains or the thread may no longer be stored.</li>
 * </ol>
 * Pyris keeps the highest version per thread and ignores older operations, so the operation with the highest version
 * always describes the newest committed state, whatever order the webhooks finish in.
 * <p>
 * Three things keep the store honest where a single refresh cannot:
 * <ul>
 * <li>Iris only ever serves entries from channels that Artemis lists as readable by every student when it dispatches
 * an autonomous tutor run, and Artemis checks those channels again before publishing a reply unreviewed.</li>
 * <li>Changes that a refresh must reflect bump the version right before the change is saved, so an update that never
 * reached Pyris leaves an entry with an older version behind.</li>
 * <li>The nightly {@link CourseMemorySyncService} retracts every entry whose version is older than Artemis's, whose
 * thread may no longer be stored, or whose thread or course was deleted.</li>
 * </ul>
 * Course Memory stores no user identity: no logins, no ids of people. Messages of participants who opted out of AI,
 * whose account is not active or who no longer exist are redacted.
 */
@Service
@Lazy
@Conditional(IrisEnabled.class)
public class CourseMemoryIngestionService {

    private static final Logger log = LoggerFactory.getLogger(CourseMemoryIngestionService.class);

    /**
     * Prefixes disambiguating the two id namespaces sent to Pyris. Posts and answer posts live in separate tables with
     * independent {@code IDENTITY} sequences, so a root post and one of its answers routinely share a number.
     */
    private static final String POST_ID_PREFIX = "post-";

    private static final String ANSWER_ID_PREFIX = "answer-";

    /** The only variant the Pyris course memory pipelines define. */
    private static final String COURSE_MEMORY_PIPELINE_VARIANT = "default";

    /**
     * A structured user mention, {@code [user]Display Name(login)[/user]}, as {@code PostingService} parses it. The login
     * is identity and must not reach Course Memory; the display name stays, as the author wrote it.
     */
    private static final Pattern USER_MENTION = Pattern.compile("\\[user]([^\\[\\]()]*+)\\(?([^\\[\\]()]*+)\\)?\\[/user]");

    /**
     * The version sent when the thread itself was deleted. Its row is gone, so no version can be minted for it, and none
     * is needed: post ids are never reused, so nothing legitimate can ever follow this retraction.
     */
    static final long FINAL_VERSION = Long.MAX_VALUE;

    private final PyrisConnectorService pyrisConnectorService;

    private final PyrisJobService pyrisJobService;

    private final IrisSettingsService irisSettingsService;

    private final AuthorizationCheckService authCheckService;

    private final ConversationMessageRepository conversationMessageRepository;

    private final AnswerPostRepository answerPostRepository;

    private final ChannelRepository channelRepository;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final UserAiPreferenceService userAiPreferenceService;

    private final IrisWebsocketService irisWebsocketService;

    @Value("${server.url}")
    private String artemisBaseUrl;

    public CourseMemoryIngestionService(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService, IrisSettingsService irisSettingsService,
            AuthorizationCheckService authCheckService, ConversationMessageRepository conversationMessageRepository, AnswerPostRepository answerPostRepository,
            ChannelRepository channelRepository, CourseRepository courseRepository, UserRepository userRepository, IrisWebsocketService irisWebsocketService,
            UserAiPreferenceService userAiPreferenceService) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.pyrisJobService = pyrisJobService;
        this.irisSettingsService = irisSettingsService;
        this.authCheckService = authCheckService;
        this.conversationMessageRepository = conversationMessageRepository;
        this.answerPostRepository = answerPostRepository;
        this.channelRepository = channelRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.irisWebsocketService = irisWebsocketService;
        this.userAiPreferenceService = userAiPreferenceService;
    }

    /**
     * The two trust tiers an anchor can hold. Declared in ascending order so the natural ordering picks the
     * tutor-endorsed candidate.
     */
    private enum EndorsementTier {
        /** Marked resolving by someone without teaching authority, or with no endorser recorded at all. */
        COMMUNITY,
        /** Marked resolving by a tutor, or approved by one in the verification dashboard. */
        TUTOR
    }

    /**
     * An answer that could anchor the thread's entry, with everything the ranking in {@link #selectAnchor} compares.
     */
    private record AnchorCandidate(AnswerPost answer, EndorsementTier tier, boolean resolving) {
    }

    /**
     * Refreshes a thread's entry after anything that can change it: a resolution change, an edit, a deletion of one of
     * its messages, a dashboard approval of an Iris draft.
     *
     * @param postId the thread's root post id
     * @param actor  the user whose action triggered the refresh, notified about the run; {@code null} for none
     * @param course the course the thread belongs to
     */
    public void refreshThread(long postId, @Nullable User actor, Course course) {
        Optional<Long> previous = conversationMessageRepository.findCourseMemoryVersion(postId);
        if (previous.isEmpty()) {
            return;
        }
        // Nothing was ever dispatched for a thread at version 0, so a thread that may not be stored, or that has nothing
        // to store, needs no work at all: no retraction and no status message about a removal. This read is only a
        // shortcut; the decision itself is made again on the fresh state below. A change that adds an anchor later
        // refreshes the thread itself, after its own commit.
        if (previous.get() == 0 && (!mayBeStored(postId, course) || !hasAnchor(postId, course))) {
            return;
        }
        Optional<Long> minted = conversationMessageRepository.mintCourseMemoryVersion(postId);
        if (minted.isEmpty()) {
            return;
        }
        long version = minted.get();
        Optional<Post> thread = fetchThread(postId);
        if (thread.isEmpty()) {
            return;
        }
        Post fullPost = thread.get();

        if (!mayBeStored(postId, course)) {
            retract(course.getId(), postId, version, actor);
            return;
        }

        List<AnswerPost> answers = visibleAnswers(fullPost);
        Map<Long, Boolean> tutorEndorsed = loadTutorEndorsements(fullPost, course);
        Optional<AnchorCandidate> anchor = selectAnchor(answers, tutorEndorsed);
        if (anchor.isEmpty()) {
            // Nothing a participant or a tutor signed off on survives, or the only candidate belongs to an author whose
            // words may not be stored: the entry has to go rather than keep serving what a retracted answer wrote.
            retract(course.getId(), postId, version, actor);
            return;
        }
        ingest(fullPost, anchor.get(), course, actor, version);
    }

    /**
     * Whether the thread currently has an answer its entry could be built from.
     */
    private boolean hasAnchor(long postId, Course course) {
        return fetchThread(postId).flatMap(post -> selectAnchor(visibleAnswers(post), loadTutorEndorsements(post, course))).isPresent();
    }

    /**
     * Refreshes several threads in the background, e.g. after a participant opted out of AI. The versions were already
     * bumped by the caller, so a refresh that never runs leaves entries the nightly sync retracts.
     *
     * @param postIds the threads' root post ids
     */
    @Async
    public void refreshThreadsAsync(Collection<Long> postIds) {
        SecurityUtils.setAuthorizationObject();
        for (long postId : postIds) {
            try {
                Optional<Long> courseId = conversationMessageRepository.findCourseIdOfPost(postId);
                if (courseId.isEmpty()) {
                    continue;
                }
                refreshThread(postId, null, courseRepository.findByIdElseThrow(courseId.get()));
            }
            catch (Exception e) {
                log.error("Failed to refresh course memory of thread {}", postId, e);
            }
        }
    }

    /**
     * The whole thread was deleted: its entry is retracted for good. Always sent, whatever the stored version, because the
     * row that held it is gone; Pyris turns it into a tombstone nothing can overwrite.
     *
     * @param postId   the deleted thread's root post id
     * @param courseId the course it belonged to
     * @param actor    the user who deleted it, notified about the removal
     */
    public void retractDeletedThread(long postId, long courseId, @Nullable User actor) {
        retract(courseId, postId, FINAL_VERSION, actor);
    }

    /**
     * Bumps the version of every thread with a Course Memory version that contains content by the given user. Called right
     * before an opt-out from AI, a deactivation or the closing of an account is recorded; pass the result to
     * {@link #outdateThreadsAfterChange} once it is. A refresh that never runs leaves the outdated
     * entries for the nightly sync to retract.
     *
     * @param userId the user
     * @return the affected threads' root post ids
     */
    public List<Long> invalidateThreadsWithContentBy(long userId) {
        List<Long> postIds = conversationMessageRepository.findCourseMemoryThreadIdsWithContentBy(userId);
        if (!postIds.isEmpty()) {
            conversationMessageRepository.bumpCourseMemoryVersionsOfThreadsWithContentBy(userId);
        }
        return postIds;
    }

    /**
     * Bumps the threads with content by the user again once the account change is saved, and returns the threads to
     * rebuild. See {@link ConversationMessageRepository#bumpCourseMemoryVersionIfTracked} for why the threads are bumped
     * both before and after. The threads are selected again here: a thread stored for the first time during the change was
     * not tracked yet when {@link #invalidateThreadsWithContentBy} ran, and its refresh may have read the account from
     * before the change. A refresh that mints its version after this selection reads the saved change.
     *
     * @param userId the user
     * @param before the threads returned by {@link #invalidateThreadsWithContentBy} before the change
     * @return the threads bumped now, to rebuild
     */
    public List<Long> outdateThreadsAfterChange(long userId, Collection<Long> before) {
        Set<Long> postIds = new LinkedHashSet<>(before);
        postIds.addAll(conversationMessageRepository.findCourseMemoryThreadIdsWithContentBy(userId));
        if (!postIds.isEmpty()) {
            conversationMessageRepository.bumpCourseMemoryVersionsIfTracked(postIds);
        }
        return List.copyOf(postIds);
    }

    /**
     * Follows up on an account deletion that removed the user's messages: deleted threads are retracted for good. Their
     * versions were bumped in the transaction that removed the messages.
     *
     * @param threads the threads that held content by the deleted account, as captured before the deletion
     * @return the threads that still exist and need a refresh
     */
    public List<Long> retractDeletedThreadsOf(List<CourseMemoryThreadDTO> threads) {
        List<Long> surviving = new ArrayList<>();
        for (CourseMemoryThreadDTO thread : threads) {
            if (conversationMessageRepository.findCourseMemoryVersion(thread.postId()).isPresent()) {
                surviving.add(thread.postId());
            }
            else {
                retract(thread.courseId(), thread.postId(), FINAL_VERSION, null);
            }
        }
        return surviving;
    }

    /**
     * Whether the thread may be stored now: it lives in a channel every student of the course can read, and Iris is
     * enabled for the course. Read from the database on every call.
     */
    private boolean mayBeStored(long postId, Course course) {
        Optional<Long> conversationId = conversationMessageRepository.findConversationIdOfPost(postId);
        if (conversationId.isEmpty() || !channelRepository.isChannelReadableByAllStudents(conversationId.get(), ZonedDateTime.now())) {
            return false;
        }
        return irisSettingsService.isEnabledForCourse(course);
    }

    /**
     * Picks the answer the entry is anchored on, from persisted state only, so every refresh of the same state picks the
     * same answer. Candidates are the answers that resolve the thread and the Iris answers a tutor approved in the
     * dashboard. They are ranked by:
     * <ol>
     * <li><b>trust tier</b> — a tutor-endorsed or dashboard-verified answer beats a community-resolved one, so a student
     * marking a second answer resolving cannot demote a tutor-verified entry;</li>
     * <li><b>resolving</b> — within a tier, an answer marked as resolving the thread beats a dashboard-verified Iris answer
     * nobody marked;</li>
     * <li><b>recency</b>, then the id.</li>
     * </ol>
     * Answers by authors whose words may not be stored are not candidates at all.
     */
    private Optional<AnchorCandidate> selectAnchor(List<AnswerPost> answers, Map<Long, Boolean> tutorEndorsed) {
        List<AnchorCandidate> candidates = new ArrayList<>();
        for (AnswerPost answer : answers) {
            if (isExcludedAuthor(answer.getAuthor())) {
                continue;
            }
            boolean resolving = Boolean.TRUE.equals(answer.doesResolvePost());
            // The human-verifier check belongs in the filter: an Iris answer published automatically is also isVerified().
            boolean dashboardVerified = isDashboardVerifiedIrisAnswer(answer);
            if (!resolving && !dashboardVerified) {
                continue;
            }
            boolean tutor = dashboardVerified || tutorEndorsed.getOrDefault(answer.getId(), false);
            candidates.add(new AnchorCandidate(answer, tutor ? EndorsementTier.TUTOR : EndorsementTier.COMMUNITY, resolving));
        }
        return candidates.stream().max(Comparator.comparing(AnchorCandidate::tier).thenComparing(AnchorCandidate::resolving)
                .thenComparing(candidate -> candidate.answer().getCreationDate()).thenComparing(candidate -> candidate.answer().getId()));
    }

    /**
     * Whether a tutor approved this Iris answer in the verification dashboard. An Iris answer published automatically on
     * a high confidence score is also {@code verified}, but records no human verifier.
     */
    private boolean isDashboardVerifiedIrisAnswer(AnswerPost answer) {
        return isBot(answer.getAuthor()) && answer.isVerified() && answerPostRepository.hasHumanVerifier(answer.getId());
    }

    /**
     * Whether each resolving answer of the thread was marked resolving by someone with teaching authority in the course.
     * The role is resolved now rather than stored with the endorsement: someone who has since left the course's staff no
     * longer lends their answers the tutor tier.
     */
    private Map<Long, Boolean> loadTutorEndorsements(Post fullPost, Course course) {
        Map<String, Boolean> tutorByLogin = new HashMap<>();
        Map<Long, Boolean> endorsements = new HashMap<>();
        for (ResolvingAnswerEndorserDTO endorser : answerPostRepository.findResolvingAnswerEndorsersByPostId(fullPost.getId())) {
            boolean tutor = tutorByLogin.computeIfAbsent(endorser.endorserLogin(), login -> authCheckService.isAtLeastTeachingAssistantInCourse(login, course.getId()));
            endorsements.put(endorser.answerPostId(), tutor);
        }
        return endorsements;
    }

    /**
     * The provenance label of an entry: who signed off on the anchor. A tutor approving an Iris draft in the dashboard
     * yields {@code IRIS_AUTO}, or {@code IRIS_CORRECTED} when the text was edited; a tutor marking an answer resolving
     * yields {@code TUTOR_WRITTEN}, or {@code IRIS_AUTO} for an Iris answer published on its own, for which the tutor's
     * mark is the first human sign-off. Without a tutor's sign-off nothing is tutor-verified.
     */
    private PyrisCourseMemorySource sourceOf(AnchorCandidate anchor) {
        AnswerPost answer = anchor.answer();
        if (isDashboardVerifiedIrisAnswer(answer)) {
            return answer.getUpdatedDate() != null ? PyrisCourseMemorySource.IRIS_CORRECTED : PyrisCourseMemorySource.IRIS_AUTO;
        }
        if (anchor.tier() != EndorsementTier.TUTOR) {
            return PyrisCourseMemorySource.THREAD_RESOLVED;
        }
        return isBot(answer.getAuthor()) ? PyrisCourseMemorySource.IRIS_AUTO : PyrisCourseMemorySource.TUTOR_WRITTEN;
    }

    /**
     * Dispatches the ingestion of a freshly read thread under the given version.
     */
    private void ingest(Post fullPost, AnchorCandidate anchor, Course course, @Nullable User actor, long version) {
        AnswerPost anchorAnswer = anchor.answer();
        PyrisCourseMemorySource source = sourceOf(anchor);
        List<PyrisCourseMemoryThreadMessageDTO> thread = buildThread(fullPost, course, anchorAnswer.getId());
        // Every tutor-verified source stores the anchor's text exactly as the tutor read it.
        String existingAnswer = source == PyrisCourseMemorySource.THREAD_RESOLVED ? null : withoutLogins(anchorAnswer.getContent());
        String verifiedAt = source == PyrisCourseMemorySource.THREAD_RESOLVED ? null
                : isoInstant(isDashboardVerifiedIrisAnswer(anchorAnswer) ? anchorAnswer.getVerifiedAt() : anchorAnswer.getResolvedAt());

        String conversationId = String.valueOf(fullPost.getConversation().getId());
        String postId = String.valueOf(fullPost.getId());
        String messageId = String.valueOf(anchorAnswer.getId());
        String actorLogin = loginOf(actor);

        String jobToken = pyrisJobService.addCourseMemoryIngestionWebhookJob(course.getId(), conversationId, postId, messageId, actorLogin, CourseMemoryOperation.INGEST);
        var settings = executionSettings(jobToken, resolveThreadAiSelection(fullPost));
        // The real value rather than a constant: Pyris fails closed on this flag, so it stays a last line of defence.
        boolean isPublicChannel = fullPost.getConversation() instanceof Channel channel && (channel.getIsPublic() || channel.getIsCourseWide());
        var executionDTO = new PyrisWebhookCourseMemoryIngestionExecutionDTO(settings, course.getId(), conversationId, postId, messageId, version, source, isPublicChannel, thread,
                verifiedAt, existingAnswer);

        log.info("Ingesting course memory for thread {} (source={}, anchor={}, version={}) in course {}", postId, source, messageId, version, course.getId());
        notifyActor(actorLogin, IrisCourseMemoryStatusDTO.triggered(CourseMemoryOperation.INGEST, course.getId(), postId));
        boolean dispatched = pyrisConnectorService.executeCourseMemoryIngestionWebhook(executionDTO);
        notifyIfDispatchFailed(dispatched, jobToken, actorLogin, CourseMemoryOperation.INGEST, course.getId(), postId);
    }

    /**
     * Retracts a thread's entry with a tombstone carrying {@code version}. Safe to send when no entry exists.
     */
    private void retract(long courseId, long postId, long version, @Nullable User actor) {
        String postIdString = String.valueOf(postId);
        String actorLogin = loginOf(actor);
        // Deletion runs no model, so the selection is immaterial; it only has to be a valid value.
        String jobToken = pyrisJobService.addCourseMemoryIngestionWebhookJob(courseId, null, postIdString, null, actorLogin, CourseMemoryOperation.DELETE);
        var settings = executionSettings(jobToken, AiSelectionDecision.CLOUD_AI);

        log.info("Retracting course memory of thread {} in course {} (version={})", postIdString, courseId, version);
        notifyActor(actorLogin, IrisCourseMemoryStatusDTO.triggered(CourseMemoryOperation.DELETE, courseId, postIdString));
        boolean dispatched = pyrisConnectorService.executeCourseMemoryDeletionWebhook(new PyrisWebhookCourseMemoryDeletionExecutionDTO(settings, courseId, postIdString, version));
        notifyIfDispatchFailed(dispatched, jobToken, actorLogin, CourseMemoryOperation.DELETE, courseId, postIdString);
    }

    /**
     * Closes out a run the dispatch never started. {@code TRIGGERED} has already been pushed, and a request that never
     * reached Pyris produces no status callback.
     */
    private void notifyIfDispatchFailed(boolean dispatched, String jobToken, @Nullable String actorLogin, CourseMemoryOperation operation, long courseId, String postId) {
        if (dispatched) {
            return;
        }
        var job = pyrisJobService.getJob(jobToken);
        if (job != null) {
            pyrisJobService.removeJob(job);
        }
        notifyActor(actorLogin, IrisCourseMemoryStatusDTO.failed(operation, courseId, postId, "Could not reach Pyris"));
    }

    private void notifyActor(@Nullable String actorLogin, IrisCourseMemoryStatusDTO status) {
        if (actorLogin == null) {
            return;
        }
        irisWebsocketService.send(actorLogin, COURSE_MEMORY.at(status.courseId()), status);
    }

    private PyrisPipelineExecutionSettingsDTO executionSettings(String jobToken, AiSelectionDecision aiSelection) {
        // Deliberately not the course's Iris variant: the course memory pipelines define only "default".
        return new PyrisPipelineExecutionSettingsDTO(jobToken, aiSelection, artemisBaseUrl, COURSE_MEMORY_PIPELINE_VARIANT, IrisSupportLevel.MODERATE.jsonValue());
    }

    /**
     * Resolves which inference environment may see this thread: a single {@code LOCAL_AI} participant whose content is
     * forwarded pins the whole run to on-premise inference. Bot authors carry no preference; redacted authors forward no
     * content, so they have no preference left to honour.
     */
    private AiSelectionDecision resolveThreadAiSelection(Post fullPost) {
        Set<Long> userIds = Stream.concat(Stream.of(fullPost.getAuthor()), visibleAnswers(fullPost).stream().map(AnswerPost::getAuthor)).filter(Objects::nonNull)
                .filter(author -> !author.isBot()).map(User::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        boolean anyLocal = userAiPreferenceService.findDecisions(userIds).values().stream().anyMatch(AiSelectionDecision.LOCAL_AI::equals);
        return anyLocal ? AiSelectionDecision.LOCAL_AI : AiSelectionDecision.CLOUD_AI;
    }

    /**
     * Reads the thread with its answers and authors; empty if the post no longer exists.
     */
    private Optional<Post> fetchThread(long postId) {
        return conversationMessageRepository.findByPostIdsWithEagerRelationships(List.of(postId)).stream().findFirst();
    }

    /**
     * The thread's answers, oldest&rarr;newest, excluding unverified Iris replies so unapproved AI drafts never enter the
     * memory thread.
     */
    private List<AnswerPost> visibleAnswers(Post post) {
        return post.getAnswers().stream().filter(answerPost -> !answerPost.isUnverifiedIrisReply())
                .sorted(Comparator.comparing(Posting::getCreationDate).thenComparing(Posting::getId)).toList();
    }

    /**
     * Builds the thread (ordered oldest&rarr;newest): the question first, then its visible answers. Each message states
     * explicitly whether it is the anchor and whether it resolves the post. Messages by excluded authors keep their slot
     * but carry no content and no flags.
     */
    private List<PyrisCourseMemoryThreadMessageDTO> buildThread(Post fullPost, Course course, Long anchorAnswerId) {
        List<Posting> postings = new ArrayList<>();
        postings.add(fullPost);
        postings.addAll(visibleAnswers(fullPost));

        Map<Long, UserRole> rolesByUserId = resolveThreadAuthorRoles(postings, course);

        List<PyrisCourseMemoryThreadMessageDTO> thread = new ArrayList<>();
        for (Posting posting : postings) {
            User author = posting.getAuthor();
            boolean botAuthor = isBot(author);
            String authorRole = resolveAuthorRole(author, botAuthor, rolesByUserId);
            String createdAt = isoInstant(posting.getCreationDate());
            boolean isAnswer = posting instanceof AnswerPost;
            String id = (isAnswer ? ANSWER_ID_PREFIX : POST_ID_PREFIX) + posting.getId();
            boolean isAnchor = isAnswer && posting.getId().equals(anchorAnswerId);
            boolean resolvesPost = posting instanceof AnswerPost answerPost && Boolean.TRUE.equals(answerPost.doesResolvePost());

            boolean redacted = isExcludedAuthor(author);
            String content = redacted ? "" : withoutLogins(posting.getContent());
            if (redacted) {
                isAnchor = false;
                resolvesPost = false;
            }
            thread.add(new PyrisCourseMemoryThreadMessageDTO(id, authorRole, content, createdAt, botAuthor, isAnchor, resolvesPost, redacted));
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
        // The extractor's vocabulary is a trust tier (student / tutor / iris), not a course role.
        return switch (rolesByUserId.getOrDefault(author.getId(), UserRole.USER)) {
            case INSTRUCTOR, TUTOR -> "tutor";
            case USER -> "student";
        };
    }

    private static boolean isBot(@Nullable User user) {
        return user != null && user.isBot();
    }

    private static @Nullable String loginOf(@Nullable User user) {
        return user != null ? user.getLogin() : null;
    }

    private static @Nullable String isoInstant(@Nullable ZonedDateTime timestamp) {
        return timestamp != null ? timestamp.toInstant().toString() : null;
    }

    /**
     * Rewrites structured user mentions to their display name, so no login reaches Course Memory.
     */
    static @Nullable String withoutLogins(@Nullable String content) {
        return content == null ? null : USER_MENTION.matcher(content).replaceAll(match -> Matcher.quoteReplacement(match.group(1)));
    }

    /**
     * Whether nothing a user wrote may be stored: they opted out of AI, their account is not active (a permanent deletion
     * deactivates the account first), or they no longer exist. Bots are never excluded.
     */
    private boolean isExcludedAuthor(@Nullable User user) {
        if (user == null || user.getId() == null) {
            return true;
        }
        if (user.isBot()) {
            return false;
        }
        return !user.getActivated() || AiSelectionDecision.NO_AI.equals(userAiPreferenceService.findDecision(user.getId()));
    }

    /**
     * Resolves the course role of every thread author in a single query.
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
