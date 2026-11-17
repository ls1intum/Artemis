package de.tum.cit.aet.artemis.atlas.service.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;

class UniformBaselinePolicyTest {

    private UniformBaselinePolicy policy;

    private User student;

    private List<QuizQuestion> questions;

    @BeforeEach
    void setUp() {
        policy = new UniformBaselinePolicy();
        student = new User();
        student.setId(1L);

        questions = new ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            MultipleChoiceQuestion question = new MultipleChoiceQuestion();
            question.setId(i);
            question.setTitle("Question " + i);
            questions.add(question);
        }
    }

    @Test
    void testAllocateQuestionsReturnsRequestedCount() {
        int count = 5;
        List<QuizQuestion> allocated = policy.allocateQuestions(student, Set.of(), count, questions);

        assertThat(allocated).hasSize(count);
        assertThat(questions).containsAll(allocated);
        // Ensure no duplicate questions were selected
        assertThat(allocated).doesNotHaveDuplicates();
    }

    @Test
    void testAllocateQuestionsWhenCountExceedsAvailable() {
        int count = 15;
        List<QuizQuestion> allocated = policy.allocateQuestions(student, Set.of(), count, questions);

        assertThat(allocated).hasSize(10);
        assertThat(allocated).containsExactlyInAnyOrderElementsOf(questions);
    }

    @Test
    void testAllocateQuestionsWithEmptyPool() {
        List<QuizQuestion> allocated = policy.allocateQuestions(student, Set.of(), 5, List.of());
        assertThat(allocated).isEmpty();
    }

    @Test
    void testAllocateQuestionsWithZeroOrNegativeCount() {
        assertThat(policy.allocateQuestions(student, Set.of(), 0, questions)).isEmpty();
        assertThat(policy.allocateQuestions(student, Set.of(), -2, questions)).isEmpty();
    }
}
