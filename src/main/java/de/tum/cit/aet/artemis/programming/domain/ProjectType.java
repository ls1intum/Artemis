package de.tum.cit.aet.artemis.programming.domain;

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
     * Returns if a project type is for a Maven project: legacy projects have no project type, but are Maven projects.
     *
     * @param projectType the project type that should be checked
     * @return whether the project is a Maven project
     */
    public static boolean isMavenProject(ProjectType projectType) {
        return projectType == null || projectType.isMaven();
    }
}
