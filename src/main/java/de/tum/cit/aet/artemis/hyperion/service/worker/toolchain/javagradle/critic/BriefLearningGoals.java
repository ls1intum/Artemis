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

    /** An explicit starting-state requirement, extracted before the proposed design is available. */
    public enum ConstructorBaseline {
        UNSPECIFIED, NO_EXPLICIT_CONSTRUCTORS
    }

    /**
     * One stated learner goal, with whitespace normalized for line-wrapped briefs.
     *
     * @param id                  the server-assigned identity retained during specification repair
     * @param quote               the instructor's words, rather than a candidate-derived interpretation
     * @param constructorBaseline whether the brief requires an existing class without explicit constructors
     */
    public record Goal(String id, String quote, ConstructorBaseline constructorBaseline) {

        /**
         * @param id    the retained goal identity
         * @param quote the source-grounded goal without an additional constructor requirement
         */
        public Goal(String id, String quote) {
            this(id, quote, ConstructorBaseline.UNSPECIFIED);
        }
    }
}
