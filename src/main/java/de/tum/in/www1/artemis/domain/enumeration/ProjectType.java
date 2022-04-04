package de.tum.in.www1.artemis.domain.enumeration;

/**
 * The ProjectType enumeration.
 * Differentiates different templates for a programming language.
 */
public enum ProjectType {

    MAVEN_MAVEN, PLAIN_MAVEN, PLAIN, XCODE, FACT, GCC, PLAIN_GRADLE, GRADLE_GRADLE;

    public boolean isMaven() {
        return this == MAVEN_MAVEN || this == PLAIN_MAVEN;
    }

    public boolean isGradle() {
        return this == PLAIN_GRADLE || this == GRADLE_GRADLE;
    }
}
