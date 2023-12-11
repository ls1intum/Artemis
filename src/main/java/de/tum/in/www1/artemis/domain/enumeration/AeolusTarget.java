package de.tum.in.www1.artemis.domain.enumeration;

public enum AeolusTarget {

    BAMBOO("bamboo"), JENKINS("jenkins");

    private final String name;

    AeolusTarget(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
