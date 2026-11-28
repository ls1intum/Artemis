package de.tum.cit.aet.artemis.sandbox.domain;

/** The build tool of the exercise-tests repository, which selects the Ares language configuration of a generated policy. */
public enum BuildTool {

    GRADLE("JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ"), MAVEN("JAVA_USING_MAVEN_ARCHUNIT_AND_ASPECTJ");

    private final String aresLanguageConfiguration;

    BuildTool(String aresLanguageConfiguration) {
        this.aresLanguageConfiguration = aresLanguageConfiguration;
    }

    /**
     * @return the language configuration that a generated policy names for this build tool
     */
    public String aresLanguageConfiguration() {
        return aresLanguageConfiguration;
    }
}
