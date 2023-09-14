package de.tum.in.www1.artemis.domain.enumeration;

import java.util.Locale;

/**
 * The ProjectType enumeration.
 * Differentiates different templates for a programming language.
 */
public enum ProjectType {

    MAVEN_MAVEN, PLAIN_MAVEN, PLAIN, XCODE, FACT, GCC, PLAIN_GRADLE, GRADLE_GRADLE, MAVEN_BLACKBOX;

    public boolean isMaven() {
        return this == MAVEN_MAVEN || this == PLAIN_MAVEN || this == MAVEN_BLACKBOX;
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
     * <li>{@code default} for {@link ProjectType#PLAIN}</li>
     * <li>{@code maven} for {@link ProjectType#MAVEN_MAVEN}</li>
     * <li>{@code gradle} for {@link ProjectType#GRADLE_GRADLE}</li>
     * </ul>
     *
     * @param projectType The project type string that should be parsed.
     * @return The project type represented by the given input.
     * @throws IllegalArgumentException Thrown if the input cannot be parsed as a project type.
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

    /**
     * Returns if a project type is for a Maven project: legacy projects have no project type, but are Maven projects.
     *
     * @param projectType the project type that should be checked
     * @return whether the project is a Maven project
     */
    public static boolean isMavenProject(ProjectType projectType) {
        return projectType == null || projectType.isMaven();
    }
}
