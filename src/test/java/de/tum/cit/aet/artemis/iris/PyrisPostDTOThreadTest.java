package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.UserRole;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisPostDTO;

/**
 * A thread must reach Pyris oldest-first, with every message tagged by its author's role.
 * <p>
 * Pyris answers the LAST entry of {@code answers}: Artemis re-runs the autonomous tutor on every new
 * message and sends the whole thread, so the newest message is the one that triggered the run. That
 * contract was silently broken because {@link Post#getAnswers()} is a {@code HashSet} and the DTOs
 * were collected into another one. {@link de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisAnswerPostDTO}
 * is a record, so its hash folds in the message content and the bucket order is effectively arbitrary.
 * When Iris's own earlier reply happened to land last, Pyris treated it as the message to answer,
 * correctly concluded that its own reply is not a question addressed to it, and skipped the student's
 * follow-up entirely — the thread just went quiet.
 */
class PyrisPostDTOThreadTest {

    private static final ZonedDateTime START = ZonedDateTime.parse("2026-08-29T06:59:00Z");

    private static User user(long id) {
        return user(id, false);
    }

    private static User user(long id, boolean bot) {
        var user = new User();
        user.setId(id);
        // User.isBot() is derived from the login, so that is what makes this the Iris bot.
        user.setLogin(bot ? User.IRIS_BOT_LOGIN : "student" + id);
        return user;
    }

    private static AnswerPost answer(long id, String content, long authorId, int minutesLater) {
        return answer(id, content, user(authorId), minutesLater);
    }

    private static AnswerPost answer(long id, String content, User author, int minutesLater) {
        var answerPost = new AnswerPost();
        answerPost.setId(id);
        answerPost.setContent(content);
        answerPost.setAuthor(author);
        answerPost.setCreationDate(START.plusMinutes(minutesLater));
        return answerPost;
    }

    private static Post threadOf(AnswerPost... answers) {
        var post = new Post();
        post.setId(66L);
        post.setContent("How does the devops cycle look like?");
        post.setAuthor(user(6L));
        // Deliberately a HashSet, as Hibernate hands it over: insertion order is not iteration order.
        post.setAnswers(new HashSet<>(Set.of(answers)));
        return post;
    }

    @Test
    void answersAreOrderedOldestFirstSoTheNewestMessageIsLast() {
        var irisReply = answer(58L, "The DevOps cycle typically consists of several key phases...", 4L, 1);
        var studentFollowUp = answer(59L, "What is the difference between continuous integration and delivery", 6L, 2);

        var dto = PyrisPostDTO.of(threadOf(irisReply, studentFollowUp), Map.of(), Map.of());

        assertThat(dto.answers()).extracting(answer -> answer.id()).containsExactly(58L, 59L);
    }

    @Test
    void orderDoesNotDependOnContentHashes() {
        // The exact regression: these two ids and contents land in HashSet buckets that put the bot's
        // reply last, which is what made Iris go quiet on this real thread.
        var irisReply = answer(56L, "DevOps encompasses a range of responsibilities that bridge software development and IT operations...", 4L, 1);
        var studentFollowUp = answer(57L, "And how does the devops cyvle look like", 6L, 2);

        var dto = PyrisPostDTO.of(threadOf(irisReply, studentFollowUp), Map.of(), Map.of());

        assertThat(dto.answers()).last().extracting(answer -> answer.id()).isEqualTo(57L);
    }

    @Test
    void theBotsOwnRepliesAreTaggedIris() {
        // Artemis stamps UserRole.USER on the bot's posts for the client, so the stored role cannot be
        // forwarded as-is: without the bot flag deciding this, Iris reads its own previous answer as just
        // another course member's message and cannot build on it.
        var irisReply = answer(58L, "The DevOps cycle typically consists of...", user(4L, true), 1);
        var studentFollowUp = answer(59L, "What is the difference between CI and CD", user(6L), 2);

        var dto = PyrisPostDTO.of(threadOf(irisReply, studentFollowUp), Map.of(), Map.of());

        assertThat(dto.answers()).extracting(answer -> answer.authorRole()).containsExactly("IRIS", "STUDENT");
    }

    @Test
    void staffRolesAreDistinguished() {
        var tutorAnswer = answer(60L, "Have a look at the slides.", user(7L), 1);
        var instructorAnswer = answer(61L, "To add to that...", user(8L), 2);

        var dto = PyrisPostDTO.of(threadOf(tutorAnswer, instructorAnswer), Map.of(7L, UserRole.TUTOR, 8L, UserRole.INSTRUCTOR), Map.of());

        assertThat(dto.answers()).extracting(answer -> answer.authorRole()).containsExactly("TUTOR", "INSTRUCTOR");
        assertThat(dto.authorRole()).isEqualTo("STUDENT");
    }

    @Test
    void idBreaksTiesWhenTwoMessagesShareATimestamp() {
        var first = answer(70L, "first", 6L, 5);
        var second = answer(71L, "second", 6L, 5);

        var dto = PyrisPostDTO.of(threadOf(second, first), Map.of(), Map.of());

        assertThat(dto.answers()).extracting(answer -> answer.id()).containsExactly(70L, 71L);
    }
}
