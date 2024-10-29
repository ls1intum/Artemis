package de.tum.cit.aet.artemis.programming.domain;

/**
 * Targets Aeolus is able to create build plans for.
 */
public enum AeolusTarget {

    JENKINS("jenkins"), CLI("cli");

    private final String name;

    AeolusTarget(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
