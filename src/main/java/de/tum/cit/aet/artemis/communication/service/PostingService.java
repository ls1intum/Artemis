package de.tum.cit.aet.artemis.communication.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.ConversationNotificationRecipientSummary;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.Posting;
import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.UserRole;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.dto.MetisCrudAction;
import de.tum.cit.aet.artemis.communication.dto.PostBroadcastDTO;
import de.tum.cit.aet.artemis.communication.dto.PostDTO;
import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.communication.repository.SavedPostRepository;
import de.tum.cit.aet.artemis.core.dto.UserRoleDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

public abstract class PostingService {

    private static final Logger log = LoggerFactory.getLogger(PostingService.class);

    protected final CourseRepository courseRepository;

    protected final UserRepository userRepository;

    protected final ExerciseRepository exerciseRepository;

    protected final SavedPostRepository savedPostRepository;

    protected final ConversationParticipantRepository conversationParticipantRepository;

    protected final AuthorizationCheckService authorizationCheckService;

    private final WebsocketMessagingService websocketMessagingService;

    protected static final String METIS_POST_ENTITY_NAME = "metis.post";

    private static final String METIS_WEBSOCKET_CHANNEL_PREFIX = "/topic/communication/";

    // Legacy STOMP destination kept in parallel during the migration to /topic/communication/...
    // Deployed mobile and external clients may still be subscribed here.
    // TODO: Remove once external clients have migrated. Target sunset: 2026-09-30 — keep in sync with
    // LegacyApiPathDeprecationInterceptor.SUNSET_DATE.
    @Deprecated(forRemoval = true, since = "9.3")
    private static final String LEGACY_METIS_WEBSOCKET_CHANNEL_PREFIX = "/topic/metis/";

    protected PostingService(CourseRepository courseRepository, UserRepository userRepository, ExerciseRepository exerciseRepository,
            AuthorizationCheckService authorizationCheckService, WebsocketMessagingService websocketMessagingService,
            ConversationParticipantRepository conversationParticipantRepository, SavedPostRepository savedPostRepository) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.websocketMessagingService = websocketMessagingService;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.savedPostRepository = savedPostRepository;
    }

    /**
     * Helper method to prepare the post included in the websocket message and initiate the broadcasting
     *
     * @param post post that should be broadcast
     */
    public void preparePostForBroadcast(Post post) {
        try {
            var user = userRepository.getUser();
            var savedPostIds = savedPostRepository.findSavedPostIdsByUserIdAndPostType(user.getId(), PostingType.POST);
            post.setIsSaved(savedPostIds.contains(post.getId()));
            var savedAnswerIds = savedPostRepository.findSavedPostIdsByUserIdAndPostType(user.getId(), PostingType.ANSWER);
            post.getAnswers().forEach(answer -> answer.setIsSaved(savedAnswerIds.contains(answer.getId())));
        }
        catch (Exception e) {
            post.setIsSaved(false);
            post.getAnswers().forEach(answer -> answer.setIsSaved(false));
        }
    }

    /**
     * Helper method to prepare the post included in the websocket message and initiate the broadcasting
     *
     * @param updatedAnswerPost answer post that was updated
     * @param course            course the answer post belongs to
     */
    public void preparePostAndBroadcast(AnswerPost updatedAnswerPost, Course course) {
        // we need to explicitly (and newly) add the updated answer post to the answers of the broadcast post to share up-to-date information
        Post updatedPost = updatedAnswerPost.getPost();
        // remove and add operations on sets identify an AnswerPost by its id; to update a certain property of an existing answer post,
        // we need to remove the existing AnswerPost (based on unchanged id in updatedAnswerPost) and add the updatedAnswerPost afterwards
        updatedPost.removeAnswerPost(updatedAnswerPost);
        updatedPost.addAnswerPost(updatedAnswerPost);
        preparePostForBroadcast(updatedPost);
        broadcastForPost(new PostDTO(updatedPost, MetisCrudAction.UPDATE), course.getId(), null);
    }

    /**
     * Broadcasts a posting related event in a course under a specific topic via websockets.
     * <p>
     * The wire payload is a {@link PostBroadcastDTO}: a cycle-free projection that drops the
     * {@code reactions → Reaction.user → User} and {@code answers → AnswerPost.post} cycles
     * before the frame leaves the server. Sending the {@link Post} entity directly used to walk
     * the same JSON cycle that fires Jackson's {@code DeserializerCache} race during integration
     * test deserialization (see {@code JacksonDeserializerInitializationConfig}).
     *
     * @param postDTO    object including the affected post as well as the action
     * @param courseId   the id of the course the posting belongs to
     * @param recipients the recipients for this broadcast, can be null
     */
    @SuppressWarnings("deprecation")
    public void broadcastForPost(PostDTO postDTO, Long courseId, Set<ConversationNotificationRecipientSummary> recipients) {
        Post post = postDTO.post();
        // Build the cycle-free wire payload before adjusting entity state — PostResponseDTO.from
        // walks the entity exactly once.
        PostBroadcastDTO broadcastPayload = PostBroadcastDTO.from(post, postDTO.action());

        Conversation postConversation = post.getConversation();
        if (postConversation != null) {
            String coursePathSuffix = "courses/" + courseId;
            if (postConversation instanceof Channel channel && channel.getIsCourseWide()) {
                websocketMessagingService.sendMessage(METIS_WEBSOCKET_CHANNEL_PREFIX + coursePathSuffix, broadcastPayload);
                // Mirror to the legacy destination so older subscribers still receive updates during the migration window.
                websocketMessagingService.sendMessage(LEGACY_METIS_WEBSOCKET_CHANNEL_PREFIX + coursePathSuffix, broadcastPayload);
            }
            else {
                if (recipients == null) {
                    // send to all participants of the conversation
                    recipients = getConversationParticipantsAsSummaries(postConversation);
                }
                recipients.forEach(recipient -> websocketMessagingService.sendMessage("/topic/user/" + recipient.userId() + "/notifications/conversations", broadcastPayload));
            }
        }
        else if (post.getPlagiarismCase() != null) {
            String plagiarismCaseSuffix = "plagiarismCase/" + post.getPlagiarismCase().getId();
            websocketMessagingService.sendMessage(METIS_WEBSOCKET_CHANNEL_PREFIX + plagiarismCaseSuffix, broadcastPayload);
            websocketMessagingService.sendMessage(LEGACY_METIS_WEBSOCKET_CHANNEL_PREFIX + plagiarismCaseSuffix, broadcastPayload);
        }
    }

    /**
     * Gets the participants of a conversation and maps them to ConversationNotificationRecipientSummary records
     *
     * @param postConversation the conversation
     * @return Set of ConversationNotificationRecipientSummary
     */
    private Set<ConversationNotificationRecipientSummary> getConversationParticipantsAsSummaries(Conversation postConversation) {
        return conversationParticipantRepository.findConversationParticipantsByConversationId(postConversation.getId()).stream()
                .map(participant -> new ConversationNotificationRecipientSummary(participant.getUser(), participant.getIsMuted(),
                        participant.getIsHidden() != null && participant.getIsHidden(), false))
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
            return userRepository.findAllNotificationRecipientsInCourseForConversation(conversation.getId(), course.getStudentGroupName(), course.getTeachingAssistantGroupName(),
                    course.getEditorGroupName(), course.getInstructorGroupName()).stream();
        }

        return conversationParticipantRepository.findConversationParticipantsWithUserGroupsByConversationId(conversation.getId()).stream()
                .map(participant -> new ConversationNotificationRecipientSummary(participant.getUser(), participant.getIsMuted(),
                        participant.getIsHidden() != null && participant.getIsHidden(),
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

    /**
     * Ensures that user is allowed to communicate or message in the given course
     *
     * @param user   that wants to communicate or message
     * @param course the course in which the user wants to communicate or message
     */
    public void preCheckUserAndCourseForCommunicationOrMessaging(User user, Course course) {
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        if (course.getCourseInformationSharingConfiguration() == CourseInformationSharingConfiguration.DISABLED) {
            throw new BadRequestAlertException("Communication and messaging is disabled for this course", getEntityName(), "400", true);
        }
    }

    /**
     * Sets the author role of each post and its answers in a given list of posts.
     *
     * <p>
     * This method processes a list of posts to determine the roles of their authors within the context of a specified course.
     * It collects a unique set of user IDs from the posts and their answers, fetches the corresponding user roles from the repository,
     * and sets the author roles accordingly.
     * </p>
     *
     * @param posts    the list of posts for which the author roles need to be set
     * @param courseId the ID of the course in which the posts exist
     */
    protected void setAuthorRoleOfPostings(Collection<Post> posts, Long courseId) {
        // prepares a unique set of userIds that authored the current list of postings
        Set<Long> userIds = new HashSet<>();
        posts.forEach(post -> {
            // needs to handle posts created by SingleUserNotificationService.notifyUserAboutNewPlagiarismCaseBySystem
            if (post.getAuthor() != null) {
                userIds.add(post.getAuthor().getId());
            }
            post.getAnswers().forEach(answerPost -> userIds.add(answerPost.getAuthor().getId()));
        });

        // we only fetch the minimal data needed for the mapping to avoid performance issues
        Set<UserRoleDTO> userRoles = userRepository.findUserRolesInCourse(userIds, courseId);
        log.debug("userRepository.findUserRolesInCourse done for {} authors ", userRoles.size());

        Map<Long, UserRoleDTO> authorRoles = userRoles.stream().collect(Collectors.toMap(UserRoleDTO::userId, Function.identity()));

        // sets respective author role to display user authority icon on posting headers
        posts.stream().filter(post -> post.getAuthor() != null).forEach(post -> {
            post.setAuthorRole(authorRoles.get(post.getAuthor().getId()).role());
            post.getAnswers().forEach(answerPost -> answerPost.setAuthorRole(authorRoles.get(answerPost.getAuthor().getId()).role()));
        });
    }

    /**
     * Assigns the author role to a given posting based on the user's groups and authorities within a course.
     *
     * <p>
     * This helper method determines the appropriate author role (INSTRUCTOR, TUTOR, or USER) for the author of a posting
     * based on their permissions and groups within the specified course. The author must be fetched with their authorities
     * and groups set prior to calling this method.
     * </p>
     *
     * <p>
     * Note: The course to which the posting belongs must be explicitly fetched and provided to handle cases of new post creation.
     * </p>
     *
     * @param posting       the posting to assign an author role to
     * @param postingCourse the course that the posting belongs to, required to determine the author's role
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
    protected Set<User> parseUserMentions(@NonNull Course course, String postingContent) {
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

        Set<User> mentionedUsers = userRepository.findAllWithGroupsAndAuthoritiesByDeletedIsFalseAndLoginIn(matches.keySet());

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
}
