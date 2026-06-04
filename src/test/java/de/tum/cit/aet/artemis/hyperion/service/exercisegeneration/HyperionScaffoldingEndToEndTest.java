package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * Verifies that the AI-generation scaffolding path — {@link ProgrammingExerciseCreationUpdateService#createProgrammingExercise(ProgrammingExercise, boolean)} with
 * {@code emptyRepositories=true} — works for EVERY Artemis-supported language and project type. It exercises the real production scaffold (template copy into the LocalVC
 * repositories followed by the source clearing), so a regression in the per-language template resolution or the source clearing fails here. It needs LocalVC and a Postgres
 * Testcontainer but NO LLM and NO build images, so it is fast; it is gated behind {@code HYPERION_SCAFFOLD_TEST} only to keep it out of the default fast suite.
 */
@EnabledIfEnvironmentVariable(named = "HYPERION_SCAFFOLD_TEST", matches = "true")
class HyperionScaffoldingEndToEndTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "hypscaf";

    @Autowired
    private ProgrammingExerciseCreationUpdateService creationService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private GitService gitService;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
    }

    /** Every Artemis-supported language × project type creatable in the LocalCI deployment (see LocalCIProgrammingLanguageFeatureService). */
    static Stream<Arguments> allSupportedConfigurations() {
        return Stream.of(config(ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN, "SCJPM"), config(ProgrammingLanguage.JAVA, ProjectType.PLAIN_GRADLE, "SCJPG"),
                config(ProgrammingLanguage.JAVA, ProjectType.MAVEN_MAVEN, "SCJMM"), config(ProgrammingLanguage.JAVA, ProjectType.GRADLE_GRADLE, "SCJGG"),
                config(ProgrammingLanguage.JAVA, ProjectType.MAVEN_BLACKBOX, "SCJBB"), config(ProgrammingLanguage.KOTLIN, null, "SCKT"),
                config(ProgrammingLanguage.PYTHON, null, "SCPY"), config(ProgrammingLanguage.C, ProjectType.GCC, "SCCGCC"),
                config(ProgrammingLanguage.C, ProjectType.FACT, "SCCFCT"), config(ProgrammingLanguage.C_PLUS_PLUS, null, "SCCPP"),
                config(ProgrammingLanguage.C_SHARP, null, "SCCS"), config(ProgrammingLanguage.GO, null, "SCGO"), config(ProgrammingLanguage.HASKELL, null, "SCHS"),
                config(ProgrammingLanguage.OCAML, null, "SCOCAML"), config(ProgrammingLanguage.JAVASCRIPT, null, "SCJS"), config(ProgrammingLanguage.TYPESCRIPT, null, "SCTS"),
                config(ProgrammingLanguage.RUBY, null, "SCRB"), config(ProgrammingLanguage.R, null, "SCR"), config(ProgrammingLanguage.RUST, null, "SCRS"),
                config(ProgrammingLanguage.SWIFT, ProjectType.PLAIN, "SCSW"), config(ProgrammingLanguage.DART, null, "SCDART"), config(ProgrammingLanguage.BASH, null, "SCBASH"),
                config(ProgrammingLanguage.ASSEMBLER, null, "SCASM"), config(ProgrammingLanguage.MATLAB, null, "SCMAT"), config(ProgrammingLanguage.VHDL, null, "SCVHDL"));
    }

    private static Arguments config(ProgrammingLanguage language, ProjectType projectType, String shortName) {
        return Arguments.of(language, projectType, shortName);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("allSupportedConfigurations")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void scaffoldsEmptyRepositoriesForEveryLanguageAndProjectType(ProgrammingLanguage language, ProjectType projectType, String shortName) throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        ProgrammingExercise exercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course, language);
        exercise.setProjectType(projectType);
        exercise.setShortName(shortName);
        exercise.setTitle("Scaffold " + shortName);
        exercise.setChannelName("scaf-" + shortName.toLowerCase());

        // The whole point: this must NOT throw for any configuration, and must produce an exercise with all three repositories wired up.
        ProgrammingExercise created = creationService.createProgrammingExercise(exercise, true);

        assertThat(created.getId()).as("%s %s exercise was persisted", language, projectType).isNotNull();
        assertThat(created.getTemplateRepositoryUri()).as("%s %s template repository", language, projectType).isNotBlank();
        assertThat(created.getSolutionRepositoryUri()).as("%s %s solution repository", language, projectType).isNotBlank();
        assertThat(created.getTestRepositoryUri()).as("%s %s test repository", language, projectType).isNotBlank();

        // Export the three cleared repositories to disk (build/hyperion-scaffold/<config>/) so the scaffold contents can be inspected and reviewed against the canonical template —
        // a passing creation call is necessary but not sufficient; the actual initialized files must be correct.
        exportScaffold(created, exportDirectoryName(language, projectType));
    }

    private static String exportDirectoryName(ProgrammingLanguage language, ProjectType projectType) {
        return language.name().toLowerCase() + (projectType == null ? "" : "_" + projectType.name().toLowerCase());
    }

    private void exportScaffold(ProgrammingExercise exercise, String configName) throws Exception {
        Path base = Path.of("build", "hyperion-scaffold", configName);
        deleteRecursively(base);
        for (RepositoryType repositoryType : List.of(RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS)) {
            Repository repository = gitService.getOrCheckoutRepository(exercise.getRepositoryURI(repositoryType), false, true);
            copyWorkingTree(repository.getLocalPath(), base.resolve(repositoryType.name().toLowerCase()));
        }
    }

    /** Copies a repository's working tree (excluding {@code .git}) to a target directory so the scaffolded files can be inspected. */
    private static void copyWorkingTree(Path source, Path target) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                Path relative = source.relativize(path);
                if (relative.toString().isEmpty() || relative.startsWith(".git")) {
                    continue;
                }
                Path destination = target.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                }
                else {
                    // FileUtils.copyFile creates the parent directories and overwrites an existing destination, preserving bytes — the mandated replacement for Files.copy.
                    FileUtils.copyFile(path.toFile(), destination.toFile());
                }
            }
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> entries = Files.walk(path)) {
            entries.sorted(Comparator.reverseOrder()).forEach(entry -> {
                try {
                    Files.delete(entry);
                }
                catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
