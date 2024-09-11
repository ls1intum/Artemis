package de.tum.cit.aet.artemis.plagiarism;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.enumeration.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.domain.metis.AnswerPost;
import de.tum.cit.aet.artemis.domain.metis.Post;
import de.tum.cit.aet.artemis.post.ConversationUtilService;

class PlagiarismAnswerPostIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "answerpostintegration";

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ConversationUtilService conversationUtilService;

    private List<Post> existingPostsWithAnswers;

    private List<AnswerPost> existingAnswerPosts;

    private Long courseId;

    @BeforeEach
    void initTestCase() {

        userUtilService.addUsers(TEST_PREFIX, 4, 4, 4, 1);

        // initialize test setup and get all existing posts with answers (four posts, one in each context, are initialized with one answer each): 4 answers in total (with author
        // student1)
        List<Post> existingPostsAndConversationPostsWithAnswers = conversationUtilService.createPostsWithAnswerPostsWithinCourse(TEST_PREFIX).stream()
                .filter(coursePost -> coursePost.getAnswers() != null && !coursePost.getAnswers().isEmpty()).toList();

        existingPostsWithAnswers = existingPostsAndConversationPostsWithAnswers.stream().filter(post -> post.getPlagiarismCase() != null).toList();

        // get all answerPosts
        existingAnswerPosts = existingPostsWithAnswers.stream().map(Post::getAnswers).flatMap(Collection::stream).toList();

        courseId = existingPostsWithAnswers.getFirst().getPlagiarismCase().getExercise().getCourseViaExerciseGroupOrCourseMember().getId();
    }

    @ParameterizedTest
    @MethodSource("userMentionProvider")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateAnswerPostWithUserMention(String userMention, boolean isUserMentionValid) throws Exception {
        AnswerPost answerPostToSave = createAnswerPost(existingPostsWithAnswers.getFirst());
        answerPostToSave.setContent(userMention);

        if (!isUserMentionValid) {
            request.postWithResponseBody("/api/courses/" + courseId + "/messages", answerPostToSave, Post.class, HttpStatus.BAD_REQUEST);
            return;
        }

        AnswerPost createdAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-posts", answerPostToSave, AnswerPost.class, HttpStatus.CREATED);
        conversationUtilService.assertSensitiveInformationHidden(createdAnswerPost);
        // should not be automatically post resolving
        assertThat(createdAnswerPost.doesResolvePost()).isFalse();
        // should increment answer count
        assertThat(postRepository.findPostByIdElseThrow(answerPostToSave.getPost().getId()).getAnswerCount()).isEqualTo(answerPostToSave.getPost().getAnswerCount());
        checkCreatedAnswerPost(answerPostToSave, createdAnswerPost);
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

        AnswerPost answerPostToSave = createAnswerPost(existingPostsWithAnswers.getFirst());

        var answerPostCount = answerPostRepository.count();

        request.postWithResponseBody("/api/courses/" + courseId + "/answer-posts", answerPostToSave, AnswerPost.class, HttpStatus.CREATED);

        var newAnswerPostCount = answerPostRepository.count() - answerPostCount;
        assertThat(newAnswerPostCount).isOne();

        // active messaging again
        persistedCourse.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        courseRepository.saveAndFlush(persistedCourse);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateExistingAnswerPost_badRequest() throws Exception {
        AnswerPost existingAnswerPostToSave = existingAnswerPosts.getFirst();

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
    void testGetPlagiarismPostsForCourse() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("plagiarismCaseId", existingPostsWithAnswers.getFirst().getPlagiarismCase().getId().toString());

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        assertThat(returnedPosts).isEqualTo(existingPostsWithAnswers);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetPlagiarismPostsForCourse_Forbidden() throws Exception {
        // filterToOwn & filterToUnresolved set true; will fetch all unresolved posts of current user
        var params = new LinkedMultiValueMap<String, String>();
        params.add("plagiarismCaseId", existingPostsWithAnswers.getFirst().getPlagiarismCase().getId().toString());

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.FORBIDDEN, Post.class, params);
        assertThat(returnedPosts).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPlagiarismPostsForCourse_BadRequest() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.BAD_REQUEST, Post.class, params);
        assertThat(returnedPosts).isNull();
    }

    // UPDATE

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER ")
    void testEditAnswerPost_asStudent_Forbidden() throws Exception {
        // update post of student1 (index 0)--> FORBIDDEN
        AnswerPost answerPostToUpdate = editExistingAnswerPost(existingAnswerPosts.getFirst());

        AnswerPost updatedAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPostToUpdate.getId(), answerPostToUpdate, AnswerPost.class,
                HttpStatus.FORBIDDEN);
        assertThat(updatedAnswerPost).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testEditAnswerPost_asStudent1() throws Exception {
        // update own post (index 0)--> OK
        AnswerPost answerPostToUpdate = editExistingAnswerPost(existingAnswerPosts.getFirst());

        AnswerPost updatedAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPostToUpdate.getId(), answerPostToUpdate, AnswerPost.class,
                HttpStatus.OK);
        conversationUtilService.assertSensitiveInformationHidden(updatedAnswerPost);
        assertThat(answerPostToUpdate).isEqualTo(updatedAnswerPost);
    }

    @ParameterizedTest
    @MethodSource("userMentionProvider")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testEditAnswerPostWithUserMention(String userMention, boolean isUserMentionValid) throws Exception {
        // update own post (index 0)--> OK
        AnswerPost answerPostToUpdate = editExistingAnswerPost(existingAnswerPosts.getFirst());
        answerPostToUpdate.setContent(userMention);

        if (!isUserMentionValid) {
            request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPostToUpdate.getId(), answerPostToUpdate, AnswerPost.class, HttpStatus.BAD_REQUEST);
            return;
        }

        AnswerPost updatedAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPostToUpdate.getId(), answerPostToUpdate, AnswerPost.class,
                HttpStatus.OK);
        conversationUtilService.assertSensitiveInformationHidden(updatedAnswerPost);
        assertThat(answerPostToUpdate).isEqualTo(updatedAnswerPost);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testEditAnswerPost_asStudent2_forbidden() throws Exception {
        // update post from another student (index 1)--> forbidden
        AnswerPost answerPostNotToUpdate = editExistingAnswerPost(existingAnswerPosts.getFirst());

        AnswerPost notUpdatedAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPostNotToUpdate.getId(), answerPostNotToUpdate,
                AnswerPost.class, HttpStatus.FORBIDDEN);
        assertThat(notUpdatedAnswerPost).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testEditAnswerPostWithIdIsNull_badRequest() throws Exception {
        AnswerPost answerPostToUpdate = createAnswerPost(existingPostsWithAnswers.getFirst());

        AnswerPost updatedAnswerPostServer = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPostToUpdate.getId(), answerPostToUpdate,
                AnswerPost.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedAnswerPostServer).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testEditAnswerPostWithWrongCourseId_badRequest() throws Exception {
        AnswerPost answerPostToUpdate = createAnswerPost(existingPostsWithAnswers.getFirst());
        Course dummyCourse = courseUtilService.createCourse();

        AnswerPost updatedAnswerPostServer = request.putWithResponseBody("/api/courses/" + dummyCourse.getId() + "/answer-posts/" + answerPostToUpdate.getId(), answerPostToUpdate,
                AnswerPost.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedAnswerPostServer).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testToggleResolvesPost() throws Exception {
        AnswerPost answerPost = existingAnswerPosts.getFirst();
        AnswerPost answerPost2 = existingAnswerPosts.get(1);

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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testToggleResolvesPost_asPostAuthor() throws Exception {
        // author of the associated original post is instructor1
        AnswerPost answerPost = existingAnswerPosts.getFirst();

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
        AnswerPost answerPost = existingAnswerPosts.getFirst();

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
        AnswerPost answerPostToDelete = existingAnswerPosts.getFirst();

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
        AnswerPost answerPostToNotDelete = existingAnswerPosts.getFirst();

        request.delete("/api/courses/" + courseId + "/answer-posts/" + answerPostToNotDelete.getId(), HttpStatus.FORBIDDEN);
        var newAnswerPostCount = answerPostRepository.count() - answerPostCount;
        assertThat(newAnswerPostCount).isZero();
        // should not decrement answerCount
        assertThat(postRepository.findPostByIdElseThrow(answerPostToNotDelete.getPost().getId()).getAnswerCount()).isEqualTo(answerPostToNotDelete.getPost().getAnswerCount());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeleteAnswerPost() throws Exception {
        var answerPostCount = answerPostRepository.count();
        // delete post from another student (index 0) --> ok
        AnswerPost answerPostToDelete = existingAnswerPosts.getFirst();

        request.delete("/api/courses/" + courseId + "/answer-posts/" + answerPostToDelete.getId(), HttpStatus.OK);
        var newAnswerPostCount = answerPostRepository.count() - answerPostCount;
        assertThat(newAnswerPostCount).isEqualTo(-1);
        // should decrement answerCount
        assertThat(postRepository.findPostByIdElseThrow(answerPostToDelete.getPost().getId()).getAnswerCount()).isEqualTo(answerPostToDelete.getPost().getAnswerCount() - 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeleteAnswerPost_asStudent_notFound() throws Exception {
        var countBefore = answerPostRepository.count();
        request.delete("/api/courses/" + courseId + "/answer-posts/" + 9999L, HttpStatus.NOT_FOUND);
        assertThat(answerPostRepository.count()).isEqualTo(countBefore);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeleteResolvingAnswerPost_asAuthor() throws Exception {
        AnswerPost answerPostToDeleteWhichResolves = existingAnswerPosts.getFirst();

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

    protected static List<Arguments> userMentionProvider() {
        return userMentionProvider(TEST_PREFIX + "student1", TEST_PREFIX + "student2");
    }
}
