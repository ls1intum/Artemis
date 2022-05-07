package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.repository.metis.AnswerPostRepository;

// TODO improve tests
public class AnswerMessageIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private AnswerPostRepository answerPostRepository;

    private List<Post> existingPostsWithAnswers;

    private List<Post> existingConversationPostsWithAnswers;

    private List<Post> existingPostsWithAnswersInExercise;

    private List<Post> existingPostsWithAnswersCourseWide;

    private List<AnswerPost> existingAnswerPosts;

    private Long courseId;

    @BeforeEach
    public void initTestCase() {

        database.addUsers(5, 5, 0, 1);

        // initialize test setup and get all existing posts with answers (four posts, one in each context, are initialized with one answer each): 4 answers in total (with author
        // student1)
        List<Post> existingPostsAndConversationPostsWithAnswers = database.createPostsWithAnswerPostsWithinCourse().stream().filter(coursePost -> (coursePost.getAnswers() != null))
                .toList();

        existingPostsWithAnswers = existingPostsAndConversationPostsWithAnswers.stream().filter(post -> post.getConversation() == null).collect(Collectors.toList());

        existingConversationPostsWithAnswers = existingPostsAndConversationPostsWithAnswers.stream().filter(post -> post.getConversation() != null).collect(Collectors.toList());

        // get all answerPosts
        existingAnswerPosts = existingPostsAndConversationPostsWithAnswers.stream().map(Post::getAnswers).flatMap(Collection::stream).toList();

        // get all existing posts with answers in exercise context
        existingPostsWithAnswersInExercise = existingPostsWithAnswers.stream().filter(coursePost -> (coursePost.getAnswers() != null) && coursePost.getExercise() != null).toList();

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
    // @Test
    // @WithMockUser(username = "student3", roles = "USER")
    // public void testCreateConversationAnswerPost_forbidden() throws Exception {
    // // only participants of a conversation can create posts for it
    //
    // // attempt to save new answerPost under someone elses conversation
    // AnswerPost answerPostToSave = createAnswerPost(existingConversationPostsWithAnswers.get(0));
    // AnswerPost notCreatedAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-posts", answerPostToSave, AnswerPost.class, HttpStatus.FORBIDDEN);
    //
    // assertThat(notCreatedAnswerPost).isNull();
    // assertThat(answerPostRepository.count()).isEqualTo(existingAnswerPosts.size());
    // }

    // GET

    // UPDATE

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testEditAnswerPost_asTutor() throws Exception {
        // update post of student1 (index 0)--> OK
        AnswerPost answerPostToUpdate = editExistingAnswerPost(existingAnswerPosts.get(0));

        AnswerPost updatedAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPostToUpdate.getId(), answerPostToUpdate, AnswerPost.class,
                HttpStatus.OK);
        database.assertSensitiveInformationHidden(updatedAnswerPost);
        assertThat(answerPostToUpdate).isEqualTo(updatedAnswerPost);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testEditAnswerPost_asStudent1() throws Exception {
        // update own post (index 0)--> OK
        AnswerPost answerPostToUpdate = editExistingAnswerPost(existingAnswerPosts.get(0));

        AnswerPost updatedAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + answerPostToUpdate.getId(), answerPostToUpdate, AnswerPost.class,
                HttpStatus.OK);
        database.assertSensitiveInformationHidden(updatedAnswerPost);
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

    // @Test
    // @WithMockUser(username = "student1")
    // public void testEditConversationAnswerPost() throws Exception {
    // // conversation answerPost of student1 must be editable by them
    // AnswerPost conversationAnswerPostToUpdate = existingConversationPostsWithAnswers.get(0).getAnswers().iterator().next();
    // conversationAnswerPostToUpdate.setContent("User changes one of their conversation answerPosts");
    //
    // AnswerPost updatedAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + conversationAnswerPostToUpdate.getId(),
    // conversationAnswerPostToUpdate, AnswerPost.class, HttpStatus.OK);
    //
    // assertThat(conversationAnswerPostToUpdate).isEqualTo(updatedAnswerPost);
    // }

    // @Test
    // @WithMockUser(username = "tutor1", roles = "TA")
    // public void testEditConversationAnswerPost_forbidden() throws Exception {
    // // conversation answerPost of student1 must not be editable by tutors
    // AnswerPost conversationAnswerPostToUpdate = existingConversationPostsWithAnswers.get(0).getAnswers().iterator().next();
    // conversationAnswerPostToUpdate.setContent("Tutor attempts to change some other user's conversation answerPost");
    //
    // AnswerPost notUpdatedAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-posts/" + conversationAnswerPostToUpdate.getId(),
    // conversationAnswerPostToUpdate, AnswerPost.class, HttpStatus.FORBIDDEN);
    //
    // assertThat(notUpdatedAnswerPost).isNull();
    // }

    // @Test
    // @WithMockUser(username = "student1")
    // public void testDeleteConversationPost() throws Exception {
    // // conversation post of student1 must be deletable by them
    // AnswerPost conversationAnswerPostToDelete = existingConversationPostsWithAnswers.get(0).getAnswers().iterator().next();
    // request.delete("/api/courses/" + courseId + "/answer-posts/" + conversationAnswerPostToDelete.getId(), HttpStatus.OK);
    //
    // assertThat(answerPostRepository.count()).isEqualTo(existingAnswerPosts.size() - 1);
    // }

    // @Test
    // @WithMockUser(username = "tutor1", roles = "TA")
    // public void testDeleteConversationPost_forbidden() throws Exception {
    // // conversation post of student1 must not be deletable by tutors
    // AnswerPost conversationAnswerPostToDelete = existingConversationPostsWithAnswers.get(0).getAnswers().iterator().next();
    // request.delete("/api/courses/" + courseId + "/answer-posts/" + conversationAnswerPostToDelete.getId(), HttpStatus.FORBIDDEN);
    //
    // assertThat(answerPostRepository.count()).isEqualTo(existingAnswerPosts.size());
    // }

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

    private void checkCreatedAnserPost(AnswerPost expectedAnswerPost, AnswerPost createdAnswerPost) {
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
