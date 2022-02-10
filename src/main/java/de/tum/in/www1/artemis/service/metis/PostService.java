package de.tum.in.www1.artemis.service.metis;

import java.util.*;
import java.util.stream.Collectors;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.metis.CourseWideContext;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.Reaction;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.metis.similarity.PostSimilarityComparisonStrategy;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.websocket.dto.MetisPostAction;
import de.tum.in.www1.artemis.web.websocket.dto.MetisPostDTO;

@Service
public class PostService extends PostingService {

    private static final String METIS_POST_ENTITY_NAME = "metis.post";

    public static final int TOP_K_SIMILARITY_RESULTS = 5;

    private final UserRepository userRepository;

    private final PostRepository postRepository;

    private final GroupNotificationService groupNotificationService;

    private final PostSimilarityComparisonStrategy postContentCompareStrategy;

    protected PostService(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, UserRepository userRepository, PostRepository postRepository,
            ExerciseRepository exerciseRepository, LectureRepository lectureRepository, GroupNotificationService groupNotificationService,
            PostSimilarityComparisonStrategy postContentCompareStrategy, SimpMessageSendingOperations messagingTemplate) {
        super(courseRepository, exerciseRepository, lectureRepository, postRepository, authorizationCheckService, messagingTemplate);
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.groupNotificationService = groupNotificationService;
        this.postContentCompareStrategy = postContentCompareStrategy;
    }

    /**
     * Checks course, user and post validity,
     * determines the post's author, persists the post,
     * and sends a notification to affected user groups
     *
     * @param courseId id of the course the post belongs to
     * @param post     post to create
     * @return created post that was persisted
     */
    public Post createPost(Long courseId, Post post) {
        final User user = this.userRepository.getUserWithGroupsAndAuthorities();

        // checks
        if (post.getId() != null) {
            throw new BadRequestAlertException("A new post cannot already have an ID", METIS_POST_ENTITY_NAME, "idexists");
        }
        final Course course = preCheckUserAndCourse(user, courseId);
        mayInteractWithPostElseThrow(post, user, course);
        preCheckPostValidity(post);

        // set author to current user
        post.setAuthor(user);
        // set default value display priority -> NONE
        post.setDisplayPriority(DisplayPriority.NONE);

        if (post.getCourseWideContext() == CourseWideContext.ANNOUNCEMENT) {
            // display priority of announcement is set to pinned per default
            post.setDisplayPriority(DisplayPriority.PINNED);
            Post savedPost = postRepository.save(post);
            sendNotification(savedPost, course);
            broadcastForPost(new MetisPostDTO(savedPost, MetisPostAction.CREATE_POST), course);
            return savedPost;
        }
        Post savedPost = postRepository.save(post);

        broadcastForPost(new MetisPostDTO(savedPost, MetisPostAction.CREATE_POST), course);
        sendNotification(savedPost, course);

        return savedPost;
    }

    /**
     * Checks course, user and post validity,
     * updates non-restricted field of the post, persists the post,
     * and ensures that sensitive information is filtered out
     *
     * @param courseId id of the course the post belongs to
     * @param postId   id of the post to update
     * @param post     post to update
     * @return updated post that was persisted
     */
    public Post updatePost(Long courseId, Long postId, Post post) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        // check
        if (post.getId() == null || !Objects.equals(post.getId(), postId)) {
            throw new BadRequestAlertException("Invalid id", METIS_POST_ENTITY_NAME, "idnull");
        }
        final Course course = preCheckUserAndCourse(user, courseId);
        mayInteractWithPostElseThrow(post, user, course);

        Post existingPost = postRepository.findByIdElseThrow(postId);
        preCheckPostValidity(existingPost);
        mayUpdateOrDeletePostingElseThrow(existingPost, user, course);

        boolean contextHasChanged = !existingPost.hasSameContext(post);
        // depending on if there is a context change we need to broadcast different information
        if (contextHasChanged) {
            // in case the context changed, a post is moved from one context (page) to another
            // i.e., it has to be treated as deleted post in the old context
            broadcastForPost(new MetisPostDTO(existingPost, MetisPostAction.DELETE_POST), course);
        }

        // update: allow overwriting of values only for depicted fields if user is at least student
        existingPost.setTitle(post.getTitle());
        existingPost.setContent(post.getContent());
        existingPost.setVisibleForStudents(post.isVisibleForStudents());
        existingPost.setTags(post.getTags());

        // update: allow overwriting of certain values if they are at least TAs in this course
        if (authorizationCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            existingPost.setDisplayPriority(post.getDisplayPriority());
            // allow changing the post context (moving it to another context)
            existingPost.setLecture(post.getLecture());
            existingPost.setExercise(post.getExercise());
            existingPost.setCourseWideContext(post.getCourseWideContext());
            existingPost.setCourse(post.getCourse());
        }

        Post updatedPost = postRepository.save(existingPost);

        if (updatedPost.getExercise() != null) {
            // protect sample solution, grading instructions, etc.
            updatedPost.getExercise().filterSensitiveInformation();
        }

        // depending on if there is a context change we need to broadcast different information
        if (contextHasChanged) {
            // in case the context changed, a post is moved from one context (page) to another
            // i.e., it has to be treated as newly created post in the new context
            broadcastForPost(new MetisPostDTO(updatedPost, MetisPostAction.CREATE_POST), course);
        }
        else {
            // in case the context did not change we emit with trigger a post update via websocket
            broadcastForPost(new MetisPostDTO(updatedPost, MetisPostAction.UPDATE_POST), course);
        }
        return updatedPost;
    }

    /**
     * Invokes the updatePost method to persist the change of displayPriority
     *
     * @param courseId          id of the course the post belongs to
     * @param postId            id of the post to change the pin state for
     * @param displayPriority   new displayPriority
     * @return updated post that was persisted
     */
    public Post changeDisplayPriority(Long courseId, Long postId, DisplayPriority displayPriority) {
        Post post = postRepository.findByIdElseThrow(postId);
        post.setDisplayPriority(displayPriority);

        return updatePost(courseId, postId, post);
    }

    /**
     * Add reaction to a post and persist the post
     *
     * @param post     post that is reacted on
     * @param reaction reaction that was added by a user
     * @param courseId id of course the post belongs to
     */
    public void updateWithReaction(Post post, Reaction reaction, Long courseId) {
        final Course course = preCheckUserAndCourse(reaction.getUser(), courseId);
        post.addReaction(reaction);
        Post updatedPost = postRepository.save(post);
        broadcastForPost(new MetisPostDTO(updatedPost, MetisPostAction.UPDATE_POST), course);
    }

    /**
     * @param courseId          id of the course the fetch posts for
     * @param courseWideContext course-wide context for which the posts should be fetched
     * @param exerciseId        id of the exercise for which the posts should be fetched
     * @param lectureId         id of the lecture for which the posts should be fetched
     * @return list of posts that match the given context
     */
    public List<Post> getPostsInCourse(Long courseId, CourseWideContext courseWideContext, Long exerciseId, Long lectureId) {
        // no filter -> get all posts in course
        if (courseWideContext == null && exerciseId == null && lectureId == null) {
            return this.getAllCoursePosts(courseId);
        }
        // filter by course-wide context
        else if (courseWideContext != null && exerciseId == null && lectureId == null) {
            return this.getAllPostsByCourseWideContext(courseId, courseWideContext);
        }
        // filter by exercise
        else if (courseWideContext == null && exerciseId != null && lectureId == null) {
            return this.getAllExercisePosts(courseId, exerciseId);
        }
        // filter by lecture
        else if (courseWideContext == null && exerciseId == null && lectureId != null) {
            return this.getAllLecturePosts(courseId, lectureId);
        }
        else {
            throw new BadRequestAlertException("A new post cannot be associated with more than one context", METIS_POST_ENTITY_NAME, "ambiguousContext");
        }
    }

    /**
     * Checks course, user and post validity,
     * retrieves all posts for a course by its id
     * and ensures that sensitive information is filtered out
     *
     * @param courseId id of the course the post belongs to
     * @return list of posts that belong to the course
     */
    public List<Post> getAllCoursePosts(Long courseId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        final Course course = preCheckUserAndCourse(user, courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);

        // retrieve posts
        List<Post> coursePosts = postRepository.findPostsForCourse(courseId);
        // protect sample solution, grading instructions, etc.
        coursePosts.stream().map(Post::getExercise).filter(Objects::nonNull).forEach(Exercise::filterSensitiveInformation);

        return coursePosts;
    }

    /**
     * Checks course, user and post validity,
     * retrieves all posts with a certain course-wide context by course id
     * and ensures that sensitive information is filtered out
     *
     * @param courseId          id of the course the post belongs to
     * @param courseWideContext specific course-wide context to filter course get posts for
     * @return list of posts for a certain course-wide context
     */
    public List<Post> getAllPostsByCourseWideContext(Long courseId, CourseWideContext courseWideContext) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        final Course course = preCheckUserAndCourse(user, courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);

        // retrieve posts
        List<Post> coursePosts = postRepository.findPostsForCourseWideContext(courseId, courseWideContext);
        // protect sample solution, grading instructions, etc.
        coursePosts.stream().map(Post::getExercise).filter(Objects::nonNull).forEach(Exercise::filterSensitiveInformation);

        return coursePosts;
    }

    /**
     * Checks course, user, exercise and post validity,
     * retrieves all posts for an exercise by its id
     * and ensures that sensitive information is filtered out
     *
     * @param courseId   id of the course the post belongs to
     * @param exerciseId id of the exercise for which the posts should be retrieved
     * @return list of posts that belong to the exercise
     */
    public List<Post> getAllExercisePosts(Long courseId, Long exerciseId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        preCheckUserAndCourse(user, courseId);
        preCheckExercise(user, courseId, exerciseId);

        // retrieve posts
        List<Post> exercisePosts = postRepository.findPostsByExerciseId(exerciseId);
        // protect sample solution, grading instructions, etc.
        exercisePosts.forEach(post -> post.getExercise().filterSensitiveInformation());

        return exercisePosts;
    }

    /**
     * Checks course, user, lecture and post validity,
     * retrieves all posts for a lecture by its id
     * and ensures that sensitive information is filtered out
     *
     * @param courseId  id of the course the post belongs to
     * @param lectureId id of the lecture for which the posts should be retrieved
     * @return list of posts that belong to the lecture
     */
    public List<Post> getAllLecturePosts(Long courseId, Long lectureId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        preCheckUserAndCourse(user, courseId);
        preCheckLecture(user, courseId, lectureId);

        // retrieve posts
        List<Post> lecturePosts = postRepository.findPostsByLectureId(lectureId);
        // protect sample solution, grading instructions, etc.
        lecturePosts.stream().map(Post::getExercise).filter(Objects::nonNull).forEach(Exercise::filterSensitiveInformation);

        return lecturePosts;
    }

    /**
     * Checks course, user and post validity,
     * determines authority to delete post and deletes the post
     *
     * @param courseId id of the course the post belongs to
     * @param postId   id of the post to delete
     */
    public void deletePostById(Long courseId, Long postId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        final Course course = preCheckUserAndCourse(user, courseId);
        Post post = postRepository.findByIdElseThrow(postId);
        mayInteractWithPostElseThrow(post, user, course);
        preCheckPostValidity(post);
        mayUpdateOrDeletePostingElseThrow(post, user, course);

        // delete
        postRepository.deleteById(postId);
        broadcastForPost(new MetisPostDTO(post, MetisPostAction.DELETE_POST), course);
    }

    /**
     * Checks course and user validity,
     * retrieves all tags for posts in a certain course
     *
     * @param courseId id of the course the tags belongs to
     * @return tags of all posts that belong to the course
     */
    public List<String> getAllCourseTags(Long courseId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        preCheckUserAndCourse(user, courseId);
        return postRepository.findPostTagsForCourse(courseId);
    }

    /**
     * Method to (i) check if the exercise exists, (ii) check if requesting user is authorized in the exercise context,
     * and (iii) compare the id of the course belonging to the exercise with the path variable courseId,
     *
     * @param user       requesting user
     * @param courseId   id of the course that is used as path variable
     * @param exerciseId id of the exercise that is used as path variable
     */
    private void preCheckExercise(User user, Long courseId, Long exerciseId) {
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
        if (!exercise.getCourseViaExerciseGroupOrCourseMember().getId().equals(courseId)) {
            throw new BadRequestAlertException("PathVariable courseId doesn't match the courseId of the Exercise", METIS_POST_ENTITY_NAME, "idnull");
        }
    }

    /**
     * Method to (i) check if the lecture exists, (ii) check if requesting user is authorized in the lecture context,
     * and (iii) compare the id of the course belonging to the lecture with the path variable courseId,
     *
     * @param user      requesting user
     * @param courseId  id of the course that is used as path variable
     * @param lectureId id of the lecture that is used as path variable
     */
    private void preCheckLecture(User user, Long courseId, Long lectureId) {
        Lecture lecture = lectureRepository.findByIdElseThrow(lectureId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, lecture.getCourse(), user);
        if (!lecture.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("PathVariable courseId doesn't match the courseId of the Lecture", METIS_POST_ENTITY_NAME, "idnull");
        }
    }

    /**
     * Sends notification to affected groups
     *
     * @param post post that triggered the notification
     */
    void sendNotification(Post post, Course course) {
        // create post for notification
        Post postForNotification = new Post();
        postForNotification.setId(post.getId());
        postForNotification.setAuthor(post.getAuthor());
        postForNotification.setCourse(course);
        postForNotification.setCourseWideContext(post.getCourseWideContext());
        postForNotification.setLecture(post.getLecture());
        postForNotification.setExercise(post.getExercise());
        postForNotification.setCreationDate(post.getCreationDate());
        postForNotification.setTitle(post.getTitle());

        // create html content
        Parser parser = Parser.builder().build();
        String htmlPostContent;
        try {
            Node document = parser.parse(post.getContent());
            HtmlRenderer renderer = HtmlRenderer.builder().build();
            htmlPostContent = renderer.render(document);
        }
        catch (Exception e) {
            htmlPostContent = "";
        }
        postForNotification.setContent(htmlPostContent);

        // notify via course
        if (post.getCourseWideContext() != null) {
            if (post.getCourseWideContext() == CourseWideContext.ANNOUNCEMENT) {
                groupNotificationService.notifyAllGroupsAboutNewAnnouncement(postForNotification, course);
                return;
            }
            groupNotificationService.notifyAllGroupsAboutNewCoursePost(postForNotification, course);
            return;
        }
        // notify via exercise
        if (post.getExercise() != null) {
            groupNotificationService.notifyAllGroupsAboutNewPostForExercise(postForNotification, course);
            // protect sample solution, grading instructions, etc.
            post.getExercise().filterSensitiveInformation();
            return;
        }
        // notify via lecture
        if (post.getLecture() != null) {
            groupNotificationService.notifyAllGroupsAboutNewPostForLecture(postForNotification, course);
        }
    }

    /**
     * Retrieve the entity name used in ResponseEntity
     */
    @Override
    public String getEntityName() {
        return METIS_POST_ENTITY_NAME;
    }

    /**
     * Retrieve post from database by id
     *
     * @param postId id of requested post
     * @return retrieved post
     */
    public Post findById(Long postId) {
        return postRepository.findByIdElseThrow(postId);
    }

    /**
     * Calculates k similar posts based on the underlying content comparison strategy
     *
     * @param courseId  id of the course in which similar posts are searched for
     * @param post      post that is to be created and check for similar posts beforehand
     * @return list of similar posts
     */
    public List<Post> getSimilarPosts(Long courseId, Post post) {
        List<Post> coursePosts = this.getAllCoursePosts(courseId);

        // sort course posts by calculated similarity scores
        coursePosts.sort(Comparator.comparing((coursePost) -> postContentCompareStrategy.performSimilarityCheck(post, coursePost)));
        return Lists.reverse(coursePosts).stream().limit(TOP_K_SIMILARITY_RESULTS).collect(Collectors.toList());
    }

    /**
     * Checks if the requesting user is authorized in the course context,
     * i.e., if the user is allowed to interact with a certain post
     *
     * @param post      post to interact with, i.e., create, update or delete
     * @param user      requesting user
     * @param course    course the posting belongs to
     */
    private void mayInteractWithPostElseThrow(Post post, User user, Course course) {
        if (post.getCourseWideContext() == CourseWideContext.ANNOUNCEMENT) {
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);
        }
    }
}
