package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.metis.CourseWideContext;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.repository.metis.PostRepository;

public class PostIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private PostRepository postRepository;

    private List<Post> existingPosts;

    private List<Post> existingExercisePosts;

    private List<Post> existingLecturePosts;

    private List<Post> existingCourseWidePosts;

    private Course course;

    private Long courseId;

    private Long exerciseId;

    private Long lectureId;

    private Validator validator;

    @BeforeEach
    public void initTestCase() {

        // used to test hibernate validation using custom PostContextConstraintValidator
        validator = Validation.buildDefaultValidatorFactory().getValidator();

        database.addUsers(5, 5, 0, 1);

        // initialize test setup and get all existing posts (there are 4 posts with lecture context, 4 with exercise context, and 3 with course-wide context - initialized): 11
        // posts in total
        existingPosts = database.createPostsWithinCourse();

        // filter existing posts with exercise context
        existingExercisePosts = existingPosts.stream().filter(coursePost -> (coursePost.getExercise() != null)).collect(Collectors.toList());

        // filter existing posts with lecture context
        existingLecturePosts = existingPosts.stream().filter(coursePost -> (coursePost.getLecture() != null)).collect(Collectors.toList());

        // filter existing posts with course-wide context
        existingCourseWidePosts = existingPosts.stream().filter(coursePost -> (coursePost.getCourseWideContext() != null)).collect(Collectors.toList());

        course = existingExercisePosts.get(0).getExercise().getCourseViaExerciseGroupOrCourseMember();

        courseId = course.getId();

        exerciseId = existingExercisePosts.get(0).getExercise().getId();

        lectureId = existingLecturePosts.get(0).getLecture().getId();
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    // CREATE

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCreateExercisePost() throws Exception {
        Post postToSave = createPostWithoutContext();
        Exercise exercise = existingExercisePosts.get(0).getExercise();
        postToSave.setExercise(exercise);
        postToSave.setCourse(exercise.getCourseViaExerciseGroupOrCourseMember());

        Post createdPost = request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.CREATED);
        checkCreatedPost(postToSave, createdPost);
        assertThat(existingExercisePosts.size() + 1).isEqualTo(postRepository.findPostsByExerciseId(exerciseId).size());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCreateExamExercisePost_badRequest() throws Exception {
        Exam exam = database.setupSimpleExamWithExerciseGroupExercise(course);
        Post postToSave = createPostWithoutContext();
        Exercise examExercise = exam.getExerciseGroups().get(0).getExercises().stream().findFirst().orElseThrow();
        postToSave.setExercise(examExercise);
        examExercise.setCourse(course);

        request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.BAD_REQUEST);
        assertThat(existingExercisePosts.size()).isEqualTo(postRepository.findPostsByExerciseId(exerciseId).size());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCreateLecturePost() throws Exception {
        Post postToSave = createPostWithoutContext();
        Lecture lecture = existingLecturePosts.get(0).getLecture();
        postToSave.setLecture(lecture);
        postToSave.setCourse(lecture.getCourse());

        Post createdPost = request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.CREATED);
        checkCreatedPost(postToSave, createdPost);
        assertThat(existingLecturePosts.size() + 1).isEqualTo(postRepository.findPostsByLectureId(lectureId).size());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCreateCourseWidePost() throws Exception {
        Post postToSave = createPostWithoutContext();
        postToSave.setCourse(course);
        postToSave.setCourseWideContext(existingCourseWidePosts.get(0).getCourseWideContext());

        Post createdPost = request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.CREATED);
        checkCreatedPost(postToSave, createdPost);

        List<Post> updatedCourseWidePosts = postRepository.findPostsForCourse(courseId).stream().filter(post -> post.getCourseWideContext() != null).collect(Collectors.toList());
        assertThat(existingCourseWidePosts.size() + 1).isEqualTo(updatedCourseWidePosts.size());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreateAnnouncement() throws Exception {
        Post postToSave = createPostWithoutContext();
        postToSave.setCourse(course);
        postToSave.setCourseWideContext(CourseWideContext.ANNOUNCEMENT);

        Post createdPost = request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.CREATED);
        postToSave.setDisplayPriority(DisplayPriority.PINNED);
        checkCreatedPost(postToSave, createdPost);

        List<Post> updatedCourseWidePosts = postRepository.findPostsForCourse(courseId).stream().filter(post -> post.getCourseWideContext() != null).collect(Collectors.toList());
        assertThat(existingCourseWidePosts.size() + 1).isEqualTo(updatedCourseWidePosts.size());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testCreateAnnouncement_forbidden() throws Exception {
        Post postToSave = createPostWithoutContext();
        postToSave.setCourse(course);
        postToSave.setCourseWideContext(CourseWideContext.ANNOUNCEMENT);

        request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.FORBIDDEN);
        assertThat(existingPosts.size()).isEqualTo(postRepository.count());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCreateExistingPost_badRequest() throws Exception {
        Post existingPostToSave = existingPosts.get(0);

        request.postWithResponseBody("/api/courses/" + courseId + "/posts", existingPostToSave, Post.class, HttpStatus.BAD_REQUEST);
        assertThat(existingPosts.size()).isEqualTo(postRepository.count());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCreatePostForCourseWithDisabledPosts_badRequest() throws Exception {
        Course course = database.createCourseWithPostsDisabled();
        courseId = course.getId();
        Post postToSave = createPostWithoutContext();
        postToSave.setCourse(course);
        postToSave.setCourseWideContext(CourseWideContext.RANDOM);

        request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testValidatePostContextConstraintViolation() throws Exception {
        Post invalidPost = createPostWithoutContext();
        request.postWithResponseBody("/api/courses/" + courseId + "/posts", invalidPost, Post.class, HttpStatus.BAD_REQUEST);

        invalidPost = createPostWithoutContext();
        invalidPost.setCourseWideContext(CourseWideContext.ORGANIZATION);
        invalidPost.setLecture(existingLecturePosts.get(0).getLecture());
        request.postWithResponseBody("/api/courses/" + courseId + "/posts", invalidPost, Post.class, HttpStatus.BAD_REQUEST);
        Set<ConstraintViolation<Post>> constraintViolations = validator.validate(invalidPost);
        assertThat(constraintViolations.size()).isEqualTo(1);

        invalidPost = createPostWithoutContext();
        invalidPost.setCourseWideContext(CourseWideContext.ORGANIZATION);
        invalidPost.setExercise(existingExercisePosts.get(0).getExercise());
        request.postWithResponseBody("/api/courses/" + courseId + "/posts", invalidPost, Post.class, HttpStatus.BAD_REQUEST);
        constraintViolations = validator.validate(invalidPost);
        assertThat(constraintViolations.size()).isEqualTo(1);

        invalidPost = createPostWithoutContext();
        invalidPost.setLecture(existingLecturePosts.get(0).getLecture());
        invalidPost.setExercise(existingExercisePosts.get(0).getExercise());
        request.postWithResponseBody("/api/courses/" + courseId + "/posts", invalidPost, Post.class, HttpStatus.BAD_REQUEST);
        constraintViolations = validator.validate(invalidPost);
        assertThat(constraintViolations.size()).isEqualTo(1);
    }

    // UPDATE

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testEditPost_asTutor() throws Exception {
        // update post of student1 (index 0)--> OK
        Post postToUpdate = editExistingPost(existingPosts.get(0));

        Post updatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts", postToUpdate, Post.class, HttpStatus.OK);
        assertThat(updatedPost).isEqualTo(postToUpdate);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testEditPost_asStudent() throws Exception {
        // update own post (index 0)--> OK
        Post postToUpdate = editExistingPost(existingPosts.get(0));

        Post updatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts", postToUpdate, Post.class, HttpStatus.OK);
        assertThat(updatedPost).isEqualTo(postToUpdate);

        // update post from another student (index 1)--> forbidden
        Post postToNotUpdate = editExistingPost(existingPosts.get(1));

        Post notUpdatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts", postToNotUpdate, Post.class, HttpStatus.FORBIDDEN);
        assertThat(notUpdatedPost).isNull();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testEditPost_asStudent_forbidden() throws Exception {
        // update post from another student (index 1)--> forbidden
        Post postToNotUpdate = editExistingPost(existingPosts.get(1));

        Post notUpdatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts", postToNotUpdate, Post.class, HttpStatus.FORBIDDEN);
        assertThat(notUpdatedPost).isNull();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testEditPostWithIdIsNull_badRequest() throws Exception {
        Post postToUpdate = existingPosts.get(0);
        postToUpdate.setId(null);

        Post updatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts", postToUpdate, Post.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedPost).isNull();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testPinPost_asStudent_forbidden() throws Exception {
        Post postToNotPin = editExistingPost(existingPosts.get(1));
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("displayPriority", DisplayPriority.PINNED.toString());

        // try to change display priority to PINNED
        Post notUpdatedPost = request.putWithResponseBodyAndParams("/api/courses/" + courseId + "/posts/" + postToNotPin.getId() + "/display-priority", null, Post.class,
                HttpStatus.FORBIDDEN, params);
        assertThat(notUpdatedPost).isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testPinPost_asTutor() throws Exception {
        Post postToPin = editExistingPost(existingPosts.get(0));
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("displayPriority", DisplayPriority.PINNED.toString());

        // change display priority to PINNED
        Post updatedPost = request.putWithResponseBodyAndParams("/api/courses/" + courseId + "/posts/" + postToPin.getId() + "/display-priority", null, Post.class, HttpStatus.OK,
                params);
        assertThat(updatedPost).isEqualTo(postToPin);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testArchivePost_asStudent_forbidden() throws Exception {
        Post postToNotArchive = editExistingPost(existingPosts.get(1));
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("displayPriority", DisplayPriority.ARCHIVED.toString());

        // try to change display priority to ARCHIVED
        Post notUpdatedPost = request.putWithResponseBodyAndParams("/api/courses/" + courseId + "/posts/" + postToNotArchive.getId() + "/display-priority", null, Post.class,
                HttpStatus.FORBIDDEN, params);
        assertThat(notUpdatedPost).isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testArchivePost_asTutor() throws Exception {
        Post postToArchive = editExistingPost(existingPosts.get(0));
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("displayPriority", DisplayPriority.ARCHIVED.toString());

        // change display priority to ARCHIVED
        Post updatedPost = request.putWithResponseBodyAndParams("/api/courses/" + courseId + "/posts/" + postToArchive.getId() + "/display-priority", null, Post.class,
                HttpStatus.OK, params);
        assertThat(updatedPost).isEqualTo(postToArchive);
    }

    // GET

    @Test
    @WithMockUser(username = "tutor1", roles = "USER")
    public void testGetPostsForCourse() throws Exception {
        // no request params set will fetch all course posts without any context filter
        var params = new LinkedMultiValueMap<String, String>();

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        // get amount of posts with that certain
        assertThat(returnedPosts.size()).isEqualTo(existingPosts.size());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "USER")
    public void testGetPostsForCourse_WithCourseWideContextRequestParam() throws Exception {
        var courseWideContext = CourseWideContext.RANDOM;
        // request param courseWideContext will fetch all course posts that match this context filter
        var params = new LinkedMultiValueMap<String, String>();
        params.add("courseWideContext", courseWideContext.toString());

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        // get amount of posts with that certain course-wide context
        var expectedAmountOfFetchedPosts = existingCourseWidePosts.stream().filter(coursePost -> coursePost.getCourseWideContext() == courseWideContext).count();
        assertThat(returnedPosts.size()).isEqualTo(expectedAmountOfFetchedPosts);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "USER")
    public void testGetPostsForCourse_WithExerciseIdRequestParam() throws Exception {
        // request param courseWideContext will fetch all course posts that match this context filter
        var params = new LinkedMultiValueMap<String, String>();
        params.add("exerciseId", exerciseId.toString());

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        // get amount of posts with that certain course-wide context
        assertThat(returnedPosts.size()).isEqualTo(existingExercisePosts.size());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "USER")
    public void testGetPostsForCourse_WithLectureIdRequestParam() throws Exception {
        // request param courseWideContext will fetch all course posts that match this context filter
        var params = new LinkedMultiValueMap<String, String>();
        params.add("lectureId", lectureId.toString());

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        // get amount of posts with that certain course-wide context
        assertThat(returnedPosts.size()).isEqualTo(existingLecturePosts.size());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "USER")
    public void testGetPostsForCourse_WithInvalidRequestParams_badRequest() throws Exception {
        // request param courseWideContext will fetch all course posts that match this context filter
        var params = new LinkedMultiValueMap<String, String>();
        params.add("lectureId", lectureId.toString());
        params.add("exerciseId", exerciseId.toString());

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.BAD_REQUEST, Post.class, params);
        // get amount of posts with that certain course-wide context
        assertThat(returnedPosts).isNull();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetPostTagsForCourse() throws Exception {
        List<String> returnedTags = request.getList("/api/courses/" + courseId + "/posts/tags", HttpStatus.OK, String.class);
        // 4 different tags were used for the posts
        assertThat(returnedTags.size()).isEqualTo(postRepository.findPostTagsForCourse(courseId).size());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetPostTagsForCourseWithNonExistentCourseId_notFound() throws Exception {
        List<String> returnedTags = request.getList("/api/courses/" + 9999L + "/posts/tags", HttpStatus.NOT_FOUND, String.class);
        assertThat(returnedTags).isNull();
    }

    // DELETE

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testDeletePosts_asStudent() throws Exception {
        // delete own post (index 0)--> OK
        Post postToDelete = existingPosts.get(0);

        request.delete("/api/courses/" + courseId + "/posts/" + postToDelete.getId(), HttpStatus.OK);
        assertThat(postRepository.count()).isEqualTo(existingPosts.size() - 1);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testDeletePosts_asStudent_forbidden() throws Exception {
        // delete post from another student (index 1) --> forbidden
        Post postToNotDelete = existingPosts.get(1);

        request.delete("/api/courses/" + courseId + "/posts/" + postToNotDelete.getId(), HttpStatus.FORBIDDEN);
        assertThat(postRepository.count()).isEqualTo(existingPosts.size());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testDeletePosts_asTutor() throws Exception {
        Post postToDelete = existingLecturePosts.get(0);

        request.delete("/api/courses/" + courseId + "/posts/" + postToDelete.getId(), HttpStatus.OK);
        assertThat(postRepository.findById(postToDelete.getId())).isEmpty();
        assertThat(postRepository.count()).isEqualTo(existingPosts.size() - 1);

        postToDelete = existingExercisePosts.get(0);

        request.delete("/api/courses/" + courseId + "/posts/" + postToDelete.getId(), HttpStatus.OK);
        assertThat(postRepository.count()).isEqualTo(existingPosts.size() - 2);

        postToDelete = existingCourseWidePosts.get(0);

        request.delete("/api/courses/" + courseId + "/posts/" + postToDelete.getId(), HttpStatus.OK);
        assertThat(postRepository.count()).isEqualTo(existingPosts.size() - 3);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testDeleteNonExistentPosts_asTutor_notFound() throws Exception {
        // try to delete non-existing post
        request.delete("/api/courses/" + courseId + "/posts/" + 9999L, HttpStatus.NOT_FOUND);
    }

    // HELPER METHODS

    private Post createPostWithoutContext() {
        Post post = new Post();
        post.setTitle("Title Post");
        post.setContent("Content Post");
        post.setVisibleForStudents(true);
        post.setDisplayPriority(DisplayPriority.NONE);
        post.addTag("Tag");
        return post;
    }

    private Post editExistingPost(Post postToUpdate) {
        postToUpdate.setTitle("New Title");
        postToUpdate.setContent("New Test Post");
        postToUpdate.setVisibleForStudents(false);
        postToUpdate.addTag("New Tag");
        return postToUpdate;
    }

    private void checkCreatedPost(Post expectedPost, Post createdPost) {
        // check if post was created with id
        assertThat(createdPost).isNotNull();
        assertThat(createdPost.getId()).isNotNull();

        // check if title, content, creation data, and tags are set correctly on creation
        assertThat(createdPost.getTitle()).isEqualTo(expectedPost.getTitle());
        assertThat(createdPost.getContent()).isEqualTo(expectedPost.getContent());
        assertThat(createdPost.getCreationDate()).isNotNull();
        assertThat(createdPost.getTags()).isEqualTo(expectedPost.getTags());

        // check if default values are set correctly on creation
        assertThat(createdPost.getAnswers()).isEmpty();
        assertThat(createdPost.getReactions()).isEmpty();
        assertThat(createdPost.getDisplayPriority()).isEqualTo(expectedPost.getDisplayPriority());

        // check if context, i.e. either correct lecture, exercise or course-wide context are set correctly on creation
        assertThat(createdPost.getCourse()).isEqualTo(expectedPost.getCourse());
        assertThat(createdPost.getCourseWideContext()).isEqualTo(expectedPost.getCourseWideContext());
        assertThat(createdPost.getExercise()).isEqualTo(expectedPost.getExercise());
        assertThat(createdPost.getLecture()).isEqualTo(expectedPost.getLecture());
    }
}
