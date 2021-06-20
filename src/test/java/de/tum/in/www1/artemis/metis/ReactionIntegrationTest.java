package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
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
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.Reaction;
import de.tum.in.www1.artemis.repository.metis.ReactionRepository;

public class ReactionIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ReactionRepository reactionRepository;

    private List<Post> existingPostsWithAnswers;

    private List<AnswerPost> existingAnswerPosts;

    private Long courseId;

    @BeforeEach
    public void initTestCase() {
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
    public void testCreateReactionPost() throws Exception {
        Post postReactedOn = existingPostsWithAnswers.get(0);
        Reaction reactionToSaveOnPost = createReactionOnPost(postReactedOn);

        Reaction createdReaction = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnPost, Reaction.class, HttpStatus.CREATED);
        checkCreatedReaction(reactionToSaveOnPost, createdReaction);
        assertThat(postReactedOn.getReactions().size() + 1).isEqualTo(reactionRepository.findReactionsByPost_Id(postReactedOn.getId()).size());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCreateReactionAnswerPost() throws Exception {
        AnswerPost answerPostReactedOn = existingAnswerPosts.get(0);
        Reaction reactionToSaveOnAnswerPost = createReactionOnAnswerPost(answerPostReactedOn);

        Reaction createdReaction = request.postWithResponseBody("/api/courses/" + courseId + "/postings/reactions", reactionToSaveOnAnswerPost, Reaction.class, HttpStatus.CREATED);
        checkCreatedReaction(reactionToSaveOnAnswerPost, createdReaction);
        assertThat(answerPostReactedOn.getReactions().size() + 1).isEqualTo(reactionRepository.findReactionsByAnswerPost_Id(answerPostReactedOn.getId()).size());
    }

    // HELPER METHODS

    private Reaction createReactionOnPost(Post postReactedOn) {
        Reaction reaction = new Reaction();
        reaction.setEmojiId("smiley");
        reaction.setCreationDate(ZonedDateTime.of(2015, 11, 30, 23, 45, 59, 1234, ZoneId.of("UTC")));
        reaction.setPost(postReactedOn);
        return reaction;
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
