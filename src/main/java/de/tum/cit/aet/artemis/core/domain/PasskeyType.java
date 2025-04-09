package de.tum.cit.aet.artemis.core.domain;

public enum PasskeyType {

    PUBLIC_KEY("public-key");

    private final String label;

    PasskeyType(String name) {
        this.label = name;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return this.name();
    }

    public static PasskeyType fromLabel(String label) {
        for (PasskeyType type : PasskeyType.values()) {
            if (type.label.equals(label)) {
                return type;
            }
        }
        throw new IllegalArgumentException("No PasskeyType found for label: " + label);
    }
}
