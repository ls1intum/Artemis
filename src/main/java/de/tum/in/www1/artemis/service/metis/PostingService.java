package de.tum.in.www1.artemis.service.metis;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.metis.*;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
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
     */
    protected void preparePostAndBroadcast(AnswerPost updatedAnswerPost, Course course) {
        // we need to explicitly (and newly) add the updated answer post to the answers of the broadcast post to share up-to-date information
        Post updatedPost = updatedAnswerPost.getPost();
        // remove and add operations on sets identify an AnswerPost by its id; to update a certain property of an existing answer post,
        // we need to remove the existing AnswerPost (based on unchanged id in updatedAnswerPost) and add the updatedAnswerPost afterwards
        updatedPost.removeAnswerPost(updatedAnswerPost);
        updatedPost.addAnswerPost(updatedAnswerPost);
        broadcastForPost(new PostDTO(updatedPost, MetisCrudAction.UPDATE), course, null);
    }

    /**
     * Broadcasts a posting related event in a course under a specific topic via websockets
     *
     * @param postDTO    object including the affected post as well as the action
     * @param course     course the posting belongs to
     * @param recipients the recipients for this broadcast, can be null
     */
    protected void broadcastForPost(PostDTO postDTO, Course course, Set<User> recipients) {

        // reduce the payload of the websocket message: this is important to avoid overloading the involved subsystems
        Conversation postConversation = postDTO.post().getConversation();
        if (postConversation != null) {
            postConversation.hideDetails();
        }

        String specificTopicName = METIS_WEBSOCKET_CHANNEL_PREFIX;
        String genericTopicName = METIS_WEBSOCKET_CHANNEL_PREFIX + "courses/" + course.getId();

        if (postDTO.post().getExercise() != null) {
            specificTopicName += "exercises/" + postDTO.post().getExercise().getId();
            websocketMessagingService.sendMessage(specificTopicName, postDTO);
        }
        else if (postDTO.post().getLecture() != null) {
            specificTopicName += "lectures/" + postDTO.post().getLecture().getId();
            websocketMessagingService.sendMessage(specificTopicName, postDTO);
        }
        else if (postConversation != null) {
            String conversationTopicName = genericTopicName + "/conversations/" + postConversation.getId();

            if (postConversation instanceof Channel channel && channel.getIsCourseWide()) {
                websocketMessagingService.sendMessage(conversationTopicName, postDTO);
            }
            else {
                if (recipients == null) {
                    // send to all participants of the conversation
                    recipients = conversationParticipantRepository.findConversationParticipantByConversationId(postConversation.getId()).stream()
                            .map(ConversationParticipant::getUser).collect(Collectors.toSet());
                }
                recipients.forEach(user -> websocketMessagingService.sendMessageToUser(user.getLogin(), conversationTopicName, postDTO));
            }

            return;
        }

        websocketMessagingService.sendMessage(genericTopicName, postDTO);
    }

    /**
     * Determines the participants of a conversation that should receive the new message.
     *
     * @param conversation conversation the participants are supposed be retrieved
     * @return users that should receive the new message
     */
    protected Stream<ConversationWebSocketRecipientSummary> getWebSocketRecipients(Conversation conversation) {
        if (conversation instanceof Channel channel && channel.getIsCourseWide()) {
            return userRepository.findAllWebSocketRecipientsInCourseForConversation(conversation.getCourse().getId(), conversation.getId()).stream();
        }

        return conversationParticipantRepository.findConversationParticipantWithUserGroupsByConversationId(conversation.getId()).stream()
                .map(participant -> new ConversationWebSocketRecipientSummary(participant.getUser(), participant.getIsHidden() != null && participant.getIsHidden(),
                        authorizationCheckService.isAtLeastTeachingAssistantInCourse(conversation.getCourse(), participant.getUser())));
    }

    /**
     * Checks if the requesting user is authorized in the course context,
     * i.e. a user has to be the author of posting or at least teaching assistant
     *
     * @param posting posting that is requested
     * @param user    requesting user
     * @param course  course the posting belongs to
     */
    protected void mayUpdateOrDeletePostingElseThrow(Posting posting, User user, Course course) {
        if (!user.getId().equals(posting.getAuthor().getId())) {
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, user);
        }
    }

    /**
     * Method to check if the possibly associated exercise is not an exam exercise
     *
     * @param post post that is checked
     */
    protected void preCheckPostValidity(Post post) {
        // do not allow postings for exam exercises
        if (post.getExercise() != null) {
            Long exerciseId = post.getExercise().getId();
            Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
            if (exercise.isExamExercise()) {
                throw new BadRequestAlertException("Postings are not allowed for exam exercises", getEntityName(), "400", true);
            }
        }
    }

    protected Course preCheckUserAndCourseForCommunication(User user, Long courseId) {
        final Course course = courseRepository.findByIdElseThrow(courseId);
        // user has to be at least student in the course
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        // check if the course has posts enabled
        if (!courseRepository.isCommunicationEnabled(courseId)) {
            throw new BadRequestAlertException("Communication feature is not enabled for this course", getEntityName(), "400", true);
        }
        return course;
    }

    protected Course preCheckUserAndCourseForMessaging(User user, Long courseId) {
        final Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        if (!courseRepository.isMessagingEnabled(course.getId())) {
            throw new BadRequestAlertException("Messaging is not enabled for this course", getEntityName(), "400", true);
        }
        return course;
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
            userIds.add(post.getAuthor().getId());
            post.getAnswers().forEach(answerPost -> userIds.add(answerPost.getAuthor().getId()));
        });

        // fetches and sets groups and authorities of all posting authors involved, which are used to display author role icon in the posting header
        // converts fetched set to hashmap type for performant matching of authors
        Map<Long, User> authors = userRepository.findAllWithGroupsAndAuthoritiesByIdIn(userIds).stream().collect(Collectors.toMap(DomainObject::getId, Function.identity()));

        // sets respective author role to display user authority icon on posting headers
        postsInCourse.forEach(post -> {
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
}
