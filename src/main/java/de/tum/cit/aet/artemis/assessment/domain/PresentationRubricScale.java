package de.tum.cit.aet.artemis.assessment.domain;

/**
 * The rating scale of a rubric criterion. Its levels are valued 1 to {@link #getLevelCount()}.
 */
public enum PresentationRubricScale {

    THREE_POINT(3), FIVE_POINT(5);

    private final int levelCount;

    PresentationRubricScale(int levelCount) {
        this.levelCount = levelCount;
    }

    public int getLevelCount() {
        return levelCount;
    }
}
