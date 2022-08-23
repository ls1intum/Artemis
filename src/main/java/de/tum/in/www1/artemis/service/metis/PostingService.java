package de.tum.in.www1.artemis.service.metis;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.SimpMessageSendingOperations;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.Posting;
import de.tum.in.www1.artemis.domain.metis.UserRole;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.websocket.dto.metis.MetisCrudAction;
import de.tum.in.www1.artemis.web.websocket.dto.metis.PostDTO;

public abstract class PostingService {

    final CourseRepository courseRepository;

    final UserRepository userRepository;

    final ExerciseRepository exerciseRepository;

    final LectureRepository lectureRepository;

    final AuthorizationCheckService authorizationCheckService;

    private final SimpMessageSendingOperations messagingTemplate;

    protected static final String METIS_POST_ENTITY_NAME = "metis.post";

    private static final String METIS_WEBSOCKET_CHANNEL_PREFIX = "/topic/metis/";

    protected PostingService(CourseRepository courseRepository, UserRepository userRepository, ExerciseRepository exerciseRepository, LectureRepository lectureRepository,
            AuthorizationCheckService authorizationCheckService, SimpMessageSendingOperations messagingTemplate) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.lectureRepository = lectureRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.messagingTemplate = messagingTemplate;
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
        broadcastForPost(new PostDTO(updatedPost, MetisCrudAction.UPDATE), course);
    }

    /**
     * Broadcasts a posting related event in a course under a specific topic via websockets
     *
     * @param postDTO object including the affected post as well as the action
     * @param course  course the posting belongs to
     */
    void broadcastForPost(PostDTO postDTO, Course course) {
        String specificTopicName = METIS_WEBSOCKET_CHANNEL_PREFIX;
        String genericTopicName = METIS_WEBSOCKET_CHANNEL_PREFIX + "courses/" + course.getId();

        if (postDTO.getPost().getExercise() != null) {
            specificTopicName += "exercises/" + postDTO.getPost().getExercise().getId();
            messagingTemplate.convertAndSend(specificTopicName, postDTO);
        }
        else if (postDTO.getPost().getLecture() != null) {
            specificTopicName += "lectures/" + postDTO.getPost().getLecture().getId();
            messagingTemplate.convertAndSend(specificTopicName, postDTO);
        }
        else if (postDTO.getPost().getConversation() != null) {
            messagingTemplate.convertAndSend(genericTopicName + "/conversations/" + postDTO.getPost().getConversation().getId(), postDTO);
            return;
        }
        messagingTemplate.convertAndSend(genericTopicName, postDTO);
    }

    /**
     * Checks if the requesting user is authorized in the course context,
     * i.e. a user has to be the author of posting or at least teaching assistant
     *
     * @param posting posting that is requested
     * @param user    requesting user
     * @param course  course the posting belongs to
     */
    void mayUpdateOrDeletePostingElseThrow(Posting posting, User user, Course course) {
        if (!user.getId().equals(posting.getAuthor().getId())) {
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, user);
        }
    }

    /**
     * Method to check if the possibly associated exercise is not an exam exercise
     *
     * @param post post that is checked
     */
    void preCheckPostValidity(Post post) {
        // do not allow postings for exam exercises
        if (post.getExercise() != null) {
            Long exerciseId = post.getExercise().getId();
            Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
            if (exercise.isExamExercise()) {
                throw new BadRequestAlertException("Postings are not allowed for exam exercises", getEntityName(), "400", true);
            }
        }
    }

    Course preCheckUserAndCourse(User user, Long courseId) {
        final Course course = courseRepository.findByIdElseThrow(courseId);
        // user has to be at least student in the course
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        // check if the course has posts enabled
        if (!course.getPostsEnabled()) {
            throw new BadRequestAlertException("Postings are not enabled for this course", getEntityName(), "400", true);
        }
        return course;
    }

    /**
     * helper method that fetches groups and authorities of all posting authors in a list of Posts
     * @param postsInCourse list of posts whose authors are populated with their groups, authorities, and authorRole
     */
    void setAuthorRoleOfPostings(List<Post> postsInCourse) {
        // prepares a unique set of userIds that authored the current list of postings
        Set<Long> userIds = new HashSet<>();
        postsInCourse.forEach(post -> {
            userIds.add(post.getAuthor().getId());
            post.getAnswers().forEach(answerPost -> userIds.add(answerPost.getAuthor().getId()));
        });

        // fetches and sets groups and authorities of all posting authors involved, which are used to display author role icon in the posting header
        // converts fetched set to hashmap type for performant matching of authors
        Map<Long, User> authors = userRepository.findAllWithGroupsAndAuthoritiesByIdIn(userIds).stream().collect(Collectors.toMap(user -> user.getId(), Function.identity()));

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
     * @param posting       posting to assign authorRole
     * @param postingCourse course that the post belongs to, must be explicitly fetched and provided to handle new post creation case
     */
    void setAuthorRoleForPosting(Posting posting, Course postingCourse) {
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

    abstract String getEntityName();
}
