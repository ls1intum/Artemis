package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/** Defines the generation configurations that the differential verifier supports. */
public final class LanguageGenerationProfile {

    private static final Set<ProgrammingLanguage> SUPPORTED_LANGUAGES = Set.of(ProgrammingLanguage.JAVA);

    private static final Set<ProjectType> SUPPORTED_JAVA_PROJECT_TYPES = Set.of(ProjectType.MAVEN_MAVEN, ProjectType.PLAIN_MAVEN, ProjectType.PLAIN_GRADLE,
            ProjectType.GRADLE_GRADLE);

    private LanguageGenerationProfile() {
    }

    /**
     * @return the languages with at least one supported generation configuration
     */
    public static Set<ProgrammingLanguage> supportedLanguages() {
        return SUPPORTED_LANGUAGES;
    }

    /**
     * Checks the complete exercise configuration because test execution depends on both language and project type.
     *
     * @param exercise the exercise to check, or {@code null}
     * @return whether the differential verifier supports this configuration
     */
    public static boolean isSupported(@Nullable ProgrammingExercise exercise) {
        if (exercise == null || exercise.getProgrammingLanguage() != ProgrammingLanguage.JAVA) {
            return false;
        }
        ProjectType projectType = exercise.getProjectType();
        return projectType == null || SUPPORTED_JAVA_PROJECT_TYPES.contains(projectType);
    }

    /**
     * @param exercise the exercise being generated
     * @return the production Java guidance, or an empty string for unsupported configurations
     */
    public static String guidanceFor(ProgrammingExercise exercise) {
        if (!isSupported(exercise)) {
            return "";
        }
        return """


                For this Maven/Gradle Java exercise the conventional Artemis layout is:
                - solution/src/<package path>/*
                - template/src/<package path>/* (identical signatures, placeholder bodies)
                - tests/test/<package path>/* (the test sources directory is `test`, NOT `src/test/java`)
                The directory below each source root MUST match the Java package declaration exactly. Never put a package-declared test directly in tests/test/ and never create
                tests/src/test/java/.
                The test project uses JUnit 5 and Ares (de.tum.in.ase:artemis-java-test-sandbox). Import de.tum.in.test.api.jupiter.Public,
                de.tum.in.test.api.WhitelistPath, de.tum.in.test.api.BlacklistPath, and de.tum.in.test.api.StrictTimeout. Every test class MUST carry @Public,
                @WhitelistPath("target"), and
                @BlacklistPath("target/test-classes"); every @Test MUST carry @StrictTimeout(1). Tests do not extend an Ares base class. Never create replacement framework
                classes. The [task] binding uses the test METHOD name exactly as the verifier reports it. Give tests descriptive method names and do NOT add @DisplayName because
                it can break the binding. Use plain JUnit assertions and do not modify tests/pom.xml, tests/build.gradle, or the test harness configuration.

                Sources may omit a class, method, or field from the template so Artemis generates structural tests. Behaviour tests must still compile against that incomplete template,
                so access omitted members through Ares ReflectionTestUtils. Prefer identical solution/template signatures and deliberately incomplete method bodies when structural testing
                does not serve the learning objective.
                """;
    }
}
