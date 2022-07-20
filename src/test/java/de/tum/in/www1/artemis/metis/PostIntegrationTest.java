package de.tum.in.www1.artemis.metis;

import static de.tum.in.www1.artemis.service.metis.PostService.TOP_K_SIMILARITY_RESULTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.metis.CourseWideContext;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.web.rest.dto.PostContextFilter;

class PostIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    private List<Post> existingPosts;

    private List<Post> existingCoursePosts;

    private List<Post> existingExercisePosts;

    private List<Post> existingLecturePosts;

    private List<Post> existingPlagiarismPosts;

    private List<Post> existingCourseWidePosts;

    private Course course;

    private Long courseId;

    private Long exerciseId;

    private Long lectureId;

    private Long plagiarismCaseId;

    private Validator validator;

    private User student1;

    private static final int MAX_POSTS_PER_PAGE = 20;

    @BeforeEach
    void initTestCase() {

        // used to test hibernate validation using custom PostContextConstraintValidator
        validator = Validation.buildDefaultValidatorFactory().getValidator();

        database.addUsers(5, 5, 0, 1);

        student1 = database.getUserByLogin("student1");

        // initialize test setup and get all existing posts (there are 4 posts with lecture context, 4 with exercise context,
        // 1 plagiarism case and 3 with course-wide context - initialized): 12 posts in total
        existingPosts = database.createPostsWithinCourse();

        existingCoursePosts = existingPosts.stream().filter(post -> post.getPlagiarismCase() == null).toList();

        // filter existing posts with exercise context
        existingExercisePosts = existingCoursePosts.stream().filter(coursePost -> (coursePost.getExercise() != null)).toList();

        // filter existing posts with lecture context
        existingLecturePosts = existingCoursePosts.stream().filter(coursePost -> (coursePost.getLecture() != null)).toList();

        // filter existing posts with plagiarism context
        existingPlagiarismPosts = existingPosts.stream().filter(coursePost -> coursePost.getPlagiarismCase() != null).toList();

        // filter existing posts with course-wide context
        existingCourseWidePosts = existingCoursePosts.stream().filter(coursePost -> (coursePost.getCourseWideContext() != null)).toList();

        course = existingExercisePosts.get(0).getExercise().getCourseViaExerciseGroupOrCourseMember();

        courseId = course.getId();

        exerciseId = existingExercisePosts.get(0).getExercise().getId();

        lectureId = existingLecturePosts.get(0).getLecture().getId();

        plagiarismCaseId = existingPlagiarismPosts.get(0).getPlagiarismCase().getId();

        GroupNotificationService groupNotificationService = mock(GroupNotificationService.class);
        doNothing().when(groupNotificationService).notifyAllGroupsAboutNewPostForExercise(any(), any());
        doNothing().when(groupNotificationService).notifyAllGroupsAboutNewPostForLecture(any(), any());
        doNothing().when(groupNotificationService).notifyAllGroupsAboutNewCoursePost(any(), any());
        doNothing().when(groupNotificationService).notifyAllGroupsAboutNewAnnouncement(any(), any());
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    // POST

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreateExercisePost() throws Exception {
        Post postToSave = createPostWithoutContext();
        Exercise exercise = existingExercisePosts.get(0).getExercise();
        postToSave.setExercise(exercise);

        Post createdPost = request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.CREATED);
        database.assertSensitiveInformationHidden(createdPost);
        checkCreatedPost(postToSave, createdPost);

        PostContextFilter postContextFilter = new PostContextFilter();
        postContextFilter.setExerciseId(exerciseId);
        assertThat(existingExercisePosts).hasSize(postRepository.findPosts(postContextFilter, null, false, null).getSize() - 1);
        verify(groupNotificationService, times(1)).notifyAllGroupsAboutNewPostForExercise(createdPost, course);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreateExamExercisePost_badRequest() throws Exception {
        Exam exam = database.setupSimpleExamWithExerciseGroupExercise(course);
        Post postToSave = createPostWithoutContext();
        Exercise examExercise = exam.getExerciseGroups().get(0).getExercises().stream().findFirst().orElseThrow();
        postToSave.setExercise(examExercise);

        request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.BAD_REQUEST);

        PostContextFilter postContextFilter = new PostContextFilter();
        postContextFilter.setExerciseId(exerciseId);
        assertThat(existingExercisePosts).hasSameSizeAs(postRepository.findPosts(postContextFilter, null, false, null));
        verify(groupNotificationService, times(0)).notifyAllGroupsAboutNewPostForExercise(any(), any());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreateLecturePost() throws Exception {
        Post postToSave = createPostWithoutContext();
        Lecture lecture = existingLecturePosts.get(0).getLecture();
        postToSave.setLecture(lecture);

        Post createdPost = request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.CREATED);
        database.assertSensitiveInformationHidden(createdPost);
        checkCreatedPost(postToSave, createdPost);

        PostContextFilter postContextFilter = new PostContextFilter();
        postContextFilter.setLectureId(lectureId);
        assertThat(existingLecturePosts).hasSize(postRepository.findPosts(postContextFilter, null, false, null).getSize() - 1);
        verify(groupNotificationService, times(1)).notifyAllGroupsAboutNewPostForLecture(createdPost, course);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreateCourseWidePost() throws Exception {
        Post postToSave = createPostWithoutContext();
        postToSave.setCourse(course);
        postToSave.setCourseWideContext(existingCourseWidePosts.get(0).getCourseWideContext());

        Post createdPost = request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.CREATED);
        database.assertSensitiveInformationHidden(createdPost);
        checkCreatedPost(postToSave, createdPost);

        PostContextFilter postContextFilter = new PostContextFilter();
        postContextFilter.setCourseId(courseId);

        List<Post> updatedCourseWidePosts = postRepository.findPosts(postContextFilter, null, false, null).stream().filter(post -> post.getCourseWideContext() != null).toList();
        assertThat(existingCourseWidePosts).hasSize(updatedCourseWidePosts.size() - 1);
        verify(groupNotificationService, times(1)).notifyAllGroupsAboutNewCoursePost(createdPost, course);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCreateAnnouncement() throws Exception {
        Post postToSave = createPostWithoutContext();
        postToSave.setCourse(course);
        postToSave.setCourseWideContext(CourseWideContext.ANNOUNCEMENT);

        Post createdPost = request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.CREATED);
        database.assertSensitiveInformationHidden(createdPost);
        postToSave.setDisplayPriority(DisplayPriority.PINNED);
        checkCreatedPost(postToSave, createdPost);

        PostContextFilter postContextFilter = new PostContextFilter();
        postContextFilter.setCourseId(courseId);

        List<Post> updatedCourseWidePosts = postRepository.findPosts(postContextFilter, null, false, null).stream().filter(post -> post.getCourseWideContext() != null).toList();
        assertThat(existingCourseWidePosts).hasSize(updatedCourseWidePosts.size() - 1);
        verify(groupNotificationService, times(1)).notifyAllGroupsAboutNewAnnouncement(createdPost, course);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testCreateAnnouncement_asStudent_forbidden() throws Exception {
        Post postToSave = createPostWithoutContext();
        postToSave.setCourse(course);
        postToSave.setCourseWideContext(CourseWideContext.ANNOUNCEMENT);

        request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.FORBIDDEN);
        assertThat(existingPosts.size()).isEqualTo(postRepository.count());
        verify(groupNotificationService, times(0)).notifyAllGroupsAboutNewAnnouncement(any(), any());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCreatePlagiarismPost() throws Exception {
        doNothing().when(singleUserNotificationService).notifyUserAboutNewPlagiarismCase(any(), any());

        Post postToSave = createPostWithoutContext();
        postToSave.setCourse(course);

        final PlagiarismCase plagiarismCase = plagiarismCaseRepository.save(new PlagiarismCase());
        postToSave.setPlagiarismCase(plagiarismCase);

        request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.CREATED);

        List<Post> updatedPlagiarismCasePosts = postRepository.findPostsByPlagiarismCaseId(plagiarismCase.getId());
        assertThat(updatedPlagiarismCasePosts).hasSize(1);
        verify(singleUserNotificationService, times(1)).notifyUserAboutNewPlagiarismCase(any(), any());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreateExistingPost_badRequest() throws Exception {
        Post existingPostToSave = existingPosts.get(0);

        request.postWithResponseBody("/api/courses/" + courseId + "/posts", existingPostToSave, Post.class, HttpStatus.BAD_REQUEST);
        assertThat(existingPosts.size()).isEqualTo(postRepository.count());
        verify(groupNotificationService, times(0)).notifyAllGroupsAboutNewPostForExercise(any(), any());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreatePostForCourseWithDisabledPosts_badRequest() throws Exception {
        Course course = database.createCourseWithPostsDisabled();
        courseId = course.getId();
        Post postToSave = createPostWithoutContext();
        postToSave.setCourse(course);
        postToSave.setCourseWideContext(CourseWideContext.RANDOM);

        request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.BAD_REQUEST);
        verify(groupNotificationService, times(0)).notifyAllGroupsAboutNewCoursePost(any(), any());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreateEmptyPostWithParsingError() throws Exception {
        Post postToSave = createPostWithoutContext();
        Exercise exercise = existingExercisePosts.get(0).getExercise();
        postToSave.setExercise(exercise);
        postToSave.setContent("");

        Post createdPost = request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.CREATED);
        database.assertSensitiveInformationHidden(createdPost);
        Post expectedPost = new Post();
        expectedPost.setId(createdPost.getId());
        expectedPost.setAuthor(createdPost.getAuthor());
        expectedPost.setCourse(course);
        expectedPost.setExercise(createdPost.getExercise());
        expectedPost.setTitle(createdPost.getTitle());
        expectedPost.setCreationDate(createdPost.getCreationDate());
        // if error occurs during parsing markdown to html, the content will be replaced by an empty string
        expectedPost.setContent("");

        verify(groupNotificationService, times(1)).notifyAllGroupsAboutNewPostForExercise(expectedPost, course);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testValidatePostContextConstraintViolation() throws Exception {
        Post invalidPost = createPostWithoutContext();
        request.postWithResponseBody("/api/courses/" + courseId + "/posts", invalidPost, Post.class, HttpStatus.BAD_REQUEST);

        invalidPost = createPostWithoutContext();
        invalidPost.setCourseWideContext(CourseWideContext.ORGANIZATION);
        invalidPost.setLecture(existingLecturePosts.get(0).getLecture());
        request.postWithResponseBody("/api/courses/" + courseId + "/posts", invalidPost, Post.class, HttpStatus.BAD_REQUEST);
        Set<ConstraintViolation<Post>> constraintViolations = validator.validate(invalidPost);
        assertThat(constraintViolations).hasSize(1);

        invalidPost = createPostWithoutContext();
        invalidPost.setCourseWideContext(CourseWideContext.ORGANIZATION);
        invalidPost.setExercise(existingExercisePosts.get(0).getExercise());
        request.postWithResponseBody("/api/courses/" + courseId + "/posts", invalidPost, Post.class, HttpStatus.BAD_REQUEST);
        constraintViolations = validator.validate(invalidPost);
        assertThat(constraintViolations).hasSize(1);

        invalidPost = createPostWithoutContext();
        invalidPost.setLecture(existingLecturePosts.get(0).getLecture());
        invalidPost.setExercise(existingExercisePosts.get(0).getExercise());
        request.postWithResponseBody("/api/courses/" + courseId + "/posts", invalidPost, Post.class, HttpStatus.BAD_REQUEST);
        constraintViolations = validator.validate(invalidPost);
        assertThat(constraintViolations).hasSize(1);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testSimilarityCheck() throws Exception {
        Post postToCheck = new Post();
        postToCheck.setTitle("Title Post");

        List<Post> similarPosts = request.postListWithResponseBody("/api/courses/" + courseId + "/posts/similarity-check", postToCheck, Post.class, HttpStatus.OK);
        assertThat(similarPosts).hasSize(TOP_K_SIMILARITY_RESULTS);
    }

    // UPDATE

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testEditAnnouncement_asTutor_forbidden() throws Exception {
        Post postToUpdate = editExistingPost(existingCourseWidePosts.get(0));
        // simulate as if it was an announcement
        postToUpdate.setCourseWideContext(CourseWideContext.ANNOUNCEMENT);

        Post notUpdatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts/" + postToUpdate.getId(), postToUpdate, Post.class, HttpStatus.FORBIDDEN);
        assertThat(notUpdatedPost).isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testEditPost_asTutor() throws Exception {
        // update post of student1 (index 0)--> OK
        Post postToUpdate = editExistingPost(existingPosts.get(0));

        Post updatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts/" + postToUpdate.getId(), postToUpdate, Post.class, HttpStatus.OK);
        database.assertSensitiveInformationHidden(updatedPost);
        assertThat(updatedPost).isEqualTo(postToUpdate);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testEditPostByChangingContext1_asTutor() throws Exception {
        // update exercise post
        Post postToUpdate = existingExercisePosts.get(0);
        // change to context to lecture
        postToUpdate.setExercise(null);
        postToUpdate.setLecture(this.existingLecturePosts.get(0).getLecture());

        Post updatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts/" + postToUpdate.getId(), postToUpdate, Post.class, HttpStatus.OK);
        database.assertSensitiveInformationHidden(updatedPost);
        assertThat(updatedPost).isEqualTo(postToUpdate);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testEditPostByChangingContext2_asTutor() throws Exception {
        // update lecture post
        Post postToUpdate = existingLecturePosts.get(0);
        // change to context to exercise
        postToUpdate.setLecture(null);
        postToUpdate.setExercise(this.existingExercisePosts.get(0).getExercise());

        Post updatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts/" + postToUpdate.getId(), postToUpdate, Post.class, HttpStatus.OK);
        database.assertSensitiveInformationHidden(updatedPost);
        assertThat(updatedPost).isEqualTo(postToUpdate);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testEditPostByChangingContext3_asTutor() throws Exception {
        // update course-wide post
        Post postToUpdate = existingCourseWidePosts.get(0);
        // change to context to lecture
        postToUpdate.setCourseWideContext(null);
        postToUpdate.setCourse(null);
        postToUpdate.setLecture(this.existingLecturePosts.get(0).getLecture());

        Post updatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts/" + postToUpdate.getId(), postToUpdate, Post.class, HttpStatus.OK);
        database.assertSensitiveInformationHidden(updatedPost);
        assertThat(updatedPost).isEqualTo(postToUpdate);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testEditPostByChangingContext4_asTutor() throws Exception {
        // update course post
        Post postToUpdate = existingCourseWidePosts.get(0);
        // change to course post with different course-wide context
        postToUpdate.setCourseWideContext(CourseWideContext.RANDOM);

        Post updatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts/" + postToUpdate.getId(), postToUpdate, Post.class, HttpStatus.OK);
        database.assertSensitiveInformationHidden(updatedPost);
        assertThat(updatedPost).isEqualTo(postToUpdate);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testEditPost_forbidden() throws Exception {
        // update own post (index 0)--> OK
        Post postToUpdate = editExistingPost(existingPosts.get(0));

        Post updatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts/" + postToUpdate.getId(), postToUpdate, Post.class, HttpStatus.OK);
        database.assertSensitiveInformationHidden(updatedPost);
        assertThat(updatedPost).isEqualTo(postToUpdate);

        // update post from another student (index 1)--> forbidden
        Post postToNotUpdate = editExistingPost(existingPosts.get(1));

        Post notUpdatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts/" + postToNotUpdate.getId(), postToNotUpdate, Post.class, HttpStatus.FORBIDDEN);
        assertThat(notUpdatedPost).isNull();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testEditPostByChangingContext_asStudent() throws Exception {
        // update exercise post
        Post postToNotUpdate = existingExercisePosts.get(0);
        // change to context to lecture
        postToNotUpdate.setExercise(null);
        postToNotUpdate.setLecture(this.existingLecturePosts.get(0).getLecture());

        Post notUpdatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts/" + postToNotUpdate.getId(), postToNotUpdate, Post.class, HttpStatus.OK);
        database.assertSensitiveInformationHidden(notUpdatedPost);
        // no effect on post context
        assertThat(notUpdatedPost.getCourseWideContext()).isNull();
        assertThat(notUpdatedPost.getCourse()).isNull();
        assertThat(notUpdatedPost.getLecture()).isNull();
        assertThat(notUpdatedPost.getExercise()).isEqualTo(existingExercisePosts.get(2).getExercise());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testEditPost_asStudent_forbidden() throws Exception {
        // update post from another student (index 1)--> forbidden
        Post postToNotUpdate = editExistingPost(existingPosts.get(1));

        Post notUpdatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts/" + postToNotUpdate.getId(), postToNotUpdate, Post.class, HttpStatus.FORBIDDEN);
        assertThat(notUpdatedPost).isNull();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testEditPostWithIdIsNull_badRequest() throws Exception {
        Post postToUpdate = existingPosts.get(0);
        postToUpdate.setId(null);

        Post updatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts/" + postToUpdate.getId(), postToUpdate, Post.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedPost).isNull();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testPinPost_asStudent_forbidden() throws Exception {
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
    void testPinPost_asTutor() throws Exception {
        Post postToPin = editExistingPost(existingPosts.get(0));
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("displayPriority", DisplayPriority.PINNED.toString());

        // change display priority to PINNED
        Post updatedPost = request.putWithResponseBodyAndParams("/api/courses/" + courseId + "/posts/" + postToPin.getId() + "/display-priority", null, Post.class, HttpStatus.OK,
                params);
        database.assertSensitiveInformationHidden(updatedPost);
        assertThat(updatedPost).isEqualTo(postToPin);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testArchivePost_asStudent_forbidden() throws Exception {
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
    void testArchivePost_asTutor() throws Exception {
        Post postToArchive = editExistingPost(existingPosts.get(0));
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("displayPriority", DisplayPriority.ARCHIVED.toString());

        // change display priority to ARCHIVED
        Post updatedPost = request.putWithResponseBodyAndParams("/api/courses/" + courseId + "/posts/" + postToArchive.getId() + "/display-priority", null, Post.class,
                HttpStatus.OK, params);
        database.assertSensitiveInformationHidden(updatedPost);
        assertThat(updatedPost).isEqualTo(postToArchive);
    }

    // GET

    @Test
    @WithMockUser(username = "tutor1", roles = "USER")
    void testGetPostsForCourse() throws Exception {
        // no request params set will fetch all course posts without any context filter
        var params = new LinkedMultiValueMap<String, String>();

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        // get amount of posts with that certain
        database.assertSensitiveInformationHidden(returnedPosts);
        assertThat(returnedPosts).hasSameSizeAs(existingCoursePosts);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "USER")
    void testGetPostsForCourse_WithCourseWideContextRequestParam() throws Exception {
        var courseWideContext = CourseWideContext.RANDOM;
        // request param courseWideContext will fetch all course posts that match this context filter
        var params = new LinkedMultiValueMap<String, String>();
        params.add("courseWideContext", courseWideContext.toString());

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        database.assertSensitiveInformationHidden(returnedPosts);
        // get amount of posts with that certain course-wide context
        var expectedAmountOfFetchedPosts = existingCourseWidePosts.stream().filter(coursePost -> coursePost.getCourseWideContext() == courseWideContext).count();
        assertThat(returnedPosts.size()).isEqualTo(expectedAmountOfFetchedPosts);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "USER")
    void testGetPostsForCourse_WithExerciseIdRequestParam() throws Exception {
        // request param courseWideContext will fetch all course posts that match this context filter
        var params = new LinkedMultiValueMap<String, String>();
        params.add("exerciseId", exerciseId.toString());

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        database.assertSensitiveInformationHidden(returnedPosts);
        // get amount of posts with that certain course-wide context
        assertThat(returnedPosts).hasSameSizeAs(existingExercisePosts);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "USER")
    void testGetPostsForCourse_WithLectureIdRequestParam() throws Exception {
        // request param courseWideContext will fetch all course posts that match this context filter
        var params = new LinkedMultiValueMap<String, String>();
        params.add("lectureId", lectureId.toString());

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        database.assertSensitiveInformationHidden(returnedPosts);
        // get amount of posts with that certain course-wide context
        assertThat(returnedPosts).hasSameSizeAs(existingLecturePosts);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetPostsForCourse_WithPlagiarismCaseIdRequestParam_asInstructor() throws Exception {
        // request param plagiarismCaseId will fetch all posts that match this context filter
        var params = new LinkedMultiValueMap<String, String>();
        params.add("plagiarismCaseId", plagiarismCaseId.toString());

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        database.assertSensitiveInformationHidden(returnedPosts);
        // get amount of posts with certain plagiarism context
        assertThat(returnedPosts).hasSameSizeAs(existingPlagiarismPosts);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetPostsForCourse_WithPlagiarismCaseIdRequestParam_asStudent() throws Exception {
        // request param plagiarismCaseId will fetch all posts that match this context filter
        var params = new LinkedMultiValueMap<String, String>();
        params.add("plagiarismCaseId", plagiarismCaseId.toString());

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        database.assertSensitiveInformationHidden(returnedPosts);
        // get amount of posts with certain plagiarism context
        assertThat(returnedPosts).hasSameSizeAs(existingPlagiarismPosts);
    }

    @Test
    @WithMockUser(username = "student2", roles = "USER")
    void testGetPostsForCourse_WithPlagiarismCaseIdRequestParam_asStudent_Forbidden() throws Exception {
        // request param plagiarismCaseId will fetch all posts that match this context filter
        var params = new LinkedMultiValueMap<String, String>();
        params.add("plagiarismCaseId", plagiarismCaseId.toString());

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.FORBIDDEN, Post.class, params);
        assertThat(returnedPosts).isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "USER")
    void testGetPostsForCourse_WithInvalidRequestParams_badRequest() throws Exception {
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
    void testGetPostTagsForCourse() throws Exception {
        List<String> returnedTags = request.getList("/api/courses/" + courseId + "/posts/tags", HttpStatus.OK, String.class);
        // 4 different tags were used for the posts
        assertThat(returnedTags).hasSameSizeAs(postRepository.findPostTagsForCourse(courseId));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetPostTagsForCourseWithNonExistentCourseId_notFound() throws Exception {
        List<String> returnedTags = request.getList("/api/courses/" + 9999L + "/posts/tags", HttpStatus.NOT_FOUND, String.class);
        assertThat(returnedTags).isNull();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetPostsForCourse_WithUsersOwnPosts() throws Exception {
        // filterToOwn set; will fetch all course posts of current logged-in user
        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToOwn", "true");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        database.assertSensitiveInformationHidden(returnedPosts);
        // get posts of current user and compare
        assertThat(returnedPosts).isEqualTo(existingCoursePosts.stream().filter(post -> student1.getId().equals(post.getAuthor().getId())).toList());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetUnresolvedPostsPostsForCourse_AnnouncementsFilteredOut() throws Exception {
        // filterToUnresolved set true; will filter out announcements as they are resolved by default
        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToUnresolved", "true");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        database.assertSensitiveInformationHidden(returnedPosts);
        // get posts of current user without announcements and compare
        List<Post> postsWithoutAnnouncements = existingCoursePosts.stream()
                .filter(post -> (post.getCourseWideContext() == null || !post.getCourseWideContext().equals(CourseWideContext.ANNOUNCEMENT))).toList();

        assertThat(returnedPosts).isEqualTo(postsWithoutAnnouncements);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetPostsForCourse_WithPostId() throws Exception {

        var params = new LinkedMultiValueMap<String, String>();
        params.add("searchText", "#1");
        params.add("pagingEnabled", "true"); // search by text

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        database.assertSensitiveInformationHidden(returnedPosts);
        assertThat(returnedPosts).isEqualTo(existingPosts.stream().filter(post -> post.getId() == 1).toList());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(strings = { "Title Post 1", "Content Post 1", "Tag 1" })
    @WithMockUser(username = "student1", roles = "USER")
    void testGetPostsForCourse_SearchByTitleOrContentOrTag(String searchText) throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("searchText", searchText);
        params.add("pagingEnabled", "true"); // search by text

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        database.assertSensitiveInformationHidden(returnedPosts);
        assertThat(returnedPosts).hasSize(3);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetPostsForCourse_OrderByCreationDateDESC() throws Exception {
        // TODO: Disabled until next refactoring due to incompatibility of DISTINCT & ORDER BY in H2 DB during testing
        // TODO: https://github.com/h2database/h2database/issues/408

        // PostSortCriterion sortCriterion = PostSortCriterion.CREATION_DATE;
        // SortingOrder sortingOrder = SortingOrder.DESCENDING;
        //
        // var params = new LinkedMultiValueMap<String, String>();
        //
        // // ordering only available in course discussions page, where paging is enabled
        // params.add("pagingEnabled", "true");
        // params.add("page", "0");
        // params.add("size", String.valueOf(MAX_POSTS_PER_PAGE));
        //
        // params.add("postSortCriterion", sortCriterion.toString());
        // params.add("sortingOrder", sortingOrder.toString());
        //
        // List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        // database.assertSensitiveInformationHidden(returnedPosts);
        // existingPosts.sort(Comparator.comparing(Post::getCreationDate).reversed());
        //
        // assertThat(returnedPosts).isEqualTo(existingPosts);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetPostsForCourse_OrderByCreationDateASC() throws Exception {
        // TODO: Disabled until next refactoring due to incompatibility of DISTINCT & ORDER BY in H2 DB during testing
        // TODO: https://github.com/h2database/h2database/issues/408

        // PostSortCriterion sortCriterion = PostSortCriterion.CREATION_DATE;
        // SortingOrder sortingOrder = SortingOrder.ASCENDING;
        //
        // var params = new LinkedMultiValueMap<String, String>();
        //
        // // ordering only available in course discussions page, where paging is enabled
        // params.add("pagingEnabled", "true");
        // params.add("page", "0");
        // params.add("size", String.valueOf(MAX_POSTS_PER_PAGE));
        //
        // params.add("postSortCriterion", sortCriterion.toString());
        // params.add("sortingOrder", sortingOrder.toString());
        //
        // List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        // database.assertSensitiveInformationHidden(returnedPosts);
        // existingPosts.sort(Comparator.comparing(Post::getCreationDate));
        //
        // assertThat(returnedPosts).isEqualTo(existingPosts);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetPostsPageForCourse() throws Exception {
        // pagingEnabled set; will fetch a page of course posts
        var params = new LinkedMultiValueMap<String, String>();

        params.add("pagingEnabled", "true");
        // Valid page request
        params.add("page", "0");
        params.add("size", String.valueOf(MAX_POSTS_PER_PAGE));

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        database.assertSensitiveInformationHidden(returnedPosts);
        assertThat(returnedPosts.size()).isIn(returnedPosts.size(), MAX_POSTS_PER_PAGE);
    }

    // DELETE

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testDeletePosts_asStudent() throws Exception {
        // delete own post (index 0)--> OK
        Post postToDelete = existingPosts.get(0);

        request.delete("/api/courses/" + courseId + "/posts/" + postToDelete.getId(), HttpStatus.OK);
        assertThat(postRepository.count()).isEqualTo(existingPosts.size() - 1);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testDeletePosts_asStudent_forbidden() throws Exception {
        // delete post from another student (index 1) --> forbidden
        Post postToNotDelete = existingPosts.get(1);

        request.delete("/api/courses/" + courseId + "/posts/" + postToNotDelete.getId(), HttpStatus.FORBIDDEN);
        assertThat(postRepository.count()).isEqualTo(existingPosts.size());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testDeleteAnnouncement_asTutor_forbidden() throws Exception {
        Post postToNotDelete = existingCourseWidePosts.get(1);
        // simulate as if it was an announcement
        postToNotDelete.setCourseWideContext(CourseWideContext.ANNOUNCEMENT);
        postRepository.save(postToNotDelete);

        request.delete("/api/courses/" + courseId + "/posts/" + postToNotDelete.getId(), HttpStatus.FORBIDDEN);
        assertThat(postRepository.count()).isEqualTo(existingPosts.size());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testDeletePosts_asTutor() throws Exception {
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
    void testDeleteNonExistentPosts_asTutor_notFound() throws Exception {
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
