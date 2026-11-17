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
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItem;

/**
 * Baseline policy that uniformly and randomly samples questions across available candidate items from the AI Quiz Question Pool.
 * Serves as the experimental control baseline in Iteration 1.
 */
@Component
@Conditional(AtlasEnabled.class)
@Lazy
public class UniformBaselinePolicy implements PracticeAllocationPolicy {

    @Override
    public List<QuizQuestionPoolItem> allocateQuestions(User user, Set<CourseCompetency> competencies, int count, List<QuizQuestionPoolItem> availableItems) {
        if (availableItems == null || availableItems.isEmpty() || count <= 0) {
            return List.of();
        }

        List<QuizQuestionPoolItem> shuffled = new ArrayList<>(availableItems);
        Collections.shuffle(shuffled);
        return shuffled.stream().limit(count).toList();
    }
}
