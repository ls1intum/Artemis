package de.tum.in.www1.artemis.domain.enumeration;

import java.util.Locale;

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

    /**
     * Tries to parse the given project type string.
     * <p>
     * Ignores the casing of the input string.
     * <p>
     * In addition to the representation of values as obtained by calling
     * {@link ProjectType#toString()}, a few shorthand aliases can be parsed:
     * <ul>
     *     <li>{@code default} for {@link ProjectType#PLAIN}</li>
     *     <li>{@code maven} for {@link ProjectType#MAVEN_MAVEN}</li>
     *     <li>{@code gradle} for {@link ProjectType#GRADLE_GRADLE}</li>
     * </ul>
     *
     * @param projectType The project type string that should be parsed.
     * @throws IllegalArgumentException Thrown if the input cannot be parsed as a project type.
     * @return The project type represented by the given input.
     */
    public static ProjectType tryFromString(final String projectType) throws IllegalArgumentException {
        final String input = projectType.toUpperCase(Locale.ROOT);

        try {
            return ProjectType.valueOf(input);
        }
        catch (IllegalArgumentException ex) {
            return switch (input) {
                case "DEFAULT" -> PLAIN;
                case "MAVEN" -> MAVEN_MAVEN;
                case "GRADLE" -> GRADLE_GRADLE;
                default -> throw new IllegalArgumentException("Unknown project type: " + projectType);
            };
        }
    }
}
