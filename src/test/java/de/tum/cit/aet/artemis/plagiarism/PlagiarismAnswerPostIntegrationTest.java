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

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.dto.AnswerPostResponseDTO;
import de.tum.cit.aet.artemis.communication.dto.PostResponseDTO;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismAnswerPostCreateRequestDTO;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismAnswerPostUpdateRequestDTO;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class PlagiarismAnswerPostIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "answerpostintegration";

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private PostTestRepository postRepository;

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
        List<Post> existingPostsAndConversationPostsWithAnswers = conversationUtilService.createPostsWithAnswerPostsWithinCourse(courseUtilService.createCourse(), TEST_PREFIX)
                .stream().filter(coursePost -> coursePost.getAnswers() != null && !coursePost.getAnswers().isEmpty()).toList();

        existingPostsWithAnswers = existingPostsAndConversationPostsWithAnswers.stream().filter(post -> post.getPlagiarismCase() != null).toList();

        // get all answerPosts
        existingAnswerPosts = existingPostsWithAnswers.stream().map(Post::getAnswers).flatMap(Collection::stream).toList();

        courseId = existingPostsWithAnswers.getFirst().getPlagiarismCase().getExercise().getCourseViaExerciseGroupOrCourseMember().getId();
    }

    @ParameterizedTest
    @MethodSource("userMentionProvider")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateAnswerPostWithUserMention(String userMention, boolean isUserMentionValid) throws Exception {
        Post parentPost = existingPostsWithAnswers.getFirst();
        PlagiarismAnswerPostCreateRequestDTO createRequest = new PlagiarismAnswerPostCreateRequestDTO(parentPost.getId(), userMention, false);

        if (!isUserMentionValid) {
            // Send the invalid-mention request to the plagiarism answer-post endpoint that the success branch
            // below targets — otherwise BAD_REQUEST could surface for an unrelated reason (e.g. content-type
            // mismatch on /api/communication/courses/{id}/messages) and a regression on plagiarism answer-post
            // validation would go undetected.
            request.postWithResponseBody("/api/plagiarism/courses/" + courseId + "/answer-posts", createRequest, AnswerPostResponseDTO.class, HttpStatus.BAD_REQUEST);
            return;
        }

        AnswerPostResponseDTO createdAnswerPost = request.postWithResponseBody("/api/plagiarism/courses/" + courseId + "/answer-posts", createRequest, AnswerPostResponseDTO.class,
                HttpStatus.CREATED);
        conversationUtilService.assertSensitiveInformationHidden(createdAnswerPost);
        // should not be automatically post resolving
        assertThat(createdAnswerPost.resolvesPost()).isFalse();
        assertThat(createdAnswerPost.id()).isNotNull();
        assertThat(createdAnswerPost.content()).isEqualTo(userMention);
        assertThat(createdAnswerPost.creationDate()).isNotNull();
        // @JsonInclude(NON_EMPTY) on AnswerPostResponseDTO strips empty Sets from the wire payload, so the
        // field comes back as null when there are no reactions yet.
        assertThat(createdAnswerPost.reactions()).isNullOrEmpty();
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

        Post parentPost = existingPostsWithAnswers.getFirst();
        PlagiarismAnswerPostCreateRequestDTO createRequest = new PlagiarismAnswerPostCreateRequestDTO(parentPost.getId(), "Content Answer Post", false);

        var answerPostCount = answerPostRepository.count();

        request.postWithResponseBody("/api/plagiarism/courses/" + courseId + "/answer-posts", createRequest, AnswerPostResponseDTO.class, HttpStatus.CREATED);

        var newAnswerPostCount = answerPostRepository.count() - answerPostCount;
        assertThat(newAnswerPostCount).isOne();

        // active messaging again
        persistedCourse.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        courseRepository.saveAndFlush(persistedCourse);
    }

    // Note: the previous testCreateExistingAnswerPost_badRequest case (creating with an existing id and expecting
    // BAD_REQUEST) is no longer expressible — the request DTO has no id field by construction, so the only way the
    // server could observe the case has been removed.

    // GET
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPlagiarismPostsForCourse() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("plagiarismCaseId", existingPostsWithAnswers.getFirst().getPlagiarismCase().getId().toString());

        List<PostResponseDTO> returnedPosts = request.getList("/api/plagiarism/courses/" + courseId + "/posts", HttpStatus.OK, PostResponseDTO.class, params);
        conversationUtilService.assertPostDtoSensitiveInformationHidden(returnedPosts);
        assertThat(returnedPosts).extracting(PostResponseDTO::id).containsExactlyInAnyOrderElementsOf(existingPostsWithAnswers.stream().map(Post::getId).toList());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetPlagiarismPostsForCourse_Forbidden() throws Exception {
        // authorIds containing own id && filterToUnresolved set true; will fetch all unresolved posts of current user
        var userId = userTestRepository.findOneByLogin(TEST_PREFIX + "student2").orElseThrow().getId();
        var params = new LinkedMultiValueMap<String, String>();
        params.add("plagiarismCaseId", existingPostsWithAnswers.getFirst().getPlagiarismCase().getId().toString());
        params.add("authorIds", userId.toString());

        List<PostResponseDTO> returnedPosts = request.getList("/api/plagiarism/courses/" + courseId + "/posts", HttpStatus.FORBIDDEN, PostResponseDTO.class, params);
        assertThat(returnedPosts).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPlagiarismPostsForCourse_BadRequest() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();

        List<PostResponseDTO> returnedPosts = request.getList("/api/plagiarism/courses/" + courseId + "/posts", HttpStatus.BAD_REQUEST, PostResponseDTO.class, params);
        assertThat(returnedPosts).isNull();
    }

    // UPDATE

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testEditAnswerPost_asStudent_Forbidden() throws Exception {
        // update post of student1 (index 0)--> FORBIDDEN
        AnswerPost answerPostToUpdate = existingAnswerPosts.getFirst();
        PlagiarismAnswerPostUpdateRequestDTO updateRequest = new PlagiarismAnswerPostUpdateRequestDTO("New Test Answer Post",
                Boolean.TRUE.equals(answerPostToUpdate.doesResolvePost()));

        AnswerPostResponseDTO updatedAnswerPost = request.putWithResponseBody("/api/plagiarism/courses/" + courseId + "/answer-posts/" + answerPostToUpdate.getId(), updateRequest,
                AnswerPostResponseDTO.class, HttpStatus.FORBIDDEN);
        assertThat(updatedAnswerPost).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testEditAnswerPost_asStudent1() throws Exception {
        // update own post (index 0)--> OK
        AnswerPost answerPostToUpdate = existingAnswerPosts.getFirst();
        PlagiarismAnswerPostUpdateRequestDTO updateRequest = new PlagiarismAnswerPostUpdateRequestDTO("New Test Answer Post",
                Boolean.TRUE.equals(answerPostToUpdate.doesResolvePost()));

        AnswerPostResponseDTO updatedAnswerPost = request.putWithResponseBody("/api/plagiarism/courses/" + courseId + "/answer-posts/" + answerPostToUpdate.getId(), updateRequest,
                AnswerPostResponseDTO.class, HttpStatus.OK);
        conversationUtilService.assertSensitiveInformationHidden(updatedAnswerPost);
        assertThat(updatedAnswerPost.id()).isEqualTo(answerPostToUpdate.getId());
        assertThat(updatedAnswerPost.content()).isEqualTo("New Test Answer Post");
    }

    @ParameterizedTest
    @MethodSource("userMentionProvider")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testEditAnswerPostWithUserMention(String userMention, boolean isUserMentionValid) throws Exception {
        // update own post (index 0)--> OK
        AnswerPost answerPostToUpdate = existingAnswerPosts.getFirst();
        PlagiarismAnswerPostUpdateRequestDTO updateRequest = new PlagiarismAnswerPostUpdateRequestDTO(userMention, Boolean.TRUE.equals(answerPostToUpdate.doesResolvePost()));

        if (!isUserMentionValid) {
            request.putWithResponseBody("/api/plagiarism/courses/" + courseId + "/answer-posts/" + answerPostToUpdate.getId(), updateRequest, AnswerPostResponseDTO.class,
                    HttpStatus.BAD_REQUEST);
            return;
        }

        AnswerPostResponseDTO updatedAnswerPost = request.putWithResponseBody("/api/plagiarism/courses/" + courseId + "/answer-posts/" + answerPostToUpdate.getId(), updateRequest,
                AnswerPostResponseDTO.class, HttpStatus.OK);
        conversationUtilService.assertSensitiveInformationHidden(updatedAnswerPost);
        assertThat(updatedAnswerPost.id()).isEqualTo(answerPostToUpdate.getId());
        assertThat(updatedAnswerPost.content()).isEqualTo(userMention);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testEditAnswerPost_asStudent2_forbidden() throws Exception {
        // update post from another student (index 1)--> forbidden
        AnswerPost answerPostNotToUpdate = existingAnswerPosts.getFirst();
        PlagiarismAnswerPostUpdateRequestDTO updateRequest = new PlagiarismAnswerPostUpdateRequestDTO("New Test Answer Post",
                Boolean.TRUE.equals(answerPostNotToUpdate.doesResolvePost()));

        AnswerPostResponseDTO notUpdatedAnswerPost = request.putWithResponseBody("/api/plagiarism/courses/" + courseId + "/answer-posts/" + answerPostNotToUpdate.getId(),
                updateRequest, AnswerPostResponseDTO.class, HttpStatus.FORBIDDEN);
        assertThat(notUpdatedAnswerPost).isNull();
    }

    // Note: the previous testEditAnswerPostWithIdIsNull_badRequest case is no longer expressible — the request DTO has
    // no id field, so the server's id-vs-path-id mismatch path can never be reached.

    // Note: testEditAnswerPostWithWrongCourseId_badRequest was removed.
    // Before this refactor, the test (despite its name) exercised the body-id-null check — it sent an AnswerPost
    // entity without id, and the service threw BadRequest on `answerPost.getId() == null`. The "wrong course id"
    // was incidental: the assertion fired before the courseId path variable was consulted.
    // With the new DTO-based request payload (no id field at all), the body-id case is unreachable.
    // The corresponding course-mismatch authorization concern is covered by testEditAnswerPost_asStudent_Forbidden,
    // which exercises the authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow path the controller still
    // uses when a user is not enrolled in the resolved course.

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testToggleResolvesPost() throws Exception {
        AnswerPost answerPost = existingAnswerPosts.getFirst();
        AnswerPost answerPost2 = existingAnswerPosts.get(1);

        // confirm that answer post resolves the original post
        AnswerPostResponseDTO resolvingAnswerPost = request.putWithResponseBody("/api/plagiarism/courses/" + courseId + "/answer-posts/" + answerPost.getId(),
                new PlagiarismAnswerPostUpdateRequestDTO(answerPost.getContent(), true), AnswerPostResponseDTO.class, HttpStatus.OK);
        assertThat(resolvingAnswerPost.id()).isEqualTo(answerPost.getId());
        assertThat(resolvingAnswerPost.resolvesPost()).isTrue();
        // confirm that the post is marked as resolved when it has a resolving answer
        assertThat(postRepository.findPostByIdElseThrow(answerPost.getPost().getId()).isResolved()).isTrue();

        request.putWithResponseBody("/api/plagiarism/courses/" + courseId + "/answer-posts/" + answerPost2.getId(),
                new PlagiarismAnswerPostUpdateRequestDTO(answerPost2.getContent(), true), AnswerPostResponseDTO.class, HttpStatus.OK);

        // revoke that answer post resolves the original post
        AnswerPostResponseDTO notResolvingAnswerPost = request.putWithResponseBody("/api/plagiarism/courses/" + courseId + "/answer-posts/" + answerPost.getId(),
                new PlagiarismAnswerPostUpdateRequestDTO(answerPost.getContent(), false), AnswerPostResponseDTO.class, HttpStatus.OK);
        assertThat(notResolvingAnswerPost.id()).isEqualTo(answerPost.getId());
        assertThat(notResolvingAnswerPost.resolvesPost()).isFalse();

        // confirm that the post is still marked as resolved since it still has a resolving answer
        assertThat(postRepository.findPostByIdElseThrow(answerPost.getPost().getId()).isResolved()).isTrue();

        // revoke that answer post2 resolves the original post
        request.putWithResponseBody("/api/plagiarism/courses/" + courseId + "/answer-posts/" + answerPost2.getId(),
                new PlagiarismAnswerPostUpdateRequestDTO(answerPost2.getContent(), false), AnswerPostResponseDTO.class, HttpStatus.OK);

        // confirm that the post is marked as unresolved when it no longer has a resolving answer
        assertThat(postRepository.findPostByIdElseThrow(answerPost.getPost().getId()).isResolved()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testToggleResolvesPost_asPostAuthor() throws Exception {
        // author of the associated original post is instructor1
        AnswerPost answerPost = existingAnswerPosts.getFirst();

        // confirm that answer post resolves the original post
        AnswerPostResponseDTO resolvingAnswerPost = request.putWithResponseBody("/api/plagiarism/courses/" + courseId + "/answer-posts/" + answerPost.getId(),
                new PlagiarismAnswerPostUpdateRequestDTO(answerPost.getContent(), true), AnswerPostResponseDTO.class, HttpStatus.OK);
        assertThat(resolvingAnswerPost.resolvesPost()).isTrue();

        // revoke that answer post resolves the original post
        AnswerPostResponseDTO notResolvingAnswerPost = request.putWithResponseBody("/api/plagiarism/courses/" + courseId + "/answer-posts/" + answerPost.getId(),
                new PlagiarismAnswerPostUpdateRequestDTO(answerPost.getContent(), false), AnswerPostResponseDTO.class, HttpStatus.OK);
        assertThat(notResolvingAnswerPost.resolvesPost()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testToggleResolvesPost_notAuthor_forbidden() throws Exception {
        // author of the associated original post is student1, author of answer post is also student1
        AnswerPost answerPost = existingAnswerPosts.getFirst();

        // confirm that answer post resolves the original post
        AnswerPostResponseDTO resolvingAnswerPost = request.putWithResponseBody("/api/plagiarism/courses/" + courseId + "/answer-posts/" + answerPost.getId(),
                new PlagiarismAnswerPostUpdateRequestDTO(answerPost.getContent(), true), AnswerPostResponseDTO.class, HttpStatus.FORBIDDEN);
        assertThat(resolvingAnswerPost).isNull();

        // revoke that answer post resolves the original post
        AnswerPostResponseDTO notResolvingAnswerPost = request.putWithResponseBody("/api/plagiarism/courses/" + courseId + "/answer-posts/" + answerPost.getId(),
                new PlagiarismAnswerPostUpdateRequestDTO(answerPost.getContent(), false), AnswerPostResponseDTO.class, HttpStatus.FORBIDDEN);
        assertThat(notResolvingAnswerPost).isNull();
    }

    // DELETE

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeleteAnswerPosts_asStudent1() throws Exception {
        var answerPostCount = answerPostRepository.count();
        // delete own post (index 0)--> OK
        AnswerPost answerPostToDelete = existingAnswerPosts.getFirst();

        request.delete("/api/plagiarism/courses/" + courseId + "/answer-posts/" + answerPostToDelete.getId(), HttpStatus.OK);
        var newAnswerPostCount = answerPostRepository.count() - answerPostCount;
        assertThat(newAnswerPostCount).isEqualTo(-1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testDeleteAnswerPosts_asStudent2_forbidden() throws Exception {
        var answerPostCount = answerPostRepository.count();
        // delete post from another student (index 0) --> forbidden
        AnswerPost answerPostToNotDelete = existingAnswerPosts.getFirst();

        request.delete("/api/plagiarism/courses/" + courseId + "/answer-posts/" + answerPostToNotDelete.getId(), HttpStatus.FORBIDDEN);
        var newAnswerPostCount = answerPostRepository.count() - answerPostCount;
        assertThat(newAnswerPostCount).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeleteAnswerPost() throws Exception {
        var answerPostCount = answerPostRepository.count();
        // delete post from another student (index 0) --> ok
        AnswerPost answerPostToDelete = existingAnswerPosts.getFirst();

        request.delete("/api/plagiarism/courses/" + courseId + "/answer-posts/" + answerPostToDelete.getId(), HttpStatus.OK);
        var newAnswerPostCount = answerPostRepository.count() - answerPostCount;
        assertThat(newAnswerPostCount).isEqualTo(-1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeleteAnswerPost_asStudent_notFound() throws Exception {
        var countBefore = answerPostRepository.count();
        request.delete("/api/plagiarism/courses/" + courseId + "/answer-posts/" + 9999L, HttpStatus.NOT_FOUND);
        assertThat(answerPostRepository.count()).isEqualTo(countBefore);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeleteResolvingAnswerPost_asAuthor() throws Exception {
        AnswerPost answerPostToDeleteWhichResolves = existingAnswerPosts.getFirst();

        var countBefore = answerPostRepository.count();
        request.delete("/api/plagiarism/courses/" + courseId + "/answer-posts/" + answerPostToDeleteWhichResolves.getId(), HttpStatus.OK);
        assertThat(answerPostRepository.count()).isEqualTo(countBefore - 1);

        Post persistedPost = postRepository.findPostByIdElseThrow(answerPostToDeleteWhichResolves.getPost().getId());

        // should update post resolved status to false
        assertThat(persistedPost.isResolved()).isFalse();
    }

    // HELPER METHODS

    protected static List<Arguments> userMentionProvider() {
        return userMentionProvider(TEST_PREFIX + "student1", TEST_PREFIX + "student2");
    }
}
