package de.tum.cit.aet.artemis.atlas.service.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionDifficulty;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItem;

class UniformBaselinePolicyTest {

    private UniformBaselinePolicy policy;

    private User student;

    private List<QuizQuestionPoolItem> items;

    @BeforeEach
    void setUp() {
        policy = new UniformBaselinePolicy();
        student = new User();
        student.setId(1L);

        items = new ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            QuizQuestionPoolItem item = new QuizQuestionPoolItem();
            item.setId(i);
            item.setCompetencyId(100L);
            item.setDifficulty(QuizQuestionDifficulty.MEDIUM);
            items.add(item);
        }
    }

    @Test
    void testAllocateQuestionsReturnsRequestedCount() {
        int count = 5;
        List<QuizQuestionPoolItem> allocated = policy.allocateQuestions(student, Set.of(), count, items);

        assertThat(allocated).hasSize(count);
        assertThat(items).containsAll(allocated);
        assertThat(allocated).doesNotHaveDuplicates();
    }

    @Test
    void testAllocateQuestionsWhenCountExceedsAvailable() {
        int count = 15;
        List<QuizQuestionPoolItem> allocated = policy.allocateQuestions(student, Set.of(), count, items);

        assertThat(allocated).hasSize(10);
        assertThat(allocated).containsExactlyInAnyOrderElementsOf(items);
    }

    @Test
    void testAllocateQuestionsWithEmptyPool() {
        List<QuizQuestionPoolItem> allocated = policy.allocateQuestions(student, Set.of(), 5, List.of());
        assertThat(allocated).isEmpty();
    }

    @Test
    void testAllocateQuestionsWithZeroOrNegativeCount() {
        assertThat(policy.allocateQuestions(student, Set.of(), 0, items)).isEmpty();
        assertThat(policy.allocateQuestions(student, Set.of(), -2, items)).isEmpty();
    }
}
