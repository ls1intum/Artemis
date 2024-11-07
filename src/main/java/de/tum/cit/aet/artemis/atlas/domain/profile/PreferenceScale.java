package de.tum.cit.aet.artemis.atlas.domain.profile;

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
