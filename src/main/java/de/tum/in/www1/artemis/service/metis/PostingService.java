package de.tum.in.www1.artemis.service.metis;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.CourseInformationSharingConfiguration;
import de.tum.in.www1.artemis.domain.metis.*;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.domain.notification.ConversationNotification;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.websocket.dto.metis.MetisCrudAction;
import de.tum.in.www1.artemis.web.websocket.dto.metis.PostDTO;

public abstract class PostingService {

    protected final CourseRepository courseRepository;

    protected final UserRepository userRepository;

    protected final ExerciseRepository exerciseRepository;

    protected final LectureRepository lectureRepository;

    protected final ConversationParticipantRepository conversationParticipantRepository;

    protected final AuthorizationCheckService authorizationCheckService;

    private final WebsocketMessagingService websocketMessagingService;

    protected static final String METIS_POST_ENTITY_NAME = "metis.post";

    private static final String METIS_WEBSOCKET_CHANNEL_PREFIX = "/topic/metis/";

    protected PostingService(CourseRepository courseRepository, UserRepository userRepository, ExerciseRepository exerciseRepository, LectureRepository lectureRepository,
            AuthorizationCheckService authorizationCheckService, WebsocketMessagingService websocketMessagingService,
            ConversationParticipantRepository conversationParticipantRepository) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.lectureRepository = lectureRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.websocketMessagingService = websocketMessagingService;
        this.conversationParticipantRepository = conversationParticipantRepository;
    }

    /**
     * Helper method to prepare the post included in the websocket message and initiate the broadcasting
     *
     * @param updatedAnswerPost answer post that was updated
     * @param course            course the answer post belongs to
     * @param notification      notification for the update (can be null)
     */
    protected void preparePostAndBroadcast(AnswerPost updatedAnswerPost, Course course, Notification notification) {
        // we need to explicitly (and newly) add the updated answer post to the answers of the broadcast post to share up-to-date information
        Post updatedPost = updatedAnswerPost.getPost();
        // remove and add operations on sets identify an AnswerPost by its id; to update a certain property of an existing answer post,
        // we need to remove the existing AnswerPost (based on unchanged id in updatedAnswerPost) and add the updatedAnswerPost afterwards
        updatedPost.removeAnswerPost(updatedAnswerPost);
        updatedPost.addAnswerPost(updatedAnswerPost);
        broadcastForPost(new PostDTO(updatedPost, MetisCrudAction.UPDATE, notification), course.getId(), null, null);
    }

    /**
     * Broadcasts a posting related event in a course under a specific topic via websockets
     *
     * @param postDTO        object including the affected post as well as the action
     * @param courseId       the id of the course the posting belongs to
     * @param recipients     the recipients for this broadcast, can be null
     * @param mentionedUsers the users mentioned in the message, can be null
     */
    protected void broadcastForPost(PostDTO postDTO, Long courseId, Set<ConversationNotificationRecipientSummary> recipients, Set<User> mentionedUsers) {
        // reduce the payload of the websocket message: this is important to avoid overloading the involved subsystems
        Conversation postConversation = postDTO.post().getConversation();
        if (postConversation != null) {
            postConversation.hideDetails();
            if (postDTO.post().getAnswers() != null) {
                postDTO.post().getAnswers().forEach(answerPost -> answerPost.setPost(new Post(answerPost.getPost().getId())));
            }

            String courseConversationTopic = METIS_WEBSOCKET_CHANNEL_PREFIX + "courses/" + courseId;
            if (postConversation instanceof Channel channel && channel.getIsCourseWide()) {
                websocketMessagingService.sendMessage(courseConversationTopic, postDTO);
            }
            else {
                if (recipients == null) {
                    // send to all participants of the conversation
                    recipients = getConversationParticipantsAsSummaries(postConversation);
                }
                recipients.forEach(recipient -> websocketMessagingService.sendMessage("/topic/user/" + recipient.userId() + "/notifications/conversations",
                        new PostDTO(postDTO.post(), postDTO.action(), getNotificationForRecipient(recipient, postDTO.notification(), mentionedUsers))));
            }
        }
        else if (postDTO.post().getPlagiarismCase() != null) {
            String plagiarismCaseTopic = METIS_WEBSOCKET_CHANNEL_PREFIX + "plagiarismCase/" + postDTO.post().getPlagiarismCase().getId();
            websocketMessagingService.sendMessage(plagiarismCaseTopic, postDTO);
        }
    }

    /**
     * Gets the participants of a conversation and maps them to ConversationNotificationRecipientSummary records
     *
     * @param postConversation the conversation
     * @return Set of ConversationNotificationRecipientSummary
     */
    private Set<ConversationNotificationRecipientSummary> getConversationParticipantsAsSummaries(Conversation postConversation) {
        return conversationParticipantRepository.findConversationParticipantByConversationId(postConversation.getId()).stream()
                .map(participant -> new ConversationNotificationRecipientSummary(participant.getUser(), participant.getIsHidden() != null && participant.getIsHidden(), false))
                .collect(Collectors.toSet());
    }

    /**
     * Determines the participants of a conversation that should receive the new message.
     *
     * @param conversation conversation the participants are supposed be retrieved
     * @return users that should receive the new message
     */
    protected Stream<ConversationNotificationRecipientSummary> getNotificationRecipients(Conversation conversation) {
        if (conversation instanceof Channel channel && channel.getIsCourseWide()) {
            Course course = conversation.getCourse();
            return userRepository.findAllWebSocketRecipientsInCourseForConversation(conversation.getId(), course.getStudentGroupName(), course.getTeachingAssistantGroupName(),
                    course.getEditorGroupName(), course.getInstructorGroupName()).stream();
        }

        return conversationParticipantRepository.findConversationParticipantWithUserGroupsByConversationId(conversation.getId()).stream()
                .map(participant -> new ConversationNotificationRecipientSummary(participant.getUser(), participant.getIsHidden() != null && participant.getIsHidden(),
                        authorizationCheckService.isAtLeastTeachingAssistantInCourse(conversation.getCourse(), participant.getUser())));
    }

    protected Course preCheckUserAndCourseForMessaging(User user, Long courseId) {
        final Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        if (course.getCourseInformationSharingConfiguration() == CourseInformationSharingConfiguration.DISABLED) {
            throw new BadRequestAlertException("Communication and messaging is disabled for this course", getEntityName(), "400", true);
        }
        return course;
    }

    protected void preCheckUserAndCourseForCommunicationOrMessaging(User user, Course course) {
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        if (course.getCourseInformationSharingConfiguration() == CourseInformationSharingConfiguration.DISABLED) {
            throw new BadRequestAlertException("Communication and messaging is disabled for this course", getEntityName(), "400", true);
        }
    }

    /**
     * helper method that fetches groups and authorities of all posting authors in a list of Posts
     *
     * @param postsInCourse list of posts whose authors are populated with their groups, authorities, and authorRole
     */
    protected void setAuthorRoleOfPostings(List<Post> postsInCourse) {
        // prepares a unique set of userIds that authored the current list of postings
        Set<Long> userIds = new HashSet<>();
        postsInCourse.forEach(post -> {
            // needs to handle posts created by SingleUserNotificationService.notifyUserAboutNewPlagiarismCaseBySystem
            if (post.getAuthor() != null) {
                userIds.add(post.getAuthor().getId());
            }
            post.getAnswers().forEach(answerPost -> userIds.add(answerPost.getAuthor().getId()));
        });

        // fetches and sets groups and authorities of all posting authors involved, which are used to display author role icon in the posting header
        // converts fetched set to hashmap type for performant matching of authors
        Map<Long, User> authors = userRepository.findAllWithGroupsAndAuthoritiesByIdIn(userIds).stream().collect(Collectors.toMap(DomainObject::getId, Function.identity()));

        // sets respective author role to display user authority icon on posting headers
        postsInCourse.stream()
                // needs to handle posts created by SingleUserNotificationService.notifyUserAboutNewPlagiarismCaseBySystem
                .filter(post -> post.getAuthor() != null).forEach(post -> {
                    post.setAuthor(authors.get(post.getAuthor().getId()));
                    setAuthorRoleForPosting(post, post.getCoursePostingBelongsTo());
                    post.getAnswers().forEach(answerPost -> {
                        answerPost.setAuthor(authors.get(answerPost.getAuthor().getId()));
                        setAuthorRoleForPosting(answerPost, answerPost.getCoursePostingBelongsTo());
                    });
                });
    }

    /**
     * helper method that assigns authorRoles of postings in accordance to user groups and authorities
     *
     * @param posting       posting to assign authorRole
     * @param postingCourse course that the post belongs to, must be explicitly fetched and provided to handle new post creation case
     */
    protected void setAuthorRoleForPosting(Posting posting, Course postingCourse) {
        if (authorizationCheckService.isAtLeastInstructorInCourse(postingCourse, posting.getAuthor())) {
            posting.setAuthorRole(UserRole.INSTRUCTOR);
        }
        else if (authorizationCheckService.isTeachingAssistantInCourse(postingCourse, posting.getAuthor())
                || authorizationCheckService.isEditorInCourse(postingCourse, posting.getAuthor())) {
            posting.setAuthorRole(UserRole.TUTOR);
        }
        else {
            posting.setAuthorRole(UserRole.USER);
        }
    }

    protected abstract String getEntityName();

    /**
     * Gets the list of logins for users mentioned in a posting.
     * Throws an exception, if a mentioned user is not part of the course.
     *
     * @param course         course of the posting
     * @param postingContent content of the posting
     * @return set of mentioned users
     */
    protected Set<User> parseUserMentions(@NotNull Course course, String postingContent) {
        // Define a regular expression to match text enclosed in [user]...[/user] tags, along with login inside parentheses () within those tags.
        // It makes use of the possessive quantifier "*+" to avoid backtracking and increase performance.
        // Explanation:
        // - "\\[user\\]" matches the literal string "[user]".
        // - "([^\\[\\]()]*+)" captures any characters that are not '[', ']', '(', or ')' zero or more times. This captures the full name the user mention.
        // - "\\(?" matches the literal '(' character.
        // - "([^\\[\\]()]*+)" captures any characters that are not '[', ']', '(', or ')' zero or more times. This captures the content within parentheses.
        // - "\\)?" matches the literal ')' character.
        // - "\\[/user\\]" matches the literal string "[/user]".
        String regex = "\\[user\\]([^\\[\\]()]*+)\\(?([^\\[\\]()]*+)\\)?\\[/user\\]";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(Optional.ofNullable(postingContent).orElse(""));

        Map<String, String> matches = new HashMap<>();

        // Find and save all matches in the list
        while (matcher.find()) {
            String fullName = matcher.group(1);
            String userLogin = matcher.group(2);

            matches.put(userLogin, fullName);
        }

        Set<User> mentionedUsers = userRepository.findAllByLogins(matches.keySet());

        if (mentionedUsers.size() != matches.size()) {
            throw new BadRequestAlertException("At least one of the mentioned users does not exist", METIS_POST_ENTITY_NAME, "invalidUserMention");
        }

        mentionedUsers.forEach(user -> {
            if (!user.getName().equals(matches.get(user.getLogin()))) {
                throw new BadRequestAlertException("The name provided for user " + user.getLogin() + " does not match the user's full name " + user.getName(),
                        METIS_POST_ENTITY_NAME, "invalidUserMention");
            }

            if (!authorizationCheckService.isAtLeastStudentInCourse(course, user)) {
                throw new BadRequestAlertException("The user " + user.getLogin() + " is not a member of the course", METIS_POST_ENTITY_NAME, "invalidUserMention");
            }
        });

        return mentionedUsers;
    }

    /**
     * Returns null of the recipient should not receive a notification
     *
     * @param recipient      the recipient
     * @param notification   the potential notification for the recipient
     * @param mentionedUsers set of mentioned users in the message
     * @return null or the provided notification
     */
    private Notification getNotificationForRecipient(ConversationNotificationRecipientSummary recipient, Notification notification, Set<User> mentionedUsers) {
        if (recipient.isConversationHidden() && notification instanceof ConversationNotification && !mentionedUsers.contains(new User(recipient.userId()))) {
            return null;
        }

        return notification;
    }
}
