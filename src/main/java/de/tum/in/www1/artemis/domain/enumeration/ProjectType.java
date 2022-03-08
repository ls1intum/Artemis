package de.tum.in.www1.artemis.domain.enumeration;

/**
 * The ProjectType enumeration.
 * Differentiates different templates for a programming language.
 */
public enum ProjectType {

    MAVEN, ECLIPSE, PLAIN, XCODE, FACT, GCC, PLAIN_GRADLE, GRADLE_GRADLE;

    public boolean isMaven() {
        return this == MAVEN || this == ECLIPSE;
    }

    public boolean isGradle() {
        return this == PLAIN_GRADLE || this == GRADLE_GRADLE;
    }
}
