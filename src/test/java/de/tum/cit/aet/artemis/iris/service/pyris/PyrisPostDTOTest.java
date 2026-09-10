package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisPostDTO;

/**
 * Redaction depends on each answer author's LLM decision. The decisions live in {@code user_ai_preference} and are passed
 * in as one map, loaded in a single query, because reading them here would issue a query per answer post on an Iris
 * request path.
 * <p>
 * These tests pin the mapping from that map to redaction, including the cases the map cannot answer: an author with no
 * recorded decision, and an answer with no author at all.
 */
class PyrisPostDTOTest {

    private static Post postWithAnswers(AnswerPost... answers) {
        Post post = new Post();
        post.setId(1L);
        post.setContent("post content");
        User postAuthor = new User();
        postAuthor.setId(100L);
        post.setAuthor(postAuthor);
        post.setAnswers(new HashSet<>(Set.of(answers)));
        return post;
    }

    private static AnswerPost answerBy(long answerId, Long authorId, String content) {
        AnswerPost answer = new AnswerPost();
        answer.setId(answerId);
        answer.setContent(content);
        if (authorId != null) {
            User author = new User();
            author.setId(authorId);
            answer.setAuthor(author);
        }
        return answer;
    }

    @Test
    void redactsOnlyTheAnswersWhoseAuthorOptedOut() {
        Post post = postWithAnswers(answerBy(11L, 1L, "opted out"), answerBy(12L, 2L, "opted in"));
        Map<Long, AiSelectionDecision> decisions = Map.of(1L, AiSelectionDecision.NO_AI, 2L, AiSelectionDecision.CLOUD_AI);

        var dto = new PyrisPostDTO(post, decisions);

        assertThat(dto.answers()).filteredOn(answer -> answer.id() == 11L).singleElement().satisfies(answer -> assertThat(answer.redacted()).isTrue())
                .satisfies(answer -> assertThat(answer.content()).isNull());
        assertThat(dto.answers()).filteredOn(answer -> answer.id() == 12L).singleElement().satisfies(answer -> assertThat(answer.redacted()).isFalse())
                .satisfies(answer -> assertThat(answer.content()).isEqualTo("opted in"));
    }

    /**
     * An author with no row is absent from the map. Absent must not read as NO_AI, otherwise every account that never
     * made a decision - the large majority - would have its answers redacted.
     */
    @Test
    void doesNotRedactAnAuthorMissingFromTheMap() {
        Post post = postWithAnswers(answerBy(11L, 1L, "no decision recorded"));

        var dto = new PyrisPostDTO(post, Map.of());

        assertThat(dto.answers()).singleElement().satisfies(answer -> {
            assertThat(answer.redacted()).isFalse();
            assertThat(answer.content()).isEqualTo("no decision recorded");
        });
    }

    @Test
    void doesNotRedactAnAnswerWithoutAnAuthor() {
        Post post = postWithAnswers(answerBy(11L, null, "authorless"));

        var dto = new PyrisPostDTO(post, Map.of(1L, AiSelectionDecision.NO_AI));

        assertThat(dto.answers()).singleElement().satisfies(answer -> {
            assertThat(answer.redacted()).isFalse();
            assertThat(answer.userID()).isNull();
        });
    }

    @Test
    void carriesThePostContentAndAuthorThrough() {
        Post post = postWithAnswers(answerBy(11L, 1L, "answer"));

        var dto = new PyrisPostDTO(post, Map.of());

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.content()).isEqualTo("post content");
        assertThat(dto.userID()).isEqualTo(100L);
    }

    @Test
    void handlesAPostWithoutAnswers() {
        var dto = new PyrisPostDTO(postWithAnswers(), Map.of());

        assertThat(dto.answers()).isEmpty();
    }
}
