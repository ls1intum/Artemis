package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.CourseWideContext;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.PostSortCriterion;
import de.tum.in.www1.artemis.repository.metis.AnswerPostRepository;

public class AnswerPostIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private AnswerPostRepository answerPostRepository;

    private List<Post> existingPostsWithAnswers;

    private List<Post> existingPostsWithAnswersInExercise;

    private List<Post> existingPostsWithAnswersInLecture;

    private List<Post> existingPostsWithAnswersCourseWide;

    private List<AnswerPost> existingAnswerPosts;

    private Long courseId;

    private static final int MAX_POSTS_PER_PAGE = 20;

    @BeforeEach
    public void initTestCase() {

        database.addUsers(5, 5, 0, 1);

        // initialize test setup and get all existing posts with answers (three posts, one in each context, are initialized with one answer each): 3 answers in total (with author
        // student1)
        existingPostsWithAnswers = database.createPostsWithAnswerPostsWithinCourse().stream().filter(coursePost -> (coursePost.getAnswers() != null)).toList();

        // get all answerPosts
        existingAnswerPosts = existingPostsWithAnswers.stream().map(Post::getAnswers).flatMap(Collection::stream).toList();

        // get all existing posts with answers in exercise context
        existingPostsWithAnswersInExercise = existingPostsWithAnswers.stream().filter(coursePost -> (coursePost.getAnswers() != null) && coursePost.getExercise() != null).toList();

        // get all existing posts with answers in lecture context
        existingPostsWithAnswersInLecture = existingPostsWithAnswers.stream().filter(coursePost -> (coursePost.getAnswers() != null) && coursePost.getLecture() != null).toList();

        // get all existing posts with answers in lecture context
        existingPostsWithAnswersCourseWide = existingPostsWithAnswers.stream().filter(coursePost -> (coursePost.getAnswers() != null) && coursePost.getCourseWideContext() != null)
                .toList();

        courseId = existingPostsWithAnswersInExercise.get(0).getExercise().getCourseViaExerciseGroupOrCourseMember().getId();
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    // CREATE

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCreateAnswerPostInLecture() throws Exception {
        AnswerPost answerPostToSave = createAnswerPost(existingPostsWithAnswersInLecture.get(0));

        AnswerPost createdAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-posts", answerPostToSave, AnswerPost.class, HttpStatus.CREATED);
        // should not be automatically post resolving
        assertThat(createdAnswerPost.doesResolvePost()).isFalse();
        checkCreatedAnswerPost(answerPostToSave, createdAnswerPost);
        assertThat(existingAnswerPosts.size() + 1).isEqualTo(answerPostRepository.count());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCreateAnswerPostInExercise() throws Exception {
        AnswerPost answerPostToSave = createAnswerPost(existingPostsWithAnswersInExercise.get(0));

        AnswerPost createdAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-posts", answerPostToSave, AnswerPost.class, HttpStatus.CREATED);
        // should not be automatically post resolving
        assertThat(createdAnswerPost.doesResolvePost()).isFalse();
        checkCreatedAnswerPost(answerPostToSave, createdAnswerPost);
        assertThat(existingAnswerPosts.size() + 1).isEqualTo(answerPostRepository.count());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCreateAnswerPostCourseWide() throws Exception {
        AnswerPost answerPostToSave = createAnswerPost(existingPostsWithAnswersCourseWide.get(0));

        AnswerPost createdAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-posts", answerPostToSave, AnswerPost.class, HttpStatus.CREATED);
        // should not be automatically post resolving
        assertThat(createdAnswerPost.doesResolvePost()).isFalse();
        checkCreatedAnswerPost(answerPostToSave, createdAnswerPost);
        assertThat(existingAnswerPosts.size() + 1).isEqualTo(answerPostRepository.count());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreateAnswerPostInLecture_asInstructor() throws Exception {
        AnswerPost answerPostToSave = createAnswerPost(existingPostsWithAnswersInLecture.get(0));

        AnswerPost createdAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-posts", answerPostToSave, AnswerPost.class, HttpStatus.CREATED);
        checkCreatedAnswerPost(answerPostToSave, createdAnswerPost);
        assertThat(existingAnswerPosts.size() + 1).isEqualTo(answerPostRepository.count());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreateAnswerPostInExercise_asInstructor() throws Exception {
        AnswerPost answerPostToSave = createAnswerPost(existingPostsWithAnswersInExercise.get(0));

        AnswerPost createdAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-posts", answerPostToSave, AnswerPost.class, HttpStatus.CREATED);
        checkCreatedAnswerPost(answerPostToSave, createdAnswerPost);
        assertThat(existingAnswerPosts.size() + 1).isEqualTo(answerPostRepository.count());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreateAnswerPostCourseWide_asInstructor() throws Exception {
        AnswerPost answerPostToSave = createAnswerPost(existingPostsWithAnswersCourseWide.get(0));

        AnswerPost createdAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-posts", answerPostToSave, AnswerPost.class, HttpStatus.CREATED);
        checkCreatedAnswerPost(answerPostToSave, createdAnswerPost);
        assertThat(existingAnswerPosts.size() + 1).isEqualTo(answerPostRepository.count());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCreateExistingAnswerPost_badRequest() throws Exception {
        AnswerPost existingAnswerPostToSave = existingAnswerPosts.get(0);

        request.postWithResponseBody("/api/courses/" + courseId + "/answer-posts", existingAnswerPostToSave, AnswerPost.class, HttpStatus.BAD_REQUEST);
        assertThat(existingAnswerPosts.size()).isEqualTo(answerPostRepository.count());
    }

    // GET
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetPostsForCourse_WithUnresolvedPosts() throws Exception {
        // filterToUnresolved set true; will fetch all unresolved posts of current course
        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToUnresolved", "true");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        // get posts of current user and compare
        List<Post> resolvedPosts = existingPostsWithAnswers.stream()
                .filter(post -> post.getAnswers().stream().allMatch(answerPost -> !Boolean.TRUE.equals(answerPost.doesResolvePost()))).toList();

        assertThat(returnedPosts).isEqualTo(resolvedPosts);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetPostsForCourse_WithOwnAndUnresolvedPosts() throws Exception {
        // filterToOwn & filterToUnresolved set true; will fetch all unresolved posts of current user
        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToUnresolved", "true");
        params.add("filterToOwn", "true");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        // get unresolved posts of current user and compare
        List<Post> resolvedPosts = existingPostsWithAnswers.stream().filter(
                post -> "student1".equals(post.getAuthor().getLogin()) && (post.getAnswers().stream().allMatch(answerPost -> !Boolean.TRUE.equals(answerPost.doesResolvePost()))))
                .toList();

        assertThat(returnedPosts).isEqualTo(resolvedPosts);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetPostsForCourseWithCourseWideContent() throws Exception {
        // filterToUnresolved set true; will fetch all unresolved posts of current course
        var params = new LinkedMultiValueMap<String, String>();
        params.add("courseWideContext", "TECH_SUPPORT");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        List<Post> resolvedPosts = existingPostsWithAnswers.stream().filter(post -> CourseWideContext.TECH_SUPPORT.equals(post.getCourseWideContext())).toList();

        assertThat(returnedPosts).isEqualTo(resolvedPosts);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetPostsForCourseWithUnresolvedPostsWithCourseWideContent() throws Exception {
        // filterToUnresolved set true; will fetch all unresolved posts of current course
        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToUnresolved", "true");
        params.add("courseWideContext", "TECH_SUPPORT");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        List<Post> resolvedPosts = existingPostsWithAnswers.stream()
                .filter(post -> post.getAnswers().stream().allMatch(answerPost -> !Boolean.TRUE.equals(answerPost.doesResolvePost()))
                        && CourseWideContext.TECH_SUPPORT.equals(post.getCourseWideContext()))
                .toList();

        assertThat(returnedPosts).isEqualTo(resolvedPosts);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetPostsForCourseWithOwnWithCourseWideContent() throws Exception {
        // filterToOwn & filterToUnresolved set true; will fetch all unresolved posts of current user
        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToOwn", "true");
        params.add("courseWideContext", "TECH_SUPPORT");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        List<Post> resolvedPosts = existingPostsWithAnswers.stream()
                .filter(post -> "student1".equals(post.getAuthor().getLogin())
                        && post.getAnswers().stream().allMatch(answerPost -> !Boolean.TRUE.equals(answerPost.doesResolvePost()))
                        && CourseWideContext.TECH_SUPPORT.equals(post.getCourseWideContext()))
                .toList();

        assertThat(returnedPosts).isEqualTo(resolvedPosts);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetPostsForCourseWithOwnAndUnresolvedPostsWithCourseWideContent() throws Exception {
        // filterToOwn & filterToUnresolved set true; will fetch all unresolved posts of current user
        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToUnresolved", "true");
        params.add("filterToOwn", "true");
        params.add("courseWideContext", "TECH_SUPPORT");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        // get unresolved posts of current user and compare
        List<Post> resolvedPosts = existingPostsWithAnswers.stream()
                .filter(post -> "student1".equals(post.getAuthor().getLogin())
                        && post.getAnswers().stream().allMatch(answerPost -> !Boolean.TRUE.equals(answerPost.doesResolvePost()))
                        && CourseWideContext.TECH_SUPPORT.equals(post.getCourseWideContext()))
                .toList();

        assertThat(returnedPosts).isEqualTo(resolvedPosts);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetPostsForCourse_OrderByAnswerCountDESC() throws Exception {
        PostSortCriterion sortCriterion = PostSortCriterion.ANSWER_COUNT;
        SortingOrder sortingOrder = SortingOrder.DESCENDING;

        var params = new LinkedMultiValueMap<String, String>();

        // ordering only available in course discussions page, where paging is enabled
        params.add("pagingEnabled", "true");
        params.add("page", "0");
        params.add("size", String.valueOf(MAX_POSTS_PER_PAGE));

        params.add("postSortCriterion", sortCriterion.toString());
        params.add("sortingOrder", sortingOrder.toString());

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        existingPostsWithAnswers = existingPostsWithAnswers.stream().sorted(Comparator.comparing((Post post) -> post.getAnswers().size()).reversed()).toList();

        assertThat(returnedPosts).isEqualTo(existingPostsWithAnswers);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetPostsForCourse_OrderByAnswerCountASC() throws Exception {
        PostSortCriterion sortCriterion = PostSortCriterion.ANSWER_COUNT;
        SortingOrder sortingOrder = SortingOrder.ASCENDING;

        var params = new LinkedMultiValueMap<String, String>();

        // ordering only available in course discussions page, where paging is enabled
        params.add("pagingEnabled", "true");
        params.add("page", "0");
        params.add("size", String.valueOf(MAX_POSTS_PER_PAGE));

        params.add("postSortCriterion", sortCriterion.toString());
        params.add("sortingOrder", sortingOrder.toString());

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        existingPostsWithAnswers = existingPostsWithAnswers.stream().sorted(Comparator.comparing(post -> post.getAnswers().size())).toList();

        assertThat(returnedPosts).isEqualTo(existingPostsWithAnswers);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetAnsweredOrReactedPostsByUserForCourse() throws Exception {

        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToAnsweredOrReacted", "true");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        existingPostsWithAnswers = existingPostsWithAnswers.stream()
                .filter(post -> post.getAnswers().stream().anyMatch(answerPost -> "student1".equals(answerPost.getAuthor().getLogin()))
                        || post.getReactions().stream().anyMatch(reaction -> "student1".equals(reaction.getUser().getLogin())))
                .toList();

        assertThat(returnedPosts).isEqualTo(existingPostsWithAnswers);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetOwnAndAnsweredOrReactedPostsByUserForCourse() throws Exception {

        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToAnsweredOrReacted", "true");
        params.add("filterToOwn", "true");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        existingPostsWithAnswers = existingPostsWithAnswers.stream().filter(
                post -> "student1".equals(post.getAuthor().getLogin()) && (post.getAnswers().stream().anyMatch(answerPost -> "student1".equals(answerPost.getAuthor().getLogin()))
                        || post.getReactions().stream().anyMatch(reaction -> "student1".equals(reaction.getUser().getLogin()))))
                .toList();

        assertThat(returnedPosts).isEqualTo(existingPostsWithAnswers);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetUnresolvedAnsweredOrReactedPostsByUserForCourse() throws Exception {

        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToAnsweredOrReacted", "true");
        params.add("filterToUnresolved", "true");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        existingPostsWithAnswers = existingPostsWithAnswers.stream()
                .filter(post -> post.getAnswers().stream().allMatch(answerPost -> !Boolean.TRUE.equals(answerPost.doesResolvePost()))
                        && (post.getAnswers().stream().anyMatch(answerPost -> "student1".equals(answerPost.getAuthor().getLogin()))
                                || post.getReactions().stream().anyMatch(reaction -> "student1".equals(reaction.getUser().getLogin()))))
                .toList();

        assertThat(returnedPosts).isEqualTo(existingPostsWithAnswers);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetUnresolvedOwnAnsweredOrReactedPostsByUserForCourse() throws Exception {

        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToOwn", "true");
        params.add("filterToUnresolved", "true");
        params.add("filterToAnsweredOrReacted", "true");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        existingPostsWithAnswers = existingPostsWithAnswers.stream().filter(
                post -> "student1".equals(post.getAuthor().getLogin()) && (post.getAnswers().stream().allMatch(answerPost -> !Boolean.TRUE.equals(answerPost.doesResolvePost()))
                        && (post.getAnswers().stream().anyMatch(answerPost -> "student1".equals(answerPost.getAuthor().getLogin()))
                                || post.getReactions().stream().anyMatch(reaction -> "student1".equals(reaction.getUser().getLogin())))))
                .toList();

        assertThat(returnedPosts).isEqualTo(existingPostsWithAnswers);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetAnsweredOrReactedPostsByUserForCourseWithCourseWideContent() throws Exception {

        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToAnsweredOrReacted", "true");
        params.add("courseWideContext", "TECH_SUPPORT");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        existingPostsWithAnswersCourseWide = existingPostsWithAnswersCourseWide.stream()
                .filter(post -> CourseWideContext.TECH_SUPPORT.equals(post.getCourseWideContext())
                        && (post.getAnswers().stream().anyMatch(answerPost -> "student1".equals(answerPost.getAuthor().getLogin()))
                                || post.getReactions().stream().anyMatch(reaction -> "student1".equals(reaction.getUser().getLogin()))))
                .toList();

        assertThat(returnedPosts).isEqualTo(existingPostsWithAnswersCourseWide);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetOwnAndAnsweredOrReactedPostsByUserForCourseWithCourseWideContent() throws Exception {

        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToAnsweredOrReacted", "true");
        params.add("filterToOwn", "true");
        params.add("courseWideContext", "TECH_SUPPORT");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        existingPostsWithAnswersCourseWide = existingPostsWithAnswersCourseWide.stream()
                .filter(post -> CourseWideContext.TECH_SUPPORT.equals(post.getCourseWideContext()) && "student1".equals(post.getAuthor().getLogin())
                        && (post.getAnswers().stream().anyMatch(answerPost -> "student1".equals(answerPost.getAuthor().getLogin()))
                                || post.getReactions().stream().anyMatch(reaction -> "student1".equals(reaction.getUser().getLogin()))))
                .toList();

        assertThat(returnedPosts).isEqualTo(existingPostsWithAnswersCourseWide);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetUnresolvedAnsweredOrReactedPostsByUserForCourseWithCourseWideContent() throws Exception {

        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToAnsweredOrReacted", "true");
        params.add("filterToUnresolved", "true");
        params.add("courseWideContext", "TECH_SUPPORT");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        existingPostsWithAnswersCourseWide = existingPostsWithAnswersCourseWide.stream()
                .filter(post -> CourseWideContext.TECH_SUPPORT.equals(post.getCourseWideContext())
                        && post.getAnswers().stream().allMatch(answerPost -> !Boolean.TRUE.equals(answerPost.doesResolvePost()))
                        && (post.getAnswers().stream().anyMatch(answerPost -> "student1".equals(answerPost.getAuthor().getLogin()))
                                || post.getReactions().stream().anyMatch(reaction -> "student1".equals(reaction.getUser().getLogin()))))
                .toList();

        assertThat(returnedPosts).isEqualTo(existingPostsWithAnswersCourseWide);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetUnresolvedOwnAnsweredOrReactedPostsByUserForCourseWithCourseWideContent() throws Exception {

        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToOwn", "true");
        params.add("filterToUnresolved", "true");
        params.add("filterToAnsweredOrReacted", "true");
        params.add("courseWideContext", "TECH_SUPPORT");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        existingPostsWithAnswersCourseWide = existingPostsWithAnswersCourseWide.stream()
                .filter(post -> CourseWideContext.TECH_SUPPORT.equals(post.getCourseWideContext()) && "student1".equals(post.getAuthor().getLogin())
                        && (post.getAnswers().stream().allMatch(answerPost -> !Boolean.TRUE.equals(answerPost.doesResolvePost()))
                                && (post.getAnswers().stream().anyMatch(answerPost -> "student1".equals(answerPost.getAuthor().getLogin()))
                                        || post.getReactions().stream().anyMatch(reaction -> "student1".equals(reaction.getUser().getLogin())))))
                .toList();

        assertThat(returnedPosts).isEqualTo(existingPostsWithAnswersCourseWide);
    }
    // UPDATE

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testEditAnswerPost_asTutor() throws Exception {
        // update post of student1 (index 0)--> OK
        AnswerPost answerPostToUpdate = editExistingAnswerPost(existingAnswerPosts.get(0));

        AnswerPost updatedAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPostToUpdate.getId(), answerPostToUpdate, AnswerPost.class,
                HttpStatus.OK);
        assertThat(answerPostToUpdate).isEqualTo(updatedAnswerPost);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testEditAnswerPost_asStudent1() throws Exception {
        // update own post (index 0)--> OK
        AnswerPost answerPostToUpdate = editExistingAnswerPost(existingAnswerPosts.get(0));

        AnswerPost updatedAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPostToUpdate.getId(), answerPostToUpdate, AnswerPost.class,
                HttpStatus.OK);
        assertThat(answerPostToUpdate).isEqualTo(updatedAnswerPost);
    }

    @Test
    @WithMockUser(username = "student2", roles = "USER")
    public void testEditAnswerPost_asStudent2_forbidden() throws Exception {
        // update post from another student (index 1)--> forbidden
        AnswerPost answerPostNotToUpdate = editExistingAnswerPost(existingAnswerPosts.get(1));

        AnswerPost notUpdatedAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPostNotToUpdate.getId(), answerPostNotToUpdate,
                AnswerPost.class, HttpStatus.FORBIDDEN);
        assertThat(notUpdatedAnswerPost).isNull();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testEditAnswerPostWithIdIsNull_badRequest() throws Exception {
        AnswerPost answerPostToUpdate = createAnswerPost(existingPostsWithAnswersCourseWide.get(0));

        AnswerPost updatedAnswerPostServer = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPostToUpdate.getId(), answerPostToUpdate,
                AnswerPost.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedAnswerPostServer).isNull();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testEditAnswerPostWithWrongCourseId_badRequest() throws Exception {
        AnswerPost answerPostToUpdate = createAnswerPost(existingPostsWithAnswersCourseWide.get(0));
        Course dummyCourse = database.createCourse();

        AnswerPost updatedAnswerPostServer = request.putWithResponseBody("/api/courses/" + dummyCourse.getId() + "/answer-posts/" + answerPostToUpdate.getId(), answerPostToUpdate,
                AnswerPost.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedAnswerPostServer).isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testToggleResolvesPost() throws Exception {
        AnswerPost answerPost = existingAnswerPosts.get(0);

        // confirm that answer post resolves the original post
        answerPost.setResolvesPost(true);
        AnswerPost resolvingAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPost.getId(), answerPost, AnswerPost.class,
                HttpStatus.OK);
        assertThat(resolvingAnswerPost).isEqualTo(answerPost);

        // revoke that answer post resolves the original post
        answerPost.setResolvesPost(false);
        AnswerPost notResolvingAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPost.getId(), answerPost, AnswerPost.class,
                HttpStatus.OK);
        assertThat(notResolvingAnswerPost).isEqualTo(answerPost);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testToggleResolvesPost_asPostAuthor() throws Exception {
        // author of the associated original post is student1
        AnswerPost answerPost = existingAnswerPosts.get(0);

        // confirm that answer post resolves the original post
        answerPost.setResolvesPost(true);
        AnswerPost resolvingAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPost.getId(), answerPost, AnswerPost.class,
                HttpStatus.OK);
        assertThat(resolvingAnswerPost).isEqualTo(answerPost);

        // revoke that answer post resolves the original post
        answerPost.setResolvesPost(false);
        AnswerPost notResolvingAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPost.getId(), answerPost, AnswerPost.class,
                HttpStatus.OK);
        assertThat(notResolvingAnswerPost).isEqualTo(answerPost);
    }

    @Test
    @WithMockUser(username = "student2", roles = "USER")
    public void testToggleResolvesPost_notAuthor_forbidden() throws Exception {
        // author of the associated original post is student1, author of answer post is also student1
        AnswerPost answerPost = existingAnswerPosts.get(0);

        // confirm that answer post resolves the original post
        answerPost.setResolvesPost(true);
        AnswerPost resolvingAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPost.getId(), answerPost, AnswerPost.class,
                HttpStatus.FORBIDDEN);
        assertThat(resolvingAnswerPost).isNull();

        // revoke that answer post resolves the original post
        answerPost.setResolvesPost(false);
        AnswerPost notResolvingAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPost.getId(), answerPost, AnswerPost.class,
                HttpStatus.FORBIDDEN);
        assertThat(notResolvingAnswerPost).isNull();
    }

    // DELETE

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testDeleteAnswerPosts_asStudent1() throws Exception {
        // delete own post (index 0)--> OK
        AnswerPost answerPostToDelete = existingAnswerPosts.get(0);

        request.delete("/api/courses/" + courseId + "/answer-posts/" + answerPostToDelete.getId(), HttpStatus.OK);
        assertThat(answerPostRepository.count()).isEqualTo(existingAnswerPosts.size() - 1);
    }

    @Test
    @WithMockUser(username = "student2", roles = "USER")
    public void testDeleteAnswerPosts_asStudent2_forbidden() throws Exception {
        // delete post from another student (index 0) --> forbidden
        AnswerPost answerPostToNotDelete = existingAnswerPosts.get(0);

        request.delete("/api/courses/" + courseId + "/answer-posts/" + answerPostToNotDelete.getId(), HttpStatus.FORBIDDEN);
        assertThat(answerPostRepository.count()).isEqualTo(existingAnswerPosts.size());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testDeleteAnswerPost_asTutor() throws Exception {
        // delete post from another student (index 0) --> ok
        AnswerPost answerPostToDelete = existingAnswerPosts.get(0);

        request.delete("/api/courses/" + courseId + "/answer-posts/" + answerPostToDelete.getId(), HttpStatus.OK);
        assertThat(answerPostRepository.count()).isEqualTo(existingAnswerPosts.size() - 1);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testDeleteAnswerPost_asTutor_notFound() throws Exception {
        request.delete("/api/courses/" + courseId + "/answer-posts/" + 9999L, HttpStatus.NOT_FOUND);
        assertThat(answerPostRepository.count()).isEqualTo(existingAnswerPosts.size());
    }

    // HELPER METHODS

    private AnswerPost createAnswerPost(Post post) {
        AnswerPost answerPost = new AnswerPost();
        answerPost.setContent("Content Answer Post");
        answerPost.setPost(post);
        post.addAnswerPost(answerPost);
        return answerPost;
    }

    private AnswerPost editExistingAnswerPost(AnswerPost answerPostToUpdate) {
        answerPostToUpdate.setContent("New Test Answer Post");
        return answerPostToUpdate;
    }

    private void checkCreatedAnswerPost(AnswerPost expectedAnswerPost, AnswerPost createdAnswerPost) {
        // check if answerPost was created with id
        assertThat(createdAnswerPost).isNotNull();
        assertThat(createdAnswerPost.getId()).isNotNull();

        // check if associated post, answerPost content, and creation date are set correctly on creation
        assertThat(createdAnswerPost.getPost()).isEqualTo(expectedAnswerPost.getPost());
        assertThat(createdAnswerPost.getContent()).isEqualTo(expectedAnswerPost.getContent());
        assertThat(createdAnswerPost.getCreationDate()).isNotNull();

        // check if default values are set correctly on creation
        assertThat(createdAnswerPost.getReactions()).isEmpty();
    }
}
