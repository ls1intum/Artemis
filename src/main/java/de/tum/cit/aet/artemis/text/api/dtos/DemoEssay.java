package de.tum.cit.aet.artemis.text.api.dtos;

import java.util.List;

/**
 * An essay a demo student submits to a text exercise of the demo course seeded by the {@code demo} profile, together with the feedback the demo tutor gives on it.
 *
 * @param text     the essay.
 * @param feedback the general feedback on the essay, whose credits add up to its score.
 */
public record DemoEssay(String text, List<GeneralFeedback> feedback) {

    /**
     * General feedback of an assessment, which refers to the essay as a whole rather than to a part of it.
     *
     * @param credits the points the feedback awards.
     * @param comment the comment that explains the points.
     */
    public record GeneralFeedback(double credits, String comment) {
    }
}
