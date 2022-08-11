package de.tum.in.www1.artemis.service.metis;

import static de.tum.in.www1.artemis.service.metis.PostService.postComparator;
import static de.tum.in.www1.artemis.service.metis.PostService.postFilter;

import java.util.List;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.Posting;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.dto.PostContextFilter;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.websocket.dto.metis.MetisCrudAction;
import de.tum.in.www1.artemis.web.websocket.dto.metis.PostDTO;

public abstract class PostingService {

    final CourseRepository courseRepository;

    final ExerciseRepository exerciseRepository;

    final LectureRepository lectureRepository;

    final PostRepository postRepository;

    final AuthorizationCheckService authorizationCheckService;

    private final SimpMessageSendingOperations messagingTemplate;

    protected static final String METIS_POST_ENTITY_NAME = "metis.post";

    private static final String METIS_WEBSOCKET_CHANNEL_PREFIX = "/topic/metis/";

    protected PostingService(CourseRepository courseRepository, ExerciseRepository exerciseRepository, LectureRepository lectureRepository, PostRepository postRepository,
            AuthorizationCheckService authorizationCheckService, SimpMessageSendingOperations messagingTemplate) {
        this.courseRepository = courseRepository;
        this.exerciseRepository = exerciseRepository;
        this.lectureRepository = lectureRepository;
        this.postRepository = postRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.messagingTemplate = messagingTemplate;
    }

    @NotNull
    protected Page<Post> orderAndPaginatePosts(boolean pagingEnabled, Pageable pageable, PostContextFilter postContextFilter, List<Post> posts) {
        List<Post> processedPosts = posts;

        // search by text or #post
        if (postContextFilter.getSearchText() != null) {
            processedPosts = processedPosts.stream().filter(post -> postFilter(post, postContextFilter.getSearchText())).collect(Collectors.toList());
        }

        final Page<Post> postsPage;
        if (pagingEnabled) {
            int startIndex = pageable.getPageNumber() * pageable.getPageSize();
            int endIndex = Math.min(startIndex + pageable.getPageSize(), processedPosts.size());

            // sort (only used by CourseDiscussions and CourseMessages Page, which has pagination enabled)
            processedPosts.sort((postA, postB) -> postComparator(postA, postB, postContextFilter.getPostSortCriterion(), postContextFilter.getSortingOrder()));

            try {
                postsPage = new PageImpl<>(processedPosts.subList(startIndex, endIndex), pageable, processedPosts.size());
            }
            catch (IllegalArgumentException ex) {
                throw new BadRequestAlertException("Not enough posts to fetch " + pageable.getPageNumber() + "'th page", METIS_POST_ENTITY_NAME, "invalidPageRequest");
            }
        }
        else {
            postsPage = new PageImpl<>(processedPosts);
        }
        return postsPage;
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

    abstract String getEntityName();
}
