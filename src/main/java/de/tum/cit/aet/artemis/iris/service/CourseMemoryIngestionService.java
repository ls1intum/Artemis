package de.tum.cit.aet.artemis.iris.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.Posting;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.repository.ConversationMessageRepository;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisCourseMemorySource;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisCourseMemoryThreadMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisWebhookCourseMemoryIngestionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;

/**
 * Builds and dispatches Course Memory ingestion requests to Pyris.
 * <p>
 * Two event-driven triggers feed it:
 * <ul>
 * <li><b>Trigger A</b> ({@link #ingestVerifiedAnswer}) – a tutor approved or edited an Iris draft in
 * the verification dashboard ({@code IRIS_AUTO} / {@code IRIS_CORRECTED}).</li>
 * <li><b>Trigger B</b> ({@link #ingestResolvedThread}) – a thread was marked resolved with a
 * tutor/student answer that never went through verification ({@code THREAD_RESOLVED}).</li>
 * </ul>
 * Ingestion is best-effort and only fires for public/course-wide channels of Iris-enabled courses;
 * the {@code messageId} is the stable answer-post id so corrections overwrite in place on Pyris.
 */
@Service
@Lazy
@Conditional(IrisEnabled.class)
public class CourseMemoryIngestionService {

    private static final Logger log = LoggerFactory.getLogger(CourseMemoryIngestionService.class);

    private final PyrisConnectorService pyrisConnectorService;

    private final PyrisJobService pyrisJobService;

    private final IrisSettingsService irisSettingsService;

    private final UserRepository userRepository;

    private final ConversationMessageRepository conversationMessageRepository;

    @Value("${server.url}")
    private String artemisBaseUrl;

    public CourseMemoryIngestionService(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService, IrisSettingsService irisSettingsService,
            UserRepository userRepository, ConversationMessageRepository conversationMessageRepository) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.pyrisJobService = pyrisJobService;
        this.irisSettingsService = irisSettingsService;
        this.userRepository = userRepository;
        this.conversationMessageRepository = conversationMessageRepository;
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
    public void ingestVerifiedAnswer(AnswerPost verifiedAnswer, boolean edited, User verifier, Course course) {
        var source = edited ? PyrisCourseMemorySource.IRIS_CORRECTED : PyrisCourseMemorySource.IRIS_AUTO;
        String existingAnswer = edited ? verifiedAnswer.getContent() : null;
        String verifiedAt = verifiedAnswer.getVerifiedAt() != null ? verifiedAnswer.getVerifiedAt().toInstant().toString() : null;
        ingest(verifiedAnswer, course, source, verifier != null ? verifier.getLogin() : null, verifiedAt, existingAnswer);
    }

    /**
     * Trigger B: a thread was marked resolved by a tutor/student answer. Skipped for Iris-authored
     * answers, which are owned by Trigger A (verification), to avoid double ingestion.
     *
     * @param resolvingAnswer the answer post that resolves the thread
     * @param course          the course the thread belongs to
     */
    public void ingestResolvedThread(AnswerPost resolvingAnswer, Course course) {
        if (resolvingAnswer.getAuthor() != null && resolvingAnswer.getAuthor().isBot()) {
            log.debug("Skipping course memory thread-resolved ingestion for Iris-authored answer {} (owned by verification trigger)", resolvingAnswer.getId());
            return;
        }
        ingest(resolvingAnswer, course, PyrisCourseMemorySource.THREAD_RESOLVED, null, null, null);
    }

    private void ingest(AnswerPost answer, Course course, PyrisCourseMemorySource source, @Nullable String verifiedBy, @Nullable String verifiedAt,
            @Nullable String existingAnswer) {
        Post post = answer.getPost();
        if (post == null) {
            return;
        }
        Conversation conversation = post.getConversation();
        if (!(conversation instanceof Channel channel) || !(channel.getIsPublic() || channel.getIsCourseWide())) {
            log.debug("Skipping course memory ingestion for answer {}: not a public/course-wide channel", answer.getId());
            return;
        }
        if (!irisSettingsService.isEnabledForCourse(course)) {
            log.debug("Skipping course memory ingestion for answer {}: Iris is not enabled for course {}", answer.getId(), course.getId());
            return;
        }

        List<PyrisCourseMemoryThreadMessageDTO> thread = buildThread(post, course);
        String conversationId = String.valueOf(conversation.getId());
        String messageId = String.valueOf(answer.getId());

        String jobToken = pyrisJobService.addCourseMemoryIngestionWebhookJob(course.getId(), conversationId, messageId);
        String variant = irisSettingsService.getSettingsForCourse(course).variant().jsonValue();
        var settings = new PyrisPipelineExecutionSettingsDTO(jobToken, AiSelectionDecision.CLOUD_AI, artemisBaseUrl, variant);

        var executionDTO = new PyrisWebhookCourseMemoryIngestionExecutionDTO(settings, List.of(), course.getId(), conversationId, messageId, source, true, thread, verifiedBy,
                verifiedAt, existingAnswer);

        log.debug("Ingesting course memory for message {} (source={}) in course {}", messageId, source, course.getId());
        pyrisConnectorService.executeCourseMemoryIngestionWebhook(executionDTO);
    }

    /**
     * Builds the thread (ordered oldest&rarr;newest) for the given root post: the question first, then
     * its visible answers sorted by creation date. Unverified Iris replies are excluded so unapproved
     * AI drafts never enter the memory thread.
     */
    private List<PyrisCourseMemoryThreadMessageDTO> buildThread(Post post, Course course) {
        // Re-fetch the thread with authors eagerly joined so role resolution does not rely on lazily-loaded
        // sibling authors (which may be detached once the originating request transaction has closed).
        Post fullPost = conversationMessageRepository.findByPostIdsWithEagerRelationships(List.of(post.getId())).stream().findFirst().orElse(post);

        List<Posting> postings = new ArrayList<>();
        postings.add(fullPost);
        fullPost.getAnswers().stream().filter(answerPost -> !answerPost.isUnverifiedIrisReply()).sorted(Comparator.comparing(Posting::getCreationDate)).forEach(postings::add);

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
            thread.add(new PyrisCourseMemoryThreadMessageDTO(String.valueOf(posting.getId()), authorRole, posting.getContent(), createdAt, isBot));
        }
        return thread;
    }

    /**
     * Resolves which thread authors are at least teaching assistants in the course, loading the
     * authors with their groups in a single query and checking course group membership directly
     * (avoids touching lazily-loaded authorities outside a session).
     */
    private Map<Long, Boolean> resolveTutorRoles(List<Posting> postings, Course course) {
        List<Long> authorIds = postings.stream().map(Posting::getAuthor).filter(author -> author != null && !author.isBot()).map(User::getId).distinct().toList();
        Map<Long, Boolean> isTutorByUserId = new HashMap<>();
        if (authorIds.isEmpty()) {
            return isTutorByUserId;
        }
        String taGroup = course.getTeachingAssistantGroupName();
        String editorGroup = course.getEditorGroupName();
        String instructorGroup = course.getInstructorGroupName();
        for (User author : userRepository.findUsersWithGroupsByIdIn(authorIds)) {
            var groups = author.getGroups();
            boolean isAtLeastTutor = groups.contains(taGroup) || groups.contains(editorGroup) || groups.contains(instructorGroup);
            isTutorByUserId.put(author.getId(), isAtLeastTutor);
        }
        return isTutorByUserId;
    }
}
