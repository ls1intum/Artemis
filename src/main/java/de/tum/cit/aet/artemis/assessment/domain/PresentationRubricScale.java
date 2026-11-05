package de.tum.cit.aet.artemis.assessment.domain;

import java.util.List;

/**
 * The rating scale of a rubric criterion. Its levels are valued 1 to {@link #getLevelCount()}.
 * The English level keys name the levels in the personal data export; the client translates its own.
 */
public enum PresentationRubricScale {

    THREE_POINT(List.of("Poor", "Satisfactory", "Excellent")), FIVE_POINT(List.of("Poor", "Needs Improvement", "Satisfactory", "Good", "Excellent"));

    private final List<String> levelKeys;

    PresentationRubricScale(List<String> levelKeys) {
        this.levelKeys = levelKeys;
    }

    public int getLevelCount() {
        return levelKeys.size();
    }

    /**
     * @param position the position of a level, starting at 0 for the lowest
     * @return the English key of the level at that position
     */
    public String getLevelKey(int position) {
        return levelKeys.get(position);
    }
}
