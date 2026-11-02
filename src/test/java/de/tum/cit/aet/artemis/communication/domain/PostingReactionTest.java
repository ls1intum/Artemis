package de.tum.cit.aet.artemis.communication.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PostingReactionTest {

    @ParameterizedTest
    @MethodSource("postings")
    void mutatesTheConcretePostingReactionCollection(Posting posting) {
        var reactions = posting.getReactions();
        var reaction = new Reaction();
        posting.addReaction(reaction);
        assertThat(posting.getReactions()).isSameAs(reactions).containsExactly(reaction);
        posting.removeReaction(reaction);
        assertThat(reactions).isEmpty();
    }

    private static Stream<Posting> postings() {
        return Stream.of(new Post(), new AnswerPost());
    }
}
