package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.ForwardedMessage;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.Posting;
import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.ForwardedMessageRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.auth.ChannelAuthorizationService;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;

/** Separates sharing selected message content from access to its original conversation. */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ForwardedMessageService {

    private final PostRepository postRepository;

    private final AnswerPostRepository answerPostRepository;

    private final ForwardedMessageRepository forwardedMessageRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final ChannelAuthorizationService channelAuthorizationService;

    public ForwardedMessageService(PostRepository postRepository, AnswerPostRepository answerPostRepository, ForwardedMessageRepository forwardedMessageRepository,
            AuthorizationCheckService authorizationCheckService, ChannelAuthorizationService channelAuthorizationService) {
        this.postRepository = postRepository;
        this.answerPostRepository = answerPostRepository;
        this.forwardedMessageRepository = forwardedMessageRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.channelAuthorizationService = channelAuthorizationService;
    }

    /**
     * Finds messages the user can read in their original conversation, including channel and Iris visibility rules.
     *
     * @param sources  messages to check
     * @param type     source message type
     * @param courseId course containing the messages
     * @param user     requesting user
     * @return IDs whose original conversation the user can access
     */
    public Set<Long> findDirectlyAccessibleIds(Collection<? extends Posting> sources, PostingType type, Long courseId, User user) {
        Set<Long> ids = sources.stream().map(Posting::getId).collect(Collectors.toSet());
        Set<Long> accessible = type == PostingType.POST ? postRepository.findAccessiblePostIds(ids, user.getId())
                : answerPostRepository.findAccessibleAnswerPostIds(ids, user.getId());
        boolean isTutor = authorizationCheckService.isAtLeastTeachingAssistantInCourse(courseId);
        return sources.stream().filter(source -> accessible.contains(source.getId()) && isVisible(source, isTutor)).map(Posting::getId).collect(Collectors.toSet());
    }

    /**
     * Finds source content shared into a destination the user can currently read. A hidden destination never grants access.
     *
     * @param sourceIds source IDs to check
     * @param type      source message type
     * @param courseId  course containing both source and destination
     * @param user      requesting user
     * @return source IDs readable through existing forwards
     */
    public Set<Long> findAccessibleSourceIds(Collection<Long> sourceIds, PostingType type, Long courseId, User user) {
        boolean isTutor = authorizationCheckService.isAtLeastTeachingAssistantInCourse(courseId);
        return forwardedMessageRepository.findAccessibleSourceIds(sourceIds, type, courseId, user.getId(), isTutor, ZonedDateTime.now(), User.IRIS_BOT_LOGIN);
    }

    /**
     * Validates and saves a forward. Its author must be able to read the source and write their own destination message in the same course.
     * A received forward is a valid source for onward sharing without granting access to the original conversation.
     * Missing and inaccessible source or destination IDs produce the same forbidden response.
     *
     * @param forward the requested forward, with exactly one destination
     * @param user    forwarding user
     * @return persisted forward
     */
    public ForwardedMessage create(ForwardedMessage forward, User user) {
        try {
            return createWithAccessChecks(forward, user);
        }
        catch (EntityNotFoundException | AccessForbiddenException exception) {
            // Missing and inaccessible IDs must have the same status and detail, including course and channel denials.
            throw new AccessForbiddenException();
        }
    }

    private ForwardedMessage createWithAccessChecks(ForwardedMessage forward, User user) {
        Posting source = forward.getSourceType() == PostingType.POST ? postRepository.findByIdElseThrow(forward.getSourceId())
                : answerPostRepository.findByIdElseThrow(forward.getSourceId());
        Posting destination = forward.getDestinationPost() != null ? postRepository.findByIdElseThrow(forward.getDestinationPost().getId())
                : answerPostRepository.findByIdElseThrow(forward.getDestinationAnswerPost().getId());
        Conversation sourceConversation = conversationOf(source);
        Conversation destinationConversation = conversationOf(destination);
        var course = destinationConversation.getCourse();
        Long courseId = course.getId();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        PostingType destinationType = destination instanceof Post ? PostingType.POST : PostingType.ANSWER;
        var sharingConfiguration = course.getCourseInformationSharingConfiguration();
        boolean destinationEnabled = destinationConversation instanceof Channel ? sharingConfiguration.isAnyCommunicationEnabled() : sharingConfiguration.isMessagingEnabled();
        if (!destination.getAuthor().getId().equals(user.getId()) || !destinationEnabled
                || !findDirectlyAccessibleIds(List.of(destination), destinationType, courseId, user).contains(destination.getId())) {
            throw new AccessForbiddenException("You may only attach a forwarded message to your own accessible message.");
        }
        if (destinationConversation instanceof Channel channel) {
            if (destinationType == PostingType.POST) {
                channelAuthorizationService.isAllowedToCreateNewPostInChannel(channel, user);
            }
            else {
                channelAuthorizationService.isAllowedToCreateNewAnswerPostInChannel(channel, user);
            }
        }
        Long sourceCourseId = sourceConversation.getCourse().getId();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, sourceConversation.getCourse(), user);
        // Pending Iris replies are not published to students, including through onward forwards.
        if (source instanceof AnswerPost answer && answer.isUnverifiedIrisReply() && !authorizationCheckService.isAtLeastTeachingAssistantInCourse(sourceCourseId)) {
            throw new AccessForbiddenException("The source reply has not been published.");
        }
        if (!findDirectlyAccessibleIds(List.of(source), forward.getSourceType(), sourceCourseId, user).contains(source.getId())
                && !findAccessibleSourceIds(Set.of(source.getId()), forward.getSourceType(), sourceCourseId, user).contains(source.getId())) {
            throw new AccessForbiddenException("You do not have access to the source message.");
        }
        // A course mismatch may be reported only after both messages are known to be accessible.
        if (!sourceCourseId.equals(courseId)) {
            throw new BadRequestAlertException("Forwarded messages must stay within the same course", "forwardedMessage", "invalidCourse");
        }
        if (destination instanceof Post post) {
            forward.setDestinationPost(post);
        }
        else {
            forward.setDestinationAnswerPost((AnswerPost) destination);
        }
        return forwardedMessageRepository.save(forward);
    }

    private boolean isVisible(Posting posting, boolean isTutor) {
        if (isTutor) {
            return true;
        }
        if (posting instanceof AnswerPost answer && answer.isUnverifiedIrisReply()) {
            return false;
        }
        return !(conversationOf(posting) instanceof Channel channel) || channel.isVisibleToStudents();
    }

    private Conversation conversationOf(Posting posting) {
        Conversation conversation = posting instanceof Post post ? post.getConversation() : ((AnswerPost) posting).getPost().getConversation();
        if (conversation == null) {
            throw new AccessForbiddenException();
        }
        return conversation;
    }
}
