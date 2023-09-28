package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.CourseInformationSharingConfiguration;
import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.CourseWideContext;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.PostSortCriterion;
import de.tum.in.www1.artemis.post.ConversationUtilService;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.metis.AnswerPostRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.user.UserUtilService;

class AnswerPostIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "answerpostintegration";

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ConversationUtilService conversationUtilService;

    private List<Post> existingPostsWithAnswers;

    private List<Post> existingPostsWithAnswersInExercise;

    private List<Post> existingPostsWithAnswersInLecture;

    private List<Post> existingPostsWithAnswersCourseWide;

    private List<AnswerPost> existingAnswerPosts;

    private Long courseId;

    private User student1;

    static final int MAX_POSTS_PER_PAGE = 20;

    @BeforeEach
    void initTestCase() {

        userUtilService.addUsers(TEST_PREFIX, 4, 4, 4, 1);
        student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        // initialize test setup and get all existing posts with answers (four posts, one in each context, are initialized with one answer each): 4 answers in total (with author
        // student1)
        List<Post> existingPostsAndConversationPostsWithAnswers = conversationUtilService.createPostsWithAnswerPostsWithinCourse(TEST_PREFIX).stream()
                .filter(coursePost -> coursePost.getAnswers() != null && coursePost.getPlagiarismCase() == null).toList();

        existingPostsWithAnswers = existingPostsAndConversationPostsWithAnswers.stream().filter(post -> post.getConversation() == null).toList();

        // get all answerPosts
        existingAnswerPosts = existingPostsAndConversationPostsWithAnswers.stream().map(Post::getAnswers).flatMap(Collection::stream).toList();

        // get all existing posts with answers in exercise context
        existingPostsWithAnswersInExercise = existingPostsWithAnswers.stream().filter(coursePost -> coursePost.getAnswers() != null && coursePost.getExercise() != null).toList();

        // get all existing posts with answers in lecture context
        existingPostsWithAnswersInLecture = existingPostsWithAnswers.stream().filter(coursePost -> coursePost.getAnswers() != null && coursePost.getLecture() != null).toList();

        // get all existing posts with answers in lecture context
        existingPostsWithAnswersCourseWide = existingPostsWithAnswers.stream().filter(coursePost -> coursePost.getAnswers() != null && coursePost.getCourseWideContext() != null)
                .toList();

        courseId = existingPostsWithAnswersInExercise.get(0).getExercise().getCourseViaExerciseGroupOrCourseMember().getId();
    }

    // CREATE

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateAnswerPostInLecture() throws Exception {
        AnswerPost answerPostToSave = createAnswerPost(existingPostsWithAnswersInLecture.get(0));
        testAnswerPostCreation(answerPostToSave);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateAnswerPostInExercise() throws Exception {
        AnswerPost answerPostToSave = createAnswerPost(existingPostsWithAnswersInExercise.get(0));
        testAnswerPostCreation(answerPostToSave);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateAnswerPostCourseWide() throws Exception {
        AnswerPost answerPostToSave = createAnswerPost(existingPostsWithAnswersCourseWide.get(0));
        testAnswerPostCreation(answerPostToSave);
    }

    private void testAnswerPostCreation(AnswerPost answerPostToSave) throws Exception {
        var countBefore = answerPostRepository.count();
        AnswerPost createdAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-posts", answerPostToSave, AnswerPost.class, HttpStatus.CREATED);
        conversationUtilService.assertSensitiveInformationHidden(createdAnswerPost);
        // should not be automatically post resolving
        assertThat(createdAnswerPost.doesResolvePost()).isFalse();
        // should increment answer count
        assertThat(postRepository.findPostByIdElseThrow(answerPostToSave.getPost().getId()).getAnswerCount()).isEqualTo(answerPostToSave.getPost().getAnswerCount());
        checkCreatedAnswerPost(answerPostToSave, createdAnswerPost);
        assertThat(countBefore + 1).isEqualTo(answerPostRepository.count());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testPostingAllowedIfMessagingOnlySetting() throws Exception {
        messagingFeatureDisabledTest(CourseInformationSharingConfiguration.MESSAGING_ONLY);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testPostingNotAllowedIfDisabledSetting() throws Exception {
        messagingFeatureDisabledTest(CourseInformationSharingConfiguration.DISABLED);
    }

    private void messagingFeatureDisabledTest(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        var persistedCourse = courseRepository.findByIdElseThrow(courseId);
        persistedCourse.setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);
        persistedCourse = courseRepository.saveAndFlush(persistedCourse);
        assertThat(persistedCourse.getCourseInformationSharingConfiguration()).isEqualTo(courseInformationSharingConfiguration);

        AnswerPost answerPostToSave = createAnswerPost(existingPostsWithAnswersCourseWide.get(0));

        var answerPostCount = answerPostRepository.count();

        request.postWithResponseBody("/api/courses/" + courseId + "/answer-posts", answerPostToSave, AnswerPost.class, HttpStatus.BAD_REQUEST);

        var newAnswerPostCount = answerPostRepository.count() - answerPostCount;
        assertThat(newAnswerPostCount).isZero();

        // active messaging again
        persistedCourse.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        courseRepository.saveAndFlush(persistedCourse);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateAnswerPostInLecture_asInstructor() throws Exception {
        AnswerPost answerPostToSave = createAnswerPost(existingPostsWithAnswersInLecture.get(0));

        AnswerPost createdAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-posts", answerPostToSave, AnswerPost.class, HttpStatus.CREATED);
        conversationUtilService.assertSensitiveInformationHidden(createdAnswerPost);
        // should increment answer count
        assertThat(postRepository.findPostByIdElseThrow(answerPostToSave.getPost().getId()).getAnswerCount()).isEqualTo(answerPostToSave.getPost().getAnswerCount());
        checkCreatedAnswerPost(answerPostToSave, createdAnswerPost);
        assertThat(answerPostRepository.findById(createdAnswerPost.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateAnswerPostInExercise_asInstructor() throws Exception {
        AnswerPost answerPostToSave = createAnswerPost(existingPostsWithAnswersInExercise.get(0));

        AnswerPost createdAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-posts", answerPostToSave, AnswerPost.class, HttpStatus.CREATED);
        conversationUtilService.assertSensitiveInformationHidden(createdAnswerPost);
        // should increment answer count
        assertThat(postRepository.findPostByIdElseThrow(answerPostToSave.getPost().getId()).getAnswerCount()).isEqualTo(answerPostToSave.getPost().getAnswerCount());
        checkCreatedAnswerPost(answerPostToSave, createdAnswerPost);
        assertThat(answerPostRepository.findById(createdAnswerPost.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateAnswerPostCourseWide_asInstructor() throws Exception {
        AnswerPost answerPostToSave = createAnswerPost(existingPostsWithAnswersCourseWide.get(0));

        AnswerPost createdAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-posts", answerPostToSave, AnswerPost.class, HttpStatus.CREATED);
        conversationUtilService.assertSensitiveInformationHidden(createdAnswerPost);
        // should increment answer count
        assertThat(postRepository.findPostByIdElseThrow(answerPostToSave.getPost().getId()).getAnswerCount()).isEqualTo(answerPostToSave.getPost().getAnswerCount());
        checkCreatedAnswerPost(answerPostToSave, createdAnswerPost);
        assertThat(answerPostRepository.findById(createdAnswerPost.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateExistingAnswerPost_badRequest() throws Exception {
        AnswerPost existingAnswerPostToSave = existingAnswerPosts.get(0);

        var answerPostCount = answerPostRepository.count();

        request.postWithResponseBody("/api/courses/" + courseId + "/answer-posts", existingAnswerPostToSave, AnswerPost.class, HttpStatus.BAD_REQUEST);
        // should not increment answer count
        var newAnswerPostCount = answerPostRepository.count() - answerPostCount;
        assertThat(postRepository.findPostByIdElseThrow(existingAnswerPostToSave.getPost().getId()).getAnswerCount())
                .isEqualTo(existingAnswerPostToSave.getPost().getAnswerCount());
        assertThat(newAnswerPostCount).isZero();
    }

    // GET
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPostsForCourse_WithUnresolvedPosts() throws Exception {
        // filterToUnresolved set true; will fetch all unresolved posts of current course
        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToUnresolved", "true");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        // get posts of current user and compare
        List<Post> unresolvedPosts = existingPostsWithAnswers.stream()
                .filter(post -> post.getCourseWideContext() == null || !post.getCourseWideContext().equals(CourseWideContext.ANNOUNCEMENT)
                        && post.getAnswers().stream().noneMatch(answerPost -> Boolean.TRUE.equals(answerPost.doesResolvePost())))
                .toList();

        assertThat(returnedPosts).isEqualTo(unresolvedPosts);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPostsForCourse_WithOwnAndUnresolvedPosts() throws Exception {
        // filterToOwn & filterToUnresolved set true; will fetch all unresolved posts of current user
        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToUnresolved", "true");
        params.add("filterToOwn", "true");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        // get unresolved posts of current user and compare
        List<Post> resolvedPosts = existingPostsWithAnswers.stream().filter(post -> student1.getId().equals(post.getAuthor().getId())
                && (post.getAnswers().stream().noneMatch(answerPost -> Boolean.TRUE.equals(answerPost.doesResolvePost())))).toList();

        assertThat(returnedPosts).isEqualTo(resolvedPosts);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPostsForCourseWithCourseWideContent() throws Exception {
        // filterToUnresolved set true; will fetch all unresolved posts of current course
        var params = new LinkedMultiValueMap<String, String>();
        params.add("courseWideContexts", "TECH_SUPPORT");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        List<Post> resolvedPosts = existingPostsWithAnswers.stream().filter(post -> CourseWideContext.TECH_SUPPORT.equals(post.getCourseWideContext())).toList();

        assertThat(returnedPosts).isEqualTo(resolvedPosts);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPostsForCourseWithUnresolvedPostsWithCourseWideContent() throws Exception {
        // filterToUnresolved set true; will fetch all unresolved posts of current course
        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToUnresolved", "true");
        params.add("courseWideContexts", "TECH_SUPPORT");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        List<Post> resolvedPosts = existingPostsWithAnswers.stream()
                .filter(post -> post.getAnswers().stream().noneMatch(answerPost -> Boolean.TRUE.equals(answerPost.doesResolvePost()))
                        && CourseWideContext.TECH_SUPPORT.equals(post.getCourseWideContext()))
                .toList();

        assertThat(returnedPosts).isEqualTo(resolvedPosts);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPostsForCourseWithOwnWithCourseWideContent() throws Exception {
        // filterToOwn & filterToUnresolved set true; will fetch all unresolved posts of current user
        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToOwn", "true");
        params.add("courseWideContexts", "TECH_SUPPORT");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        List<Post> resolvedPosts = existingPostsWithAnswers.stream()
                .filter(post -> student1.getId().equals(post.getAuthor().getId())
                        && post.getAnswers().stream().noneMatch(answerPost -> Boolean.TRUE.equals(answerPost.doesResolvePost()))
                        && CourseWideContext.TECH_SUPPORT.equals(post.getCourseWideContext()))
                .toList();

        assertThat(returnedPosts).isEqualTo(resolvedPosts);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPostsForCourseWithOwnAndUnresolvedPostsWithCourseWideContent() throws Exception {
        // filterToOwn & filterToUnresolved set true; will fetch all unresolved posts of current user
        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToUnresolved", "true");
        params.add("filterToOwn", "true");
        params.add("courseWideContexts", "TECH_SUPPORT");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        // get unresolved posts of current user and compare
        List<Post> resolvedPosts = existingPostsWithAnswers.stream()
                .filter(post -> student1.getId().equals(post.getAuthor().getId())
                        && post.getAnswers().stream().noneMatch(answerPost -> Boolean.TRUE.equals(answerPost.doesResolvePost()))
                        && CourseWideContext.TECH_SUPPORT.equals(post.getCourseWideContext()))
                .toList();

        assertThat(returnedPosts).isEqualTo(resolvedPosts);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPostsForCourse_OrderByAnswerCountDESC() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();

        // ordering only available in course discussions page, where paging is enabled
        params.add("pagingEnabled", "true");
        params.add("page", "0");
        params.add("size", String.valueOf(MAX_POSTS_PER_PAGE));

        params.add("postSortCriterion", PostSortCriterion.ANSWER_COUNT.toString());
        params.add("sortingOrder", SortingOrder.DESCENDING.toString());

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);

        int numberOfMaxAnswersSeenOnAnyPost = Integer.MAX_VALUE;
        for (Post post : returnedPosts) {
            assertThat(post.getAnswers().size()).isLessThanOrEqualTo(numberOfMaxAnswersSeenOnAnyPost);
            numberOfMaxAnswersSeenOnAnyPost = post.getAnswers().size();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPostsForCourse_OrderByAnswerCountASC() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();

        // ordering only available in course discussions page, where paging is enabled
        params.add("pagingEnabled", "true");
        params.add("page", "0");
        params.add("size", String.valueOf(MAX_POSTS_PER_PAGE));

        params.add("postSortCriterion", PostSortCriterion.ANSWER_COUNT.toString());
        params.add("sortingOrder", SortingOrder.ASCENDING.toString());

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);

        int numberOfMaxAnswersSeenOnAnyPost = 0;
        for (Post post : returnedPosts) {
            assertThat(post.getAnswers().size()).isGreaterThanOrEqualTo(numberOfMaxAnswersSeenOnAnyPost);
            numberOfMaxAnswersSeenOnAnyPost = post.getAnswers().size();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAnsweredOrReactedPostsByUserForCourse() throws Exception {

        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToAnsweredOrReacted", "true");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        existingPostsWithAnswers = existingPostsWithAnswers.stream()
                .filter(post -> post.getAnswers().stream().anyMatch(answerPost -> student1.getId().equals(answerPost.getAuthor().getId()))
                        || post.getReactions().stream().anyMatch(reaction -> student1.getId().equals(post.getAuthor().getId())))
                .toList();

        assertThat(returnedPosts).isEqualTo(existingPostsWithAnswers);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetOwnAndAnsweredOrReactedPostsByUserForCourse() throws Exception {

        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToAnsweredOrReacted", "true");
        params.add("filterToOwn", "true");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        existingPostsWithAnswers = existingPostsWithAnswers.stream().filter(
                post -> student1.getId().equals(post.getAuthor().getId()) && (post.getAnswers().stream().anyMatch(answerPost -> student1.getId().equals(post.getAuthor().getId()))
                        || post.getReactions().stream().anyMatch(reaction -> student1.getId().equals(post.getAuthor().getId()))))
                .toList();

        assertThat(returnedPosts).isEqualTo(existingPostsWithAnswers);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetUnresolvedAnsweredOrReactedPostsByUserForCourse() throws Exception {

        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToAnsweredOrReacted", "true");
        params.add("filterToUnresolved", "true");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        existingPostsWithAnswers = existingPostsWithAnswers.stream()
                .filter(post -> post.getAnswers().stream().noneMatch(answerPost -> Boolean.TRUE.equals(answerPost.doesResolvePost()))
                        && (post.getAnswers().stream().anyMatch(answerPost -> student1.getId().equals(answerPost.getAuthor().getId()))
                                || post.getReactions().stream().anyMatch(reaction -> student1.getId().equals(reaction.getUser().getId()))))
                .toList();

        assertThat(returnedPosts).isEqualTo(existingPostsWithAnswers);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetUnresolvedOwnAnsweredOrReactedPostsByUserForCourse() throws Exception {

        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToOwn", "true");
        params.add("filterToUnresolved", "true");
        params.add("filterToAnsweredOrReacted", "true");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        existingPostsWithAnswers = existingPostsWithAnswers.stream().filter(
                post -> student1.getId().equals(post.getAuthor().getId()) && (post.getAnswers().stream().noneMatch(answerPost -> Boolean.TRUE.equals(answerPost.doesResolvePost()))
                        && (post.getAnswers().stream().anyMatch(answerPost -> student1.getId().equals(post.getAuthor().getId()))
                                || post.getReactions().stream().anyMatch(reaction -> student1.getId().equals(reaction.getUser().getId())))))
                .toList();

        assertThat(returnedPosts).isEqualTo(existingPostsWithAnswers);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAnsweredOrReactedPostsByUserForCourseWithCourseWideContent() throws Exception {

        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToAnsweredOrReacted", "true");
        params.add("courseWideContexts", "TECH_SUPPORT");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        existingPostsWithAnswersCourseWide = existingPostsWithAnswersCourseWide.stream()
                .filter(post -> CourseWideContext.TECH_SUPPORT.equals(post.getCourseWideContext())
                        && (post.getAnswers().stream().anyMatch(answerPost -> student1.getId().equals(post.getAuthor().getId()))
                                || post.getReactions().stream().anyMatch(reaction -> student1.getId().equals(reaction.getUser().getId()))))
                .toList();

        assertThat(returnedPosts).isEqualTo(existingPostsWithAnswersCourseWide);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetOwnAndAnsweredOrReactedPostsByUserForCourseWithCourseWideContent() throws Exception {

        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToAnsweredOrReacted", "true");
        params.add("filterToOwn", "true");
        params.add("courseWideContexts", "TECH_SUPPORT");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        existingPostsWithAnswersCourseWide = existingPostsWithAnswersCourseWide.stream()
                .filter(post -> CourseWideContext.TECH_SUPPORT.equals(post.getCourseWideContext()) && student1.getId().equals(post.getAuthor().getId())
                        && (post.getAnswers().stream().anyMatch(answerPost -> student1.getId().equals(post.getAuthor().getId()))
                                || post.getReactions().stream().anyMatch(reaction -> student1.getId().equals(reaction.getUser().getId()))))
                .toList();

        assertThat(returnedPosts).isEqualTo(existingPostsWithAnswersCourseWide);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetUnresolvedAnsweredOrReactedPostsByUserForCourseWithCourseWideContent() throws Exception {

        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToAnsweredOrReacted", "true");
        params.add("filterToUnresolved", "true");
        params.add("courseWideContexts", "TECH_SUPPORT");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        existingPostsWithAnswersCourseWide = existingPostsWithAnswersCourseWide.stream()
                .filter(post -> CourseWideContext.TECH_SUPPORT.equals(post.getCourseWideContext())
                        && post.getAnswers().stream().noneMatch(answerPost -> Boolean.TRUE.equals(answerPost.doesResolvePost()))
                        && (post.getAnswers().stream().anyMatch(answerPost -> student1.getId().equals(post.getAuthor().getId()))
                                || post.getReactions().stream().anyMatch(reaction -> student1.getId().equals(reaction.getUser().getId()))))
                .toList();

        assertThat(returnedPosts).isEqualTo(existingPostsWithAnswersCourseWide);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetUnresolvedOwnAnsweredOrReactedPostsByUserForCourseWithCourseWideContent() throws Exception {

        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToOwn", "true");
        params.add("filterToUnresolved", "true");
        params.add("filterToAnsweredOrReacted", "true");
        params.add("courseWideContexts", "TECH_SUPPORT");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        existingPostsWithAnswersCourseWide = existingPostsWithAnswersCourseWide.stream()
                .filter(post -> CourseWideContext.TECH_SUPPORT.equals(post.getCourseWideContext()) && student1.getId().equals(post.getAuthor().getId())
                        && (post.getAnswers().stream().noneMatch(answerPost -> Boolean.TRUE.equals(answerPost.doesResolvePost()))
                                && (post.getAnswers().stream().anyMatch(answerPost -> student1.getId().equals(post.getAuthor().getId()))
                                        || post.getReactions().stream().anyMatch(reaction -> student1.getId().equals(reaction.getUser().getId())))))
                .toList();

        assertThat(returnedPosts).isEqualTo(existingPostsWithAnswersCourseWide);
    }
    // UPDATE

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testEditAnswerPost_asTutor() throws Exception {
        // update post of student1 (index 0)--> OK
        AnswerPost answerPostToUpdate = editExistingAnswerPost(existingAnswerPosts.get(0));

        AnswerPost updatedAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPostToUpdate.getId(), answerPostToUpdate, AnswerPost.class,
                HttpStatus.OK);
        conversationUtilService.assertSensitiveInformationHidden(updatedAnswerPost);
        assertThat(answerPostToUpdate).isEqualTo(updatedAnswerPost);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testEditAnswerPost_asStudent1() throws Exception {
        // update own post (index 0)--> OK
        AnswerPost answerPostToUpdate = editExistingAnswerPost(existingAnswerPosts.get(0));

        AnswerPost updatedAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPostToUpdate.getId(), answerPostToUpdate, AnswerPost.class,
                HttpStatus.OK);
        conversationUtilService.assertSensitiveInformationHidden(updatedAnswerPost);
        assertThat(answerPostToUpdate).isEqualTo(updatedAnswerPost);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testEditAnswerPost_asStudent2_forbidden() throws Exception {
        // update post from another student (index 1)--> forbidden
        AnswerPost answerPostNotToUpdate = editExistingAnswerPost(existingAnswerPosts.get(1));

        AnswerPost notUpdatedAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPostNotToUpdate.getId(), answerPostNotToUpdate,
                AnswerPost.class, HttpStatus.FORBIDDEN);
        assertThat(notUpdatedAnswerPost).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testEditAnswerPostWithIdIsNull_badRequest() throws Exception {
        AnswerPost answerPostToUpdate = createAnswerPost(existingPostsWithAnswersCourseWide.get(0));

        AnswerPost updatedAnswerPostServer = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPostToUpdate.getId(), answerPostToUpdate,
                AnswerPost.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedAnswerPostServer).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testEditAnswerPostWithWrongCourseId_badRequest() throws Exception {
        AnswerPost answerPostToUpdate = createAnswerPost(existingPostsWithAnswersCourseWide.get(0));
        Course dummyCourse = courseUtilService.createCourse();

        AnswerPost updatedAnswerPostServer = request.putWithResponseBody("/api/courses/" + dummyCourse.getId() + "/answer-posts/" + answerPostToUpdate.getId(), answerPostToUpdate,
                AnswerPost.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedAnswerPostServer).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testToggleResolvesPost() throws Exception {
        AnswerPost answerPost = existingAnswerPosts.get(1);
        AnswerPost answerPost2 = existingAnswerPosts.get(2);

        // confirm that answer post resolves the original post
        answerPost.setResolvesPost(true);
        AnswerPost resolvingAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPost.getId(), answerPost, AnswerPost.class,
                HttpStatus.OK);
        assertThat(resolvingAnswerPost).isEqualTo(answerPost);
        // confirm that the post is marked as resolved when it has a resolving answer
        assertThat(postRepository.findPostByIdElseThrow(resolvingAnswerPost.getPost().getId()).isResolved()).isTrue();

        answerPost2.setResolvesPost(true);
        request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPost2.getId(), answerPost2, AnswerPost.class, HttpStatus.OK);

        // revoke that answer post resolves the original post
        answerPost.setResolvesPost(false);
        AnswerPost notResolvingAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPost.getId(), answerPost, AnswerPost.class,
                HttpStatus.OK);
        assertThat(notResolvingAnswerPost).isEqualTo(answerPost);

        // confirm that the post is still marked as resolved since it still has a resolving answer
        assertThat(postRepository.findPostByIdElseThrow(resolvingAnswerPost.getPost().getId()).isResolved()).isTrue();

        // revoke that answer post2 resolves the original post
        answerPost2.setResolvesPost(false);
        request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPost2.getId(), answerPost2, AnswerPost.class, HttpStatus.OK);

        // confirm that the post is marked as unresolved when it no longer has a resolving answer
        assertThat(postRepository.findPostByIdElseThrow(resolvingAnswerPost.getPost().getId()).isResolved()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testToggleResolvesPost_asPostAuthor() throws Exception {
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
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testToggleResolvesPost_notAuthor_forbidden() throws Exception {
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
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeleteAnswerPosts_asStudent1() throws Exception {
        var answerPostCount = answerPostRepository.count();
        // delete own post (index 0)--> OK
        AnswerPost answerPostToDelete = existingAnswerPosts.get(0);

        request.delete("/api/courses/" + courseId + "/answer-posts/" + answerPostToDelete.getId(), HttpStatus.OK);
        var newAnswerPostCount = answerPostRepository.count() - answerPostCount;
        assertThat(newAnswerPostCount).isEqualTo(-1);
        // should decrement answerCount
        assertThat(postRepository.findPostByIdElseThrow(answerPostToDelete.getPost().getId()).getAnswerCount()).isEqualTo(answerPostToDelete.getPost().getAnswerCount() - 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testDeleteAnswerPosts_asStudent2_forbidden() throws Exception {
        var answerPostCount = answerPostRepository.count();
        // delete post from another student (index 0) --> forbidden
        AnswerPost answerPostToNotDelete = existingAnswerPosts.get(0);

        request.delete("/api/courses/" + courseId + "/answer-posts/" + answerPostToNotDelete.getId(), HttpStatus.FORBIDDEN);
        var newAnswerPostCount = answerPostRepository.count() - answerPostCount;
        assertThat(newAnswerPostCount).isZero();
        // should not decrement answerCount
        assertThat(postRepository.findPostByIdElseThrow(answerPostToNotDelete.getPost().getId()).getAnswerCount()).isEqualTo(answerPostToNotDelete.getPost().getAnswerCount());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testDeleteAnswerPost_asTutor() throws Exception {
        var answerPostCount = answerPostRepository.count();
        // delete post from another student (index 0) --> ok
        AnswerPost answerPostToDelete = existingAnswerPosts.get(0);

        request.delete("/api/courses/" + courseId + "/answer-posts/" + answerPostToDelete.getId(), HttpStatus.OK);
        var newAnswerPostCount = answerPostRepository.count() - answerPostCount;
        assertThat(newAnswerPostCount).isEqualTo(-1);
        // should decrement answerCount
        assertThat(postRepository.findPostByIdElseThrow(answerPostToDelete.getPost().getId()).getAnswerCount()).isEqualTo(answerPostToDelete.getPost().getAnswerCount() - 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testDeleteAnswerPost_asTutor_notFound() throws Exception {
        var countBefore = answerPostRepository.count();
        request.delete("/api/courses/" + courseId + "/answer-posts/" + 9999L, HttpStatus.NOT_FOUND);
        assertThat(answerPostRepository.count()).isEqualTo(countBefore);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testDeleteResolvingAnswerPost_asTutor() throws Exception {
        AnswerPost answerPostToDeleteWhichResolves = existingAnswerPosts.get(3);

        var countBefore = answerPostRepository.count();
        request.delete("/api/courses/" + courseId + "/answer-posts/" + answerPostToDeleteWhichResolves.getId(), HttpStatus.OK);
        assertThat(answerPostRepository.count()).isEqualTo(countBefore - 1);

        Post persistedPost = postRepository.findPostByIdElseThrow(answerPostToDeleteWhichResolves.getPost().getId());

        // should update post resolved status to false
        assertThat(persistedPost.isResolved()).isFalse();

        // should decrement answerCount
        assertThat(persistedPost.getAnswerCount()).isEqualTo(answerPostToDeleteWhichResolves.getPost().getAnswerCount() - 1);
    }

    // HELPER METHODS

    private AnswerPost createAnswerPost(Post post) {
        AnswerPost answerPost = new AnswerPost();
        answerPost.setContent("Content Answer Post");
        answerPost.setPost(post);
        post.addAnswerPost(answerPost);
        post.setAnswerCount(post.getAnswerCount() + 1);
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
