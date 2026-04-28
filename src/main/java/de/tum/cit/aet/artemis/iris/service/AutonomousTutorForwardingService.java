package de.tum.cit.aet.artemis.iris.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisPostDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;

/**
 * Service that listens for new channel messages and forwards them to the autonomous tutor
 * pipeline in Pyris when the feature is enabled and the author has not opted out of AI.
 */
@Service
@Lazy
@Conditional(IrisEnabled.class)
public class AutonomousTutorForwardingService {

    private static final Logger log = LoggerFactory.getLogger(AutonomousTutorForwardingService.class);

    private final FeatureToggleService featureToggleService;

    private final IrisSettingsService irisSettingsService;

    private final PyrisPipelineService pyrisPipelineService;

    public AutonomousTutorForwardingService(FeatureToggleService featureToggleService, IrisSettingsService irisSettingsService, PyrisPipelineService pyrisPipelineService) {
        this.featureToggleService = featureToggleService;
        this.irisSettingsService = irisSettingsService;
        this.pyrisPipelineService = pyrisPipelineService;
    }

    /**
     * Called when a new message is created in a conversation. If all conditions are met,
     * forwards the message to the autonomous tutor pipeline in Pyris.
     * <p>
     * Conditions:
     * <ul>
     * <li>The {@link Feature#AutonomousTutor} feature toggle is enabled.</li>
     * <li>Iris is enabled for the course.</li>
     * <li>The conversation is a {@link Channel} (not a group chat or direct message).</li>
     * <li>The author is not the Iris bot itself (prevents reply loops).</li>
     * <li>The author has not chosen {@link AiSelectionDecision#NO_AI}.</li>
     * </ul>
     *
     * @param post         the newly created message
     * @param conversation the conversation the message was posted in
     * @param course       the course the conversation belongs to
     */
    public void onNewMessage(Post post, Conversation conversation, Course course) {
        if (!featureToggleService.isFeatureEnabled(Feature.AutonomousTutor)) {
            log.debug("AutonomousTutor feature is disabled, skipping forwarding for post {}", post.getId());
            return;
        }

        if (!irisSettingsService.isEnabledForCourse(course)) {
            log.debug("Iris is not enabled for course {}, skipping autonomous tutor forwarding", course.getId());
            return;
        }

        if (!(conversation instanceof Channel)) {
            log.debug("Conversation {} is not a channel, skipping autonomous tutor forwarding", conversation.getId());
            return;
        }

        User author = post.getAuthor();

        if (author.isBot()) {
            log.debug("Skipping autonomous tutor forwarding for bot-authored post {}", post.getId());
            return;
        }

        if (AiSelectionDecision.NO_AI.equals(author.getSelectedLLMUsage())) {
            log.debug("User {} opted out of AI, skipping autonomous tutor forwarding for post {}", author.getId(), post.getId());
            return;
        }

        String variant = irisSettingsService.getSettingsForCourse(course).variant().jsonValue();
        log.debug("Forwarding post {} to autonomous tutor pipeline (variant={})", post.getId(), variant);

        pyrisPipelineService.executeAutonomousTutorPipeline(variant, new PyrisPostDTO(post), course, new PyrisUserDTO(author), null, null, null, stages -> {
        });
    }

    /**
     * Called when a new reply is created in a thread. If all conditions are met, forwards the thread
     * to the autonomous tutor pipeline in Pyris so Iris can respond to the follow-up question.
     * <p>
     * Forwarding rules:
     * <ul>
     * <li>All shared preconditions from {@link #onNewMessage} apply (feature toggle, Iris enabled, channel only, no bots).</li>
     * <li>The reply author must not have chosen {@link AiSelectionDecision#NO_AI} — no pipeline is triggered for their replies.</li>
     * <li>The parent post author must not have chosen {@link AiSelectionDecision#NO_AI} — Iris must have access to the thread root.</li>
     * </ul>
     * When forwarded, replies from No-AI students in the same thread are included as redacted placeholders
     * so Iris is aware those messages exist without seeing their content.
     *
     * @param answerPost   the newly created reply
     * @param parentPost   the parent post that was replied to (must include all answers, including the new reply)
     * @param conversation the conversation the thread belongs to
     * @param course       the course the conversation belongs to
     */
    public void onNewAnswerMessage(AnswerPost answerPost, Post parentPost, Conversation conversation, Course course) {
        if (!featureToggleService.isFeatureEnabled(Feature.AutonomousTutor)) {
            log.debug("AutonomousTutor feature is disabled, skipping forwarding for answer post {}", answerPost.getId());
            return;
        }

        if (!irisSettingsService.isEnabledForCourse(course)) {
            log.debug("Iris is not enabled for course {}, skipping autonomous tutor forwarding", course.getId());
            return;
        }

        if (!(conversation instanceof Channel)) {
            log.debug("Conversation {} is not a channel, skipping autonomous tutor forwarding", conversation.getId());
            return;
        }

        User author = answerPost.getAuthor();

        if (author.isBot()) {
            log.debug("Skipping autonomous tutor forwarding for bot-authored answer post {}", answerPost.getId());
            return;
        }

        if (AiSelectionDecision.NO_AI.equals(author.getSelectedLLMUsage())) {
            log.debug("User {} opted out of AI, skipping autonomous tutor forwarding for answer post {}", author.getId(), answerPost.getId());
            return;
        }

        User parentAuthor = parentPost.getAuthor();
        if (AiSelectionDecision.NO_AI.equals(parentAuthor.getSelectedLLMUsage())) {
            log.debug("Parent post {} author opted out of AI, skipping autonomous tutor forwarding for answer post {}", parentPost.getId(), answerPost.getId());
            return;
        }

        String variant = irisSettingsService.getSettingsForCourse(course).variant().jsonValue();
        log.debug("Forwarding answer post {} (thread {}) to autonomous tutor pipeline (variant={})", answerPost.getId(), parentPost.getId(), variant);

        pyrisPipelineService.executeAutonomousTutorPipeline(variant, new PyrisPostDTO(parentPost), course, new PyrisUserDTO(author), null, null, null, stages -> {
        });
    }
}
