package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic;

import java.util.List;

/**
 * Learner goals extracted from the instructor brief without candidate artifacts, retained across specification revisions.
 *
 * @param sourceBrief the exact brief to which the inventory belongs
 * @param goals       source-grounded goals with server-assigned, prompt-local identities
 */
public record BriefLearningGoals(String sourceBrief, List<Goal> goals) {

    public BriefLearningGoals {
        goals = List.copyOf(goals);
    }

    /**
     * One stated learner goal, with whitespace normalized for line-wrapped briefs.
     *
     * @param id    the server-assigned identity retained during specification repair
     * @param quote the instructor's words, rather than a candidate-derived interpretation
     */
    public record Goal(String id, String quote) {
    }
}
