package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/** Defines the generation configurations that the differential verifier supports. */
public final class LanguageGenerationProfile {

    private static final Set<ProgrammingLanguage> SUPPORTED_LANGUAGES = Set.of(ProgrammingLanguage.JAVA);

    private static final Set<ProjectType> SUPPORTED_JAVA_PROJECT_TYPES = Set.of(ProjectType.MAVEN_MAVEN, ProjectType.PLAIN_MAVEN, ProjectType.GRADLE_GRADLE,
            ProjectType.PLAIN_GRADLE);

    private LanguageGenerationProfile() {
    }

    public static Set<ProgrammingLanguage> supportedLanguages() {
        return SUPPORTED_LANGUAGES;
    }

    /**
     * Checks the exercise configuration because test execution depends on both language and project type. Touches only simple entity fields — NEVER the lazy
     * {@code auxiliaryRepositories} collection, which throws {@code LazyInitializationException} on a detached exercise; callers that must also reject auxiliary repositories
     * query them explicitly and pass the result to {@link #isSupported(ProgrammingExercise, boolean)}.
     *
     * @param exercise the exercise to check, or {@code null}
     * @return whether the differential verifier supports this language/project-type configuration
     */
    public static boolean isSupported(@Nullable ProgrammingExercise exercise) {
        if (exercise == null || exercise.getProgrammingLanguage() != ProgrammingLanguage.JAVA) {
            return false;
        }
        ProjectType projectType = exercise.getProjectType();
        return projectType == null || SUPPORTED_JAVA_PROJECT_TYPES.contains(projectType);
    }

    /**
     * The full support check: the language/project-type configuration must be supported AND the exercise must have no auxiliary repositories (the sandbox workspace and the
     * differential verifier only model solution/template/tests).
     *
     * @param exercise                 the exercise to check, or {@code null}
     * @param hasAuxiliaryRepositories whether the exercise has any auxiliary repositories, queried explicitly by the caller
     * @return whether generation supports this exercise
     */
    public static boolean isSupported(@Nullable ProgrammingExercise exercise, boolean hasAuxiliaryRepositories) {
        return isSupported(exercise) && !hasAuxiliaryRepositories;
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


                Java exercise layout (Maven or Gradle):
                - solution/src/<package path>/*
                - template/src/<package path>/* (given/stubbed files; student-creates absent)
                - tests/test/<package path>/* (the test sources directory is `test`, NOT `src/test/java`)
                The directory below each source root MUST match the Java package declaration exactly. Never put a package-declared test directly in tests/test/ and never create
                tests/src/test/java/.
                The test project uses JUnit 5 and Ares (de.tum.in.ase:artemis-java-test-sandbox). Import de.tum.in.test.api.jupiter.Public,
                de.tum.in.test.api.WhitelistPath, de.tum.in.test.api.BlacklistPath, and de.tum.in.test.api.StrictTimeout. Every test class MUST carry @Public,
                @WhitelistPath("target"), and
                @BlacklistPath("target/test-classes"); every @Test MUST carry @StrictTimeout(1). Never implement framework packages (`de.tum.in.test.api`, `org.junit`); dependencies
                provide them. The [task] binding uses the test METHOD name exactly as reported. Do NOT add @DisplayName because it can break binding. Use plain JUnit assertions and
                do not modify tests/pom.xml, tests/build.gradle, or the test harness.

                Sources may omit a class, method, or field from the template so Artemis generates structural tests. Behaviour tests must still compile against that incomplete template,
                so access omitted members through Ares ReflectionTestUtils. Prefer identical solution/template signatures and deliberately incomplete method bodies when structural testing
                does not serve the learning objective.
                """;
    }
}
