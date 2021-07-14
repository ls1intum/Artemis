package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
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

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.Reaction;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.repository.metis.ReactionRepository;

public class ReactionIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ReactionRepository reactionRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    private List<Post> existingPostsWithAnswers;

    private List<AnswerPost> existingAnswerPosts;

    private Long courseId;

    private Validator validator;

    @BeforeEach
    public void initTestCase() {

        // used to test hibernate validation using custom ReactionConstraintValidator
        validator = Validation.buildDefaultValidatorFactory().getValidator();

        database.addUsers(5, 5, 0, 1);

        // initialize test setup and get all existing posts with answers (three posts, one in each context, are initialized with one answer each): 3 answers in total (with author
        // student1)
        existingPostsWithAnswers = database.createPostsWithAnswerPostsWithinCourse().stream().filter(coursePost -> (coursePost.getAnswers() != null)).collect(Collectors.toList());

        // get all answerPosts
        existingAnswerPosts = existingPostsWithAnswers.stream().map(Post::getAnswers).flatMap(Collection::stream).collect(Collectors.toList());

        courseId = existingPostsWithAnswers.get(0).getCourse().getId();
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    // CREATE

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCreateOwnPostReaction() throws Exception {
        // student 1 is the author of the post and reacts on this post
        Post postReactedOn = existingPostsWithAnswers.get(0);
        Reaction reactionToSaveOnPost = createReactionOnPost(postReactedOn);

        Reaction createdReaction = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnPost, Reaction.class, HttpStatus.CREATED);
        checkCreatedReaction(reactionToSaveOnPost, createdReaction);
        assertThat(postReactedOn.getReactions().size() + 1).isEqualTo(reactionRepository.findReactionsByPostId(postReactedOn.getId()).size());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCreateOwnAnswerPostReaction() throws Exception {
        // student 1 is the author of the answer post and reacts on this answer post
        AnswerPost answerPostReactedOn = existingAnswerPosts.get(0);
        Reaction reactionToSaveOnAnswerPost = createReactionOnAnswerPost(answerPostReactedOn);

        Reaction createdReaction = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnAnswerPost, Reaction.class, HttpStatus.CREATED);
        checkCreatedReaction(reactionToSaveOnAnswerPost, createdReaction);
        assertThat(answerPostReactedOn.getReactions().size() + 1).isEqualTo(reactionRepository.findReactionsByAnswerPostId(answerPostReactedOn.getId()).size());
    }

    @Test
    @WithMockUser(username = "student2", roles = "USER")
    public void testCreatePostReactions() throws Exception {
        // student 1 is the author of the post and student 2 reacts on this post
        AnswerPost answerPostReactedOn = existingAnswerPosts.get(0);
        Reaction reactionToSaveOnAnswerPost = createReactionOnAnswerPost(answerPostReactedOn);

        Reaction createdFirstReaction = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnAnswerPost, Reaction.class,
                HttpStatus.CREATED);
        checkCreatedReaction(reactionToSaveOnAnswerPost, createdFirstReaction);
        assertThat(answerPostReactedOn.getReactions().size() + 1).isEqualTo(reactionRepository.findReactionsByAnswerPostId(answerPostReactedOn.getId()).size());

        // student 2 reacts again on this answer post
        reactionToSaveOnAnswerPost = createReactionOnAnswerPost(answerPostReactedOn);
        // change the emojiId to react differently
        reactionToSaveOnAnswerPost.setEmojiId("cry");

        Reaction createdSecondReaction = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnAnswerPost, Reaction.class,
                HttpStatus.CREATED);
        checkCreatedReaction(reactionToSaveOnAnswerPost, createdSecondReaction);
        assertThat(answerPostReactedOn.getReactions().size() + 2).isEqualTo(reactionRepository.findReactionsByAnswerPostId(answerPostReactedOn.getId()).size());
    }

    @Test
    @WithMockUser(username = "student2", roles = "USER")
    public void testCreateAnswerPostReactions() throws Exception {
        // student 1 is the author of the answer post and student 2 reacts on this answer post
        AnswerPost answerPostReactedOn = existingAnswerPosts.get(0);
        Reaction reactionToSaveOnAnswerPost = createReactionOnAnswerPost(answerPostReactedOn);

        Reaction createdFirstReaction = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnAnswerPost, Reaction.class,
                HttpStatus.CREATED);
        checkCreatedReaction(reactionToSaveOnAnswerPost, createdFirstReaction);
        assertThat(answerPostReactedOn.getReactions().size() + 1).isEqualTo(reactionRepository.findReactionsByAnswerPostId(answerPostReactedOn.getId()).size());

        // student 2 reacts again on this answer post
        reactionToSaveOnAnswerPost = createReactionOnAnswerPost(answerPostReactedOn);
        // change the emojiId to react differently
        reactionToSaveOnAnswerPost.setEmojiId("cry");

        Reaction createdSecondReaction = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnAnswerPost, Reaction.class,
                HttpStatus.CREATED);
        checkCreatedReaction(reactionToSaveOnAnswerPost, createdSecondReaction);
        assertThat(answerPostReactedOn.getReactions().size() + 2).isEqualTo(reactionRepository.findReactionsByAnswerPostId(answerPostReactedOn.getId()).size());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCreatePostWithWrongCourseIdReaction_badRequest() throws Exception {
        Course dummyCourse = database.createCourse();
        Post postToReactOn = existingPostsWithAnswers.get(0);
        Reaction reactionToSaveOnPost = createReactionOnPost(postToReactOn);

        Reaction createdReaction = request.postWithResponseBody("/api/courses/" + dummyCourse.getId() + "/postings/reactions", reactionToSaveOnPost, Reaction.class,
                HttpStatus.BAD_REQUEST);
        assertThat(createdReaction).isNull();
        assertThat(postToReactOn.getReactions().size()).isEqualTo(postRepository.findById(postToReactOn.getId()).get().getReactions().size());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCreateExistingReaction_badRequest() throws Exception {
        // student 1 is the author of the answer post and reacts on this answer post
        AnswerPost answerPostReactedOn = existingAnswerPosts.get(0);
        Reaction reactionToSaveOnAnswerPost = createReactionOnAnswerPost(answerPostReactedOn);

        Reaction createdReaction = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnAnswerPost, Reaction.class, HttpStatus.CREATED);
        request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", createdReaction, Reaction.class, HttpStatus.BAD_REQUEST);
        checkCreatedReaction(reactionToSaveOnAnswerPost, createdReaction);
        assertThat(answerPostReactedOn.getReactions().size() + 1).isEqualTo(reactionRepository.findReactionsByAnswerPostId(answerPostReactedOn.getId()).size());
    }

    @Test
    @WithMockUser(username = "student2", roles = "USER")
    public void testValidateReactionConstraintViolation() throws Exception {
        Reaction invalidReaction = createInvalidReaction();
        request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", invalidReaction, Reaction.class, HttpStatus.BAD_REQUEST);
        Set<ConstraintViolation<Reaction>> constraintViolations = validator.validate(invalidReaction);
        assertThat(constraintViolations.size()).isEqualTo(1);
    }

    // DELETE

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testDeleteOwnPostReaction() throws Exception {
        // student 1 is the author of the post and reacts on this post
        Post postReactedOn = existingPostsWithAnswers.get(0);
        Reaction reactionToSaveOnPost = createReactionOnPost(postReactedOn);

        Reaction reactionToBeDeleted = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnPost, Reaction.class, HttpStatus.CREATED);

        // student 1 deletes their reaction on this post
        request.delete("/api/courses/" + courseId + "/postings/reactions/" + reactionToBeDeleted.getId(), HttpStatus.OK);

        assertThat(postReactedOn.getReactions().size()).isEqualTo(reactionRepository.findReactionsByPostId(postReactedOn.getId()).size());
        assertThat(reactionRepository.findById(reactionToBeDeleted.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testDeleteOwnAnswerPostReaction() throws Exception {
        // student 1 is the author of the post and reacts on this post
        AnswerPost answerPostReactedOn = existingAnswerPosts.get(0);
        Reaction reactionToSaveOnPost = createReactionOnAnswerPost(answerPostReactedOn);

        Reaction reactionToBeDeleted = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnPost, Reaction.class, HttpStatus.CREATED);

        // student 1 deletes their reaction on this post
        request.delete("/api/courses/" + courseId + "/postings/reactions/" + reactionToBeDeleted.getId(), HttpStatus.OK);

        assertThat(answerPostReactedOn.getReactions().size()).isEqualTo(reactionRepository.findReactionsByPostId(answerPostReactedOn.getId()).size());
        assertThat(reactionRepository.findById(reactionToBeDeleted.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testDeletePostReactionOfOthers_forbidden() throws Exception {
        // student 1 is the author of the post and student 2 reacts on this post
        Post postReactedOn = existingPostsWithAnswers.get(0);
        Reaction reactionSaveOnPost = saveReactionOfOtherUserOnPost(postReactedOn, "student2");

        // student 1 wants to delete the reaction of student 2
        request.delete("/api/courses/" + courseId + "/postings/reactions/" + reactionSaveOnPost.getId(), HttpStatus.FORBIDDEN);

        assertThat(postReactedOn.getReactions().size() + 1).isEqualTo(reactionRepository.findReactionsByPostId(postReactedOn.getId()).size());
    }

    @Test
    @WithMockUser(username = "student2", roles = "USER")
    public void testDeletePostReactionWithWrongCourseId_badRequest() throws Exception {
        Course dummyCourse = database.createCourse();
        Post postToReactOn = existingPostsWithAnswers.get(0);
        Reaction reactionToSaveOnPost = createReactionOnPost(postToReactOn);

        request.delete("/api/courses/" + dummyCourse.getCourseIcon() + "/postings/reactions/" + reactionToSaveOnPost.getId(), HttpStatus.BAD_REQUEST);
        assertThat(postToReactOn.getReactions().size()).isEqualTo(postRepository.findById(postToReactOn.getId()).get().getReactions().size());
    }

    @Test
    @WithMockUser(username = "student2", roles = "USER")
    public void testDeletePostReaction() throws Exception {
        // student 1 is the author of the post and student 2 reacts on this post
        Post postReactedOn = existingPostsWithAnswers.get(0);
        Reaction reactionToSaveOnPost = createReactionOnPost(postReactedOn);

        Reaction reactionToBeDeleted = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnPost, Reaction.class, HttpStatus.CREATED);

        // student 2 deletes their reaction on this post
        request.delete("/api/courses/" + courseId + "/postings/reactions/" + reactionToBeDeleted.getId(), HttpStatus.OK);

        assertThat(postReactedOn.getReactions().size()).isEqualTo(reactionRepository.findReactionsByPostId(postReactedOn.getId()).size());
        assertThat(reactionRepository.findById(reactionToBeDeleted.getId())).isEmpty();
    }

    // HELPER METHODS

    private Reaction createReactionOnPost(Post postReactedOn) {
        Reaction reaction = new Reaction();
        reaction.setEmojiId("smiley");
        reaction.setCreationDate(ZonedDateTime.of(2015, 11, 30, 23, 45, 59, 1234, ZoneId.of("UTC")));
        reaction.setPost(postReactedOn);
        return reaction;
    }

    private Reaction createInvalidReaction() {
        Reaction reaction = new Reaction();
        reaction.setEmojiId("smiley");
        reaction.setCreationDate(ZonedDateTime.of(2015, 11, 30, 23, 45, 59, 1234, ZoneId.of("UTC")));
        reaction.setPost(existingPostsWithAnswers.get(0));
        reaction.setAnswerPost(existingAnswerPosts.get(0));
        return reaction;
    }

    private Reaction saveReactionOfOtherUserOnPost(Post postReactedOn, String username) {
        Reaction reaction = new Reaction();
        reaction.setEmojiId("smiley");
        reaction.setCreationDate(ZonedDateTime.of(2015, 11, 30, 23, 45, 59, 1234, ZoneId.of("UTC")));
        reaction.setPost(postReactedOn);
        Reaction savedReaction = reactionRepository.save(reaction);
        User user = this.userRepository.getUserWithGroupsAndAuthorities(username);
        savedReaction.setUser(user);
        reactionRepository.save(savedReaction);
        return savedReaction;
    }

    private Reaction createReactionOnAnswerPost(AnswerPost answerPostReactedOn) {
        Reaction reaction = new Reaction();
        reaction.setEmojiId("smiley");
        reaction.setCreationDate(ZonedDateTime.of(2015, 11, 30, 23, 45, 59, 1234, ZoneId.of("UTC")));
        reaction.setAnswerPost(answerPostReactedOn);
        return reaction;
    }

    private void checkCreatedReaction(Reaction expectedReaction, Reaction createdReaction) {
        // check if post was created with id
        assertThat(createdReaction).isNotNull();
        assertThat(createdReaction.getId()).isNotNull();

        // check if emojiId and creation data are set correctly on creation
        assertThat(createdReaction.getEmojiId()).isEqualTo(expectedReaction.getEmojiId());
        assertThat(createdReaction.getCreationDate()).isEqualTo(expectedReaction.getCreationDate());

        // check if association to post or answer post is correct
        assertThat(createdReaction.getPost()).isEqualTo(expectedReaction.getPost());
        assertThat(createdReaction.getAnswerPost()).isEqualTo(expectedReaction.getAnswerPost());
    }
}
