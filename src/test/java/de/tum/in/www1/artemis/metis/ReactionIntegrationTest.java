package de.tum.in.www1.artemis.metis;

import static de.tum.in.www1.artemis.config.Constants.VOTE_EMOJI_ID;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Set;

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

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.PostSortCriterion;
import de.tum.in.www1.artemis.domain.metis.Reaction;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.repository.metis.ReactionRepository;

class ReactionIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ReactionRepository reactionRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    private List<Post> existingPostsWithAnswers;

    private List<Post> existingConversationPosts;

    private List<AnswerPost> existingAnswerPosts;

    private Long courseId;

    private Validator validator;

    private static final int MAX_POSTS_PER_PAGE = 20;

    @BeforeEach
    void initTestCase() {

        // used to test hibernate validation using custom ReactionConstraintValidator
        validator = Validation.buildDefaultValidatorFactory().getValidator();

        database.addUsers(5, 5, 4, 4);

        // initialize test setup and get all existing posts with answers (three posts, one in each context, are initialized with one answer each): 3 answers in total (with author
        // student1)
        existingPostsWithAnswers = database.createPostsWithAnswerPostsWithinCourse().stream()
                .filter(coursePost -> coursePost.getAnswers() != null && coursePost.getPlagiarismCase() == null).toList();

        // filters existing posts with conversation
        existingConversationPosts = existingPostsWithAnswers.stream().filter(post -> post.getConversation() != null).toList();

        // get all answerPosts
        existingAnswerPosts = existingPostsWithAnswers.stream().map(Post::getAnswers).flatMap(Collection::stream).toList();

        courseId = existingPostsWithAnswers.get(0).getExercise().getCourseViaExerciseGroupOrCourseMember().getId();
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    // CREATE

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreateOwnPostReaction() throws Exception {
        // student 1 is the author of the post and reacts on this post
        Post postReactedOn = existingPostsWithAnswers.get(0);
        Reaction reactionToSaveOnPost = createReactionOnPost(postReactedOn);

        Reaction createdReaction = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnPost, Reaction.class, HttpStatus.CREATED);
        checkCreatedReaction(reactionToSaveOnPost, createdReaction);
        assertThat(postReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByPostId(postReactedOn.getId()).size() - 1);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreateVoteReaction() throws Exception {
        // student 1 is the author of the post and reacts on this post
        Post postReactedOn = existingPostsWithAnswers.get(0);
        Reaction reactionToSaveOnPost = createVoteReactionOnPost(postReactedOn, null);

        Reaction createdReaction = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnPost, Reaction.class, HttpStatus.CREATED);
        checkCreatedReaction(reactionToSaveOnPost, createdReaction);
        assertThat(postReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByPostId(postReactedOn.getId()).size() - 1);
        // should increase post's vote count
        assertThat(database.postRepository.findPostByIdElseThrow(postReactedOn.getId()).getVoteCount()).isEqualTo(postReactedOn.getVoteCount() + 1);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "USER")
    void testCreateOwnPostReactionOnAnotherUsersConversationMessage() throws Exception {
        // tutor1 is the author of the message and tutor2 reacts on this post
        Post messageReactedOn = existingConversationPosts.get(0);
        Reaction reactionToSaveOnMessage = createReactionOnPost(messageReactedOn);

        Reaction createdReaction = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnMessage, Reaction.class, HttpStatus.CREATED);
        checkCreatedReaction(reactionToSaveOnMessage, createdReaction);
        assertThat(messageReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByPostId(messageReactedOn.getId()).size() - 1);
    }

    @Test
    @WithMockUser(username = "student3", roles = "USER")
    void testCreateReactionOnConversationBetweenOtherUsers_forbidden() throws Exception {
        // student 1 is the author of the message between student1 & student2 and student3 not part of the conversation tries to react on it
        Post messageReactedOn = existingConversationPosts.get(0);
        Reaction reactionToSaveOnMessage = createReactionOnPost(messageReactedOn);

        Reaction notCreatedReaction = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnMessage, Reaction.class,
                HttpStatus.FORBIDDEN);
        assertThat(notCreatedReaction).isNull();
        assertThat(messageReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByPostId(messageReactedOn.getId()).size());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreateMultipleOwnPostReaction_internalServerError() throws Exception {
        // student 1 is the author of the post and reacts on this post
        Post postReactedOn = existingPostsWithAnswers.get(0);
        Reaction reactionToSaveOnPost = createReactionOnPost(postReactedOn);

        Reaction createdReaction = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnPost, Reaction.class, HttpStatus.CREATED);
        checkCreatedReaction(reactionToSaveOnPost, createdReaction);
        assertThat(postReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByPostId(postReactedOn.getId()).size() - 1);

        // try again
        request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnPost, Reaction.class, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreateOwnAnswerPostReaction() throws Exception {
        // student 1 is the author of the answer post and reacts on this answer post
        AnswerPost answerPostReactedOn = existingAnswerPosts.get(0);
        Reaction reactionToSaveOnAnswerPost = createReactionOnAnswerPost(answerPostReactedOn);

        Reaction createdReaction = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnAnswerPost, Reaction.class, HttpStatus.CREATED);
        checkCreatedReaction(reactionToSaveOnAnswerPost, createdReaction);
        assertThat(answerPostReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByAnswerPostId(answerPostReactedOn.getId()).size() - 1);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreateMultipleOwnAnswerPostReaction_internalServerError() throws Exception {
        // student 1 is the author of the answer post and reacts on this answer post
        AnswerPost answerPostReactedOn = existingAnswerPosts.get(0);
        Reaction reactionToSaveOnAnswerPost = createReactionOnAnswerPost(answerPostReactedOn);

        Reaction createdReaction = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnAnswerPost, Reaction.class, HttpStatus.CREATED);
        checkCreatedReaction(reactionToSaveOnAnswerPost, createdReaction);
        assertThat(answerPostReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByAnswerPostId(answerPostReactedOn.getId()).size() - 1);

        // try again
        request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnAnswerPost, Reaction.class, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = "student2", roles = "USER")
    void testCreatePostReactions() throws Exception {
        // student 1 is the author of the post and student 2 reacts on this post
        AnswerPost answerPostReactedOn = existingAnswerPosts.get(0);
        Reaction reactionToSaveOnAnswerPost = createReactionOnAnswerPost(answerPostReactedOn);

        Reaction createdFirstReaction = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnAnswerPost, Reaction.class,
                HttpStatus.CREATED);
        checkCreatedReaction(reactionToSaveOnAnswerPost, createdFirstReaction);
        assertThat(answerPostReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByAnswerPostId(answerPostReactedOn.getId()).size() - 1);

        // student 2 reacts again on this answer post
        reactionToSaveOnAnswerPost = createReactionOnAnswerPost(answerPostReactedOn);
        // change the emojiId to react differently
        reactionToSaveOnAnswerPost.setEmojiId("cry");

        Reaction createdSecondReaction = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnAnswerPost, Reaction.class,
                HttpStatus.CREATED);
        checkCreatedReaction(reactionToSaveOnAnswerPost, createdSecondReaction);
        assertThat(answerPostReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByAnswerPostId(answerPostReactedOn.getId()).size() - 2);
    }

    @Test
    @WithMockUser(username = "student2", roles = "USER")
    void testCreateAnswerPostReactions() throws Exception {
        // student 1 is the author of the answer post and student 2 reacts on this answer post
        AnswerPost answerPostReactedOn = existingAnswerPosts.get(0);
        Reaction reactionToSaveOnAnswerPost = createReactionOnAnswerPost(answerPostReactedOn);

        Reaction createdFirstReaction = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnAnswerPost, Reaction.class,
                HttpStatus.CREATED);
        checkCreatedReaction(reactionToSaveOnAnswerPost, createdFirstReaction);
        assertThat(answerPostReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByAnswerPostId(answerPostReactedOn.getId()).size() - 1);

        // student 2 reacts again on this answer post
        reactionToSaveOnAnswerPost = createReactionOnAnswerPost(answerPostReactedOn);
        // change the emojiId to react differently
        reactionToSaveOnAnswerPost.setEmojiId("cry");

        Reaction createdSecondReaction = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnAnswerPost, Reaction.class,
                HttpStatus.CREATED);
        checkCreatedReaction(reactionToSaveOnAnswerPost, createdSecondReaction);
        assertThat(answerPostReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByAnswerPostId(answerPostReactedOn.getId()).size() - 2);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreateExistingReaction_badRequest() throws Exception {
        // student 1 is the author of the answer post and reacts on this answer post
        AnswerPost answerPostReactedOn = existingAnswerPosts.get(0);
        Reaction reactionToSaveOnAnswerPost = createReactionOnAnswerPost(answerPostReactedOn);

        Reaction createdReaction = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnAnswerPost, Reaction.class, HttpStatus.CREATED);
        request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", createdReaction, Reaction.class, HttpStatus.BAD_REQUEST);
        checkCreatedReaction(reactionToSaveOnAnswerPost, createdReaction);
        assertThat(answerPostReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByAnswerPostId(answerPostReactedOn.getId()).size() - 1);
    }

    @Test
    @WithMockUser(username = "student2", roles = "USER")
    void testValidateReactionConstraintViolation() throws Exception {
        Reaction invalidReaction = createInvalidReaction();

        request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", invalidReaction, Reaction.class, HttpStatus.BAD_REQUEST);
        Set<ConstraintViolation<Reaction>> constraintViolations = validator.validate(invalidReaction);
        assertThat(constraintViolations).hasSize(1);
    }

    // GET

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetPostsForCourse_OrderByVoteCountDESC() throws Exception {
        PostSortCriterion sortCriterion = PostSortCriterion.VOTES;
        SortingOrder sortingOrder = SortingOrder.DESCENDING;

        User student1 = database.getUserByLogin("student1");
        User student2 = database.getUserByLogin("student2");

        // student 1 is the author of the post and reacts on this post
        Post postReactedOn = existingPostsWithAnswers.get(0);
        createVoteReactionOnPost(postReactedOn, student1);

        Post postReactedOn2 = existingPostsWithAnswers.get(1);
        createVoteReactionOnPost(postReactedOn2, student1);
        createVoteReactionOnPost(postReactedOn2, student2);

        var params = new LinkedMultiValueMap<String, String>();

        // ordering only available in course discussions page, where paging is enabled
        params.add("pagingEnabled", "true");
        params.add("page", "0");
        params.add("size", String.valueOf(MAX_POSTS_PER_PAGE));

        params.add("postSortCriterion", sortCriterion.toString());
        params.add("sortingOrder", sortingOrder.toString());

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);

        Long numberOfMaxVotesSeenOnAnyPost = Long.MAX_VALUE;
        for (Post post : returnedPosts) {
            Long numberOfVotes = post.getReactions().stream().filter(reaction -> reaction.getEmojiId().equals(VOTE_EMOJI_ID)).count();
            assertThat(numberOfVotes).isLessThanOrEqualTo(numberOfMaxVotesSeenOnAnyPost);
            numberOfMaxVotesSeenOnAnyPost = numberOfVotes;
        }
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetPostsForCourse_OrderByVoteCountASC() throws Exception {
        PostSortCriterion sortCriterion = PostSortCriterion.VOTES;
        SortingOrder sortingOrder = SortingOrder.ASCENDING;

        User student1 = database.getUserByLogin("student1");
        User student2 = database.getUserByLogin("student2");

        Post postReactedOn = existingPostsWithAnswers.get(0);
        createVoteReactionOnPost(postReactedOn, student1);
        createVoteReactionOnPost(postReactedOn, student2);

        Post post2ReactedOn = existingPostsWithAnswers.get(1);
        createVoteReactionOnPost(post2ReactedOn, student2);

        var params = new LinkedMultiValueMap<String, String>();

        // ordering only available in course discussions page, where paging is enabled
        params.add("pagingEnabled", "true");
        params.add("page", "0");
        params.add("size", String.valueOf(MAX_POSTS_PER_PAGE));

        params.add("postSortCriterion", sortCriterion.toString());
        params.add("sortingOrder", sortingOrder.toString());

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);

        Long numberOfMaxVotesSeenOnAnyPost = 0L;
        for (int i = 0; i < returnedPosts.size(); i++) {
            Long numberOfVotes = returnedPosts.get(i).getReactions().stream().filter(reaction -> reaction.getEmojiId().equals(VOTE_EMOJI_ID)).count();
            assertThat(numberOfVotes).isGreaterThanOrEqualTo(numberOfMaxVotesSeenOnAnyPost);
            numberOfMaxVotesSeenOnAnyPost = numberOfVotes;
        }
    }

    // DELETE

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testDeleteOwnPostReaction() throws Exception {
        // student 1 is the author of the post and reacts on this post
        Post postReactedOn = existingPostsWithAnswers.get(0);
        Reaction reactionToSaveOnPost = createReactionOnPost(postReactedOn);

        Reaction reactionToBeDeleted = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnPost, Reaction.class, HttpStatus.CREATED);

        // student 1 deletes their reaction on this post
        request.delete("/api/courses/" + courseId + "/postings/reactions/" + reactionToBeDeleted.getId(), HttpStatus.OK);

        assertThat(postReactedOn.getReactions()).hasSameSizeAs(reactionRepository.findReactionsByPostId(postReactedOn.getId()));
        assertThat(reactionRepository.findById(reactionToBeDeleted.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testDeleteOwnVoteReaction() throws Exception {
        // student 1 is the author of the post and reacts on this post
        Post postReactedOn = existingPostsWithAnswers.get(0);
        Reaction reactionToSaveOnPost = createVoteReactionOnPost(postReactedOn, null);

        Reaction reactionToBeDeleted = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnPost, Reaction.class, HttpStatus.CREATED);
        // should increase post's vote count
        assertThat(database.postRepository.findPostByIdElseThrow(postReactedOn.getId()).getVoteCount()).isEqualTo(postReactedOn.getVoteCount() + 1);

        // student 1 deletes their reaction on this post
        request.delete("/api/courses/" + courseId + "/postings/reactions/" + reactionToBeDeleted.getId(), HttpStatus.OK);

        assertThat(postReactedOn.getReactions()).hasSameSizeAs(reactionRepository.findReactionsByPostId(postReactedOn.getId()));
        assertThat(reactionRepository.findById(reactionToBeDeleted.getId())).isEmpty();
        // should decrease post's vote count
        assertThat(database.postRepository.findPostByIdElseThrow(postReactedOn.getId()).getVoteCount()).isEqualTo(postReactedOn.getVoteCount());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testDeleteOwnAnswerPostReaction() throws Exception {
        // student 1 is the author of the post and reacts on this post
        AnswerPost answerPostReactedOn = existingAnswerPosts.get(0);
        Reaction reactionToSaveOnPost = createReactionOnAnswerPost(answerPostReactedOn);

        Reaction reactionToBeDeleted = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnPost, Reaction.class, HttpStatus.CREATED);

        // student 1 deletes their reaction on this post
        request.delete("/api/courses/" + courseId + "/postings/reactions/" + reactionToBeDeleted.getId(), HttpStatus.OK);
        assertThat(answerPostReactedOn.getReactions()).hasSameSizeAs(reactionRepository.findReactionsByPostId(answerPostReactedOn.getId()));
        assertThat(reactionRepository.findById(reactionToBeDeleted.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testDeletePostReactionOfOthers_forbidden() throws Exception {
        // student 1 is the author of the post and student 2 reacts on this post
        Post postReactedOn = existingPostsWithAnswers.get(0);
        Reaction reactionSaveOnPost = saveReactionOfOtherUserOnPost(postReactedOn);

        // student 1 wants to delete the reaction of student 2
        request.delete("/api/courses/" + courseId + "/postings/reactions/" + reactionSaveOnPost.getId(), HttpStatus.FORBIDDEN);
        assertThat(postReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByPostId(postReactedOn.getId()).size() - 1);
    }

    @Test
    @WithMockUser(username = "student2", roles = "USER")
    void testDeletePostReactionWithWrongCourseId_badRequest() throws Exception {
        Course dummyCourse = database.createCourse();
        Post postToReactOn = existingPostsWithAnswers.get(0);
        Reaction reactionToSaveOnPost = createReactionOnPost(postToReactOn);

        request.delete("/api/courses/" + dummyCourse.getCourseIcon() + "/postings/reactions/" + reactionToSaveOnPost.getId(), HttpStatus.BAD_REQUEST);
        assertThat(postToReactOn.getReactions()).hasSameSizeAs(postRepository.findById(postToReactOn.getId()).get().getReactions());
    }

    @Test
    @WithMockUser(username = "student2", roles = "USER")
    void testDeletePostReaction() throws Exception {
        // student 1 is the author of the post and student 2 reacts on this post
        Post postReactedOn = existingPostsWithAnswers.get(0);
        Reaction reactionToSaveOnPost = createReactionOnPost(postReactedOn);

        Reaction reactionToBeDeleted = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnPost, Reaction.class, HttpStatus.CREATED);

        // student 2 deletes their reaction on this post
        request.delete("/api/courses/" + courseId + "/postings/reactions/" + reactionToBeDeleted.getId(), HttpStatus.OK);
        assertThat(postReactedOn.getReactions()).hasSameSizeAs(reactionRepository.findReactionsByPostId(postReactedOn.getId()));
        assertThat(reactionRepository.findById(reactionToBeDeleted.getId())).isEmpty();
    }

    // HELPER METHODS

    private Reaction createReactionOnPost(Post postReactedOn) {
        Reaction reaction = new Reaction();
        reaction.setEmojiId("smiley");
        reaction.setPost(postReactedOn);
        return reaction;
    }

    private Reaction createVoteReactionOnPost(Post postReactedOn, User user) {
        Reaction reaction = new Reaction();
        reaction.setUser(user);
        reaction.setEmojiId(VOTE_EMOJI_ID);
        reaction.setPost(postReactedOn);
        return reaction;
    }

    private Reaction createInvalidReaction() {
        Reaction reaction = new Reaction();
        reaction.setEmojiId("smiley");
        reaction.setPost(existingPostsWithAnswers.get(0));
        reaction.setAnswerPost(existingAnswerPosts.get(0));
        return reaction;
    }

    private Reaction saveReactionOfOtherUserOnPost(Post postReactedOn) {
        Reaction reaction = new Reaction();
        reaction.setEmojiId("smiley");
        reaction.setPost(postReactedOn);
        Reaction savedReaction = reactionRepository.save(reaction);
        User user = userRepository.getUserWithGroupsAndAuthorities("student2");
        savedReaction.setUser(user);
        reactionRepository.save(savedReaction);
        return savedReaction;
    }

    private Reaction createReactionOnAnswerPost(AnswerPost answerPostReactedOn) {
        Reaction reaction = new Reaction();
        reaction.setEmojiId("smiley");
        reaction.setAnswerPost(answerPostReactedOn);
        return reaction;
    }

    private void checkCreatedReaction(Reaction expectedReaction, Reaction createdReaction) {
        // check if post was created with id
        assertThat(createdReaction).isNotNull();
        assertThat(createdReaction.getId()).isNotNull();

        // check if emojiId and creation data are set correctly on creation
        assertThat(createdReaction.getEmojiId()).isEqualTo(expectedReaction.getEmojiId());
        assertThat(createdReaction.getCreationDate()).isNotNull();

        // check if association to post or answer post is correct
        assertThat(createdReaction.getPost()).isEqualTo(expectedReaction.getPost());
        assertThat(createdReaction.getAnswerPost()).isEqualTo(expectedReaction.getAnswerPost());

        database.assertSensitiveInformationHidden(createdReaction);
    }
}
