package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
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
 * Verifies the AI-generation scaffolding path — {@link ProgrammingExerciseCreationUpdateService#createProgrammingExercise(ProgrammingExercise, boolean)} with
 * {@code emptyRepositories=true} — for every Artemis-supported language and project type: the production scaffold (template copy + source clearing) must wire up all three
 * repositories and, per the documented contract, clear the template/solution sources while keeping the tests repository intact. Needs only LocalVC + Postgres (no LLM, no build
 * images), so it runs in the normal deterministic suite.
 */
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

        // Must NOT throw for any configuration, and must wire up all three repositories.
        ProgrammingExercise created = creationService.createProgrammingExercise(exercise, true);

        assertThat(created.getId()).as("%s %s exercise was persisted", language, projectType).isNotNull();
        assertThat(created.getTemplateRepositoryUri()).as("%s %s template repository", language, projectType).isNotBlank();
        assertThat(created.getSolutionRepositoryUri()).as("%s %s solution repository", language, projectType).isNotBlank();
        assertThat(created.getTestRepositoryUri()).as("%s %s test repository", language, projectType).isNotBlank();

        List<Path> templateFiles = workingTreeFiles(created, RepositoryType.TEMPLATE);
        List<Path> solutionFiles = workingTreeFiles(created, RepositoryType.SOLUTION);
        List<Path> testFiles = workingTreeFiles(created, RepositoryType.TESTS);

        // All three working trees are non-empty (clearing leaves the build scaffolding behind; the tests repo is copied verbatim).
        assertThat(templateFiles).as("%s %s template working tree is non-empty", language, projectType).isNotEmpty();
        assertThat(solutionFiles).as("%s %s solution working tree is non-empty", language, projectType).isNotEmpty();
        assertThat(testFiles).as("%s %s tests working tree is non-empty", language, projectType).isNotEmpty();

        // The emptyRepositories=true contract (ProgrammingExerciseRepositoryService#clearRepositoriesForAiGeneration): the language's source files are cleared from template and
        // solution by extension (build manifests and the keep-list preserved), while the tests repository is kept intact. We replicate production's exact source filter and assert
        // no
        // clearable source survives in template or solution; the tests tree, copied verbatim and never cleared, stays non-empty (asserted above) — the asymmetry that the clearing
        // was scoped to the code repos.
        java.util.Set<String> sourceExtensions = sourceExtensionsFor(language);
        assertThat(templateFiles).as("%s %s template sources were cleared", language, projectType).noneMatch(path -> isClearableSource(path, sourceExtensions));
        assertThat(solutionFiles).as("%s %s solution sources were cleared", language, projectType).noneMatch(path -> isClearableSource(path, sourceExtensions));
    }

    private List<Path> workingTreeFiles(ProgrammingExercise exercise, RepositoryType repositoryType) throws Exception {
        Repository repository = gitService.getOrCheckoutRepository(exercise.getRepositoryURI(repositoryType), false, true);
        Path root = repository.getLocalPath();
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).filter(path -> !root.relativize(path).startsWith(".git")).map(root::relativize).toList();
        }
    }

    // The keep-list ProgrammingExerciseRepositoryService preserves even though its name carries a source extension (Package.swift / build.sbt).
    private static final java.util.Set<String> AI_GENERATION_KEEP_FILES = java.util.Set.of("Package.swift", "build.sbt");

    private static boolean isClearableSource(Path path, java.util.Set<String> sourceExtensions) {
        String fileName = path.getFileName().toString();
        if (AI_GENERATION_KEEP_FILES.contains(fileName)) {
            return false;
        }
        String lower = fileName.toLowerCase(java.util.Locale.ROOT);
        return sourceExtensions.stream().anyMatch(lower::endsWith);
    }

    /** Mirrors {@code ProgrammingExerciseRepositoryService#sourceExtensionsFor}: the lower-cased extensions that identify the language's clearable source files. */
    private static java.util.Set<String> sourceExtensionsFor(ProgrammingLanguage language) {
        return switch (language) {
            case JAVA -> java.util.Set.of(".java");
            case KOTLIN -> java.util.Set.of(".kt");
            case PYTHON -> java.util.Set.of(".py");
            case C -> java.util.Set.of(".c", ".h");
            case C_PLUS_PLUS -> java.util.Set.of(".cpp", ".cc", ".cxx", ".hpp", ".hh", ".h");
            case C_SHARP -> java.util.Set.of(".cs");
            case GO -> java.util.Set.of(".go");
            case RUST -> java.util.Set.of(".rs");
            case SWIFT -> java.util.Set.of(".swift");
            case HASKELL -> java.util.Set.of(".hs");
            case OCAML -> java.util.Set.of(".ml", ".mli");
            case JAVASCRIPT -> java.util.Set.of(".js");
            case TYPESCRIPT -> java.util.Set.of(".ts");
            case RUBY -> java.util.Set.of(".rb");
            case R -> java.util.Set.of(".r");
            case DART -> java.util.Set.of(".dart");
            case VHDL -> java.util.Set.of(".vhd", ".vhdl");
            case ASSEMBLER -> java.util.Set.of(".asm", ".s");
            case BASH -> java.util.Set.of(".bash", ".sh");
            case MATLAB -> java.util.Set.of(".m");
            default -> java.util.Set.of();
        };
    }
}
