package de.tum.cit.aet.artemis.atlas.service.practice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;

/**
 * Baseline policy that uniformly and randomly samples questions across available candidate items.
 * Serves as the experimental control baseline in Iteration 1.
 */
@Component
@Conditional(AtlasEnabled.class)
@Lazy
public class UniformBaselinePolicy implements PracticeAllocationPolicy {

    @Override
    public List<QuizQuestion> allocateQuestions(User user, Set<CourseCompetency> competencies, int count, List<QuizQuestion> availableQuestions) {
        if (availableQuestions == null || availableQuestions.isEmpty() || count <= 0) {
            return List.of();
        }

        List<QuizQuestion> shuffled = new ArrayList<>(availableQuestions);
        Collections.shuffle(shuffled);
        return shuffled.stream().limit(count).toList();
    }
}
