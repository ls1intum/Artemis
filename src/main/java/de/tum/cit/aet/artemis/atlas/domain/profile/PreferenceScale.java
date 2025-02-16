package de.tum.cit.aet.artemis.atlas.domain.profile;

/**
 * Enum for the preferences as lickert-scale regarding settings in the (course) learner profile, see {@link CourseLearnerProfile} and {@link LearnerProfile}.
 */
public enum PreferenceScale {

    LOW(1), MEDIUM_LOW(2), MEDIUM(3), MEDIUM_HIGH(4), HIGH(5);

    private final int value;

    PreferenceScale(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
