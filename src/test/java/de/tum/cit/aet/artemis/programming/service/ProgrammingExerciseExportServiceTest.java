package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.SET_UP_TEMPLATE_FOR_EXERCISE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Constants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.core.dto.RepositoryExportOptionsDTO;
import de.tum.cit.aet.artemis.core.service.ArchivalReportEntry;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.localci.service.LocalVCLocalCITestService;
import de.tum.cit.aet.artemis.localvc.service.GitRepositoryExportService.RepositoryExportContent;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.dto.ImportProgrammingExerciseRequestDTO;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.TemplateProgrammingExerciseParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;
import de.tum.cit.aet.artemis.programming.util.ZipTestUtil;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * Tests the export of programming exercise repositories, in particular the path taken by course and exam archiving:
 * with none of the rewriting export options set, repositories are streamed from their bare repositories and never
 * checked out.
 */
class ProgrammingExerciseExportServiceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "progexexportservice";

    /** Fixed instead of relative to now, so a failure reproduces with the same dates. */
    private static final ZonedDateTime DEADLINE = ZonedDateTime.parse("2200-01-10T12:00:00Z");

    /** The options course and exam archiving use: export everyone, change nothing. */
    private static final RepositoryExportOptionsDTO ARCHIVAL_OPTIONS = new RepositoryExportOptionsDTO(true, false, false, null, false, false, false, false, false);

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private LocalVCLocalCITestService localVCLocalCITestService;

    @Autowired
    private ProgrammingExerciseStudentParticipationTestRepository studentParticipationTestRepository;

    @Autowired
    private ProgrammingExerciseExportService programmingExerciseExportService;

    @Autowired
    private TemplateProgrammingExerciseParticipationTestRepository templateParticipationTestRepository;

    @Autowired
    private TempFileUtilService tempFileUtilService;

    @Autowired
    private GradingCriterionRepository gradingCriterionRepository;

    @Autowired
    private AuxiliaryRepositoryService auxiliaryRepositoryService;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 1);
        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
    }

    @AfterEach
    void tearDown() {
        RepositoryExportTestUtil.cleanupTrackedRepositories();
    }

    /**
     * Two participations exported concurrently used to be handed the same millisecond-named temporary directory, and each
     * of them scheduled its own recursive deletion of it (issue #13575). Streaming from the bare repository removes the
     * temporary directory altogether, so there is nothing left to collide over or to clean up twice.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportStudentRepositories_shouldStreamEachRepositoryWithoutACheckout() throws Exception {
        List<ProgrammingExerciseStudentParticipation> participations = seedStudentParticipations(TEST_PREFIX + "student1", TEST_PREFIX + "student2");
        Path outputDir = tempFileUtilService.createTempDirectory("archival-export");
        List<String> exportErrors = new ArrayList<>();

        List<Path> exportedRepositories = programmingExerciseExportService.exportStudentRepositories(programmingExercise, participations, Map.of(), outputDir, exportErrors,
                ARCHIVAL_OPTIONS, RepositoryExportContent.WORKING_TREE_ONLY);

        assertThat(exportErrors).isEmpty();
        assertThat(exportedRepositories).hasSize(2).doesNotHaveDuplicates().allSatisfy(path -> {
            assertThat(path).isRegularFile().hasParent(outputDir);
            assertThat(path.getFileName().toString()).endsWith(".zip");
        });
        // No checkout means no temporary directory, and therefore no cleanup task that another one could race.
        verify(fileService, never()).createTemporaryDirectory(any(Path.class), any(), anyLong());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportStudentRepositories_shouldExportTheWorkingTreeWithoutGitMetadata() throws Exception {
        List<ProgrammingExerciseStudentParticipation> participations = seedStudentParticipations(TEST_PREFIX + "student1");
        Path outputDir = tempFileUtilService.createTempDirectory("archival-export-content");

        List<Path> exportedRepositories = programmingExerciseExportService.exportStudentRepositories(programmingExercise, participations, Map.of(), outputDir, new ArrayList<>(),
                ARCHIVAL_OPTIONS, RepositoryExportContent.WORKING_TREE_ONLY);

        assertThat(exportedRepositories).hasSize(1);
        byte[] zipContent = Files.readAllBytes(exportedRepositories.getFirst());
        ZipTestUtil.verifyZipDoesNotContainGitDirectory(zipContent);
        // The snapshot has to be the submitted working tree, not merely a zip without git metadata.
        assertThat(ZipTestUtil.readEntryAsString(zipContent, "src/Main.java")).as("the submitted file and its content must be in the snapshot").isEqualTo("public class Main {}");
    }

    /**
     * The manual repository export and the data export ask for the history even when no rewriting option is set, because
     * an instructor may untick every checkbox in the export dialog. Deriving the content from the options alone would
     * hand them a snapshot with no commits at all, which is the opposite of what unticking "combine student commits"
     * asks for. Those callers keep the directory layout they have always produced, but no longer pay for a checkout to
     * get it: the repository is materialized straight from the bare repository.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportStudentRepositories_shouldKeepTheHistoryWhenTheCallerAsksForItWithoutAnyRewritingOption() throws Exception {
        List<ProgrammingExerciseStudentParticipation> participations = seedStudentParticipations(TEST_PREFIX + "student1");
        Path outputDir = tempFileUtilService.createTempDirectory("manual-export-plain");
        List<String> exportErrors = new ArrayList<>();

        // Exactly the options of an instructor who unticked every checkbox in the export dialog.
        var noRewritingOptions = new RepositoryExportOptionsDTO(false, false, false, null, false, false, false, false, false);
        List<Path> exportedRepositories = programmingExerciseExportService.exportStudentRepositories(programmingExercise, participations, Map.of(), outputDir, exportErrors,
                noRewritingOptions, RepositoryExportContent.WITH_HISTORY);

        assertThat(exportErrors).isEmpty();
        assertThat(exportedRepositories).hasSize(1);
        Path exportedRepository = exportedRepositories.getFirst();
        assertThat(exportedRepository).as("the manual export keeps producing a directory, not a zip").isDirectory();
        // A .git directory on its own proves nothing; the commits are what unticking "combine student commits" is about.
        try (Git git = Git.open(exportedRepository.toFile())) {
            assertThat(git.log().call()).as("the student's commits must survive the export").isNotEmpty();
            assertThat(git.status().call().isClean()).as("the materialized working tree must match the index it ships with").isTrue();
        }
        // The history no longer costs a clone: without a rewriting option the repository is materialized straight from
        // the bare repository, so the export never asks for a checkout directory.
        verify(fileService, never()).createTemporaryDirectory(any(Path.class), any(), anyLong());
    }

    /**
     * A repository that cannot be read, because its setup failed and it never made it onto disk, has to be reported as an
     * export error and leave nothing behind: the callers zip whole directories, so a truncated archive inside one of them
     * would be read as an empty repository and hide the failure.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportStudentRepositories_shouldNotLeaveAnArchiveBehindForAnUnreadableRepository() throws Exception {
        var participation = (ProgrammingExerciseStudentParticipation) participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise,
                TEST_PREFIX + "student1");
        // A URI for a repository that was never created on disk, as left behind by a failed participation setup.
        String projectKey = programmingExercise.getProjectKey();
        String missingSlug = localVCLocalCITestService.getRepositorySlug(projectKey, "never-created");
        participation.setRepositoryUri(localVCLocalCITestService.buildLocalVCUri(TEST_PREFIX + "student1", projectKey, missingSlug));
        var savedParticipation = studentParticipationTestRepository.save(participation);
        Path outputDir = tempFileUtilService.createTempDirectory("archival-export-unreadable");
        List<String> exportErrors = new ArrayList<>();

        List<Path> exportedRepositories = programmingExerciseExportService.exportStudentRepositories(programmingExercise, List.of(savedParticipation), Map.of(), outputDir,
                exportErrors, ARCHIVAL_OPTIONS, RepositoryExportContent.WORKING_TREE_ONLY);

        assertThat(exportedRepositories).isEmpty();
        assertThat(exportErrors).isNotEmpty();
        try (var filesInOutputDir = Files.list(outputDir)) {
            assertThat(filesInOutputDir).as("a failed export must not leave a truncated archive behind").isEmpty();
        }
    }

    /**
     * More than one repository runs on a thread pool, and the tasks there collect their errors separately from the
     * list the caller passed, because that list is not required to be thread safe. This asserts the handover back to
     * the caller: without it a multi-repository export would report no errors at all, and the course archive would
     * tell an instructor nothing about the repository it skipped.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportStudentRepositories_shouldReportAnUnreadableRepositoryAlongsideTheOnesItExported() throws Exception {
        List<ProgrammingExerciseStudentParticipation> participations = new ArrayList<>(seedStudentParticipations(TEST_PREFIX + "student1"));
        var unreadable = (ProgrammingExerciseStudentParticipation) participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise,
                TEST_PREFIX + "student2");
        // A URI for a repository that was never created on disk, as left behind by a failed participation setup.
        String projectKey = programmingExercise.getProjectKey();
        String missingSlug = localVCLocalCITestService.getRepositorySlug(projectKey, "never-created");
        unreadable.setRepositoryUri(localVCLocalCITestService.buildLocalVCUri(TEST_PREFIX + "student2", projectKey, missingSlug));
        participations.add(studentParticipationTestRepository.save(unreadable));

        Path outputDir = tempFileUtilService.createTempDirectory("archival-export-partial");
        // Deliberately a plain list, which is what exportStudentRepositoriesToZipFile passes.
        List<String> exportErrors = new ArrayList<>();

        List<Path> exportedRepositories = programmingExerciseExportService.exportStudentRepositories(programmingExercise, participations, Map.of(), outputDir, exportErrors,
                ARCHIVAL_OPTIONS, RepositoryExportContent.WORKING_TREE_ONLY);

        assertThat(participations).as("two repositories, so that the pool is used rather than the single-repository path").hasSize(2);
        assertThat(exportedRepositories).as("the readable repository still has to be exported").hasSize(1);
        assertThat(exportErrors).as("an export that skipped a repository has to say which one").anyMatch(error -> error.contains(unreadable.getId().toString()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportStudentRepositories_shouldNameTheZipAfterTheParticipant() throws Exception {
        List<ProgrammingExerciseStudentParticipation> participations = seedStudentParticipations(TEST_PREFIX + "student1");
        Path outputDir = tempFileUtilService.createTempDirectory("archival-export-naming");

        List<Path> exportedRepositories = programmingExerciseExportService.exportStudentRepositories(programmingExercise, participations, Map.of(), outputDir, new ArrayList<>(),
                ARCHIVAL_OPTIONS, RepositoryExportContent.WORKING_TREE_ONLY);

        assertThat(exportedRepositories.getFirst().getFileName().toString()).contains(TEST_PREFIX + "student1");
    }

    /**
     * The exercise export puts one zip per repository into the output directory. Instructor repositories keep their
     * history so the exported material can be re-imported and inspected, which is what the import expects to find.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportProgrammingExerciseRepositories_shouldWriteOneZipPerRepositoryWithHistory() throws Exception {
        createAndSeedBaseRepositories();
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        Path outputDir = tempFileUtilService.createTempDirectory("instructor-export");
        List<String> exportErrors = new ArrayList<>();
        List<ArchivalReportEntry> reportData = new ArrayList<>();

        List<Path> exportedRepositories = programmingExerciseExportService.exportProgrammingExerciseRepositories(programmingExercise, false, false, outputDir, exportErrors,
                reportData);

        assertThat(exportErrors).isEmpty();
        assertThat(exportedRepositories).hasSize(3).doesNotHaveDuplicates();
        for (Path exportedRepository : exportedRepositories) {
            assertThat(exportedRepository).isRegularFile().hasParent(outputDir);
            ZipTestUtil.verifyZipContainsGitDirectory(Files.readAllBytes(exportedRepository));
        }
    }

    /**
     * The synthetic {@code .git} directory is assembled by hand, so the only meaningful check is whether Git accepts the
     * result: the extracted archive must open, resolve its branch, and report a clean working tree, which it only does if
     * the serialized index agrees with the files that were written next to it.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportProgrammingExerciseRepositories_shouldProduceAnExtractableWorkingRepository() throws Exception {
        createAndSeedBaseRepositories();
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        Path outputDir = tempFileUtilService.createTempDirectory("instructor-export-usable");

        List<Path> exportedRepositories = programmingExerciseExportService.exportProgrammingExerciseRepositories(programmingExercise, false, false, outputDir, new ArrayList<>(),
                new ArrayList<>());

        assertThat(exportedRepositories).isNotEmpty();
        Path extractedDir = tempFileUtilService.createTempDirectory("instructor-export-extracted");
        ZipTestUtil.extractZip(Files.readAllBytes(exportedRepositories.getFirst()), extractedDir);

        try (Git git = Git.open(extractedDir.toFile())) {
            assertThat(git.getRepository().resolve(Constants.HEAD)).as("HEAD of the extracted repository").isNotNull();
            assertThat(git.log().call()).as("commits reachable in the extracted repository").isNotEmpty();
            Status status = git.status().call();
            assertThat(status.getUntracked()).as("untracked files: the serialized index must cover the extracted working tree").isEmpty();
            assertThat(status.getMissing()).as("missing files: the extracted working tree must cover the serialized index").isEmpty();
            assertThat(status.getModified()).as("modified files: index entries must match the extracted file contents").isEmpty();
        }
    }

    /**
     * The only remote URL available at export time is the server's own path to the bare repository, which the archive
     * must not disclose and which is unusable on the machine that extracts it.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportProgrammingExerciseRepositories_shouldNotDiscloseTheInternalRepositoryPath() throws Exception {
        createAndSeedBaseRepositories();
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        Path outputDir = tempFileUtilService.createTempDirectory("instructor-export-config");

        List<Path> exportedRepositories = programmingExerciseExportService.exportProgrammingExerciseRepositories(programmingExercise, false, false, outputDir, new ArrayList<>(),
                new ArrayList<>());

        assertThat(exportedRepositories).isNotEmpty();
        for (Path exportedRepository : exportedRepositories) {
            String gitConfig = ZipTestUtil.readEntryAsString(Files.readAllBytes(exportedRepository), ".git/config");
            assertThat(gitConfig).as("the exported .git/config of %s", exportedRepository.getFileName()).isNotNull().doesNotContain("file:").doesNotContain("[remote");
        }
    }

    /**
     * Seeds a student repository the way a started exercise leaves it: the commit that set the exercise up, and the student's own work on top of it.
     * <p>
     * Combining and anonymizing commits both rewrite the history back to the setup commit and refuse to run without one, so a repository that only carries the student's
     * commits is not a repository those options can be tested against.
     */
    private ProgrammingExerciseStudentParticipation seedStudentParticipationOnTopOfTheExerciseSetup(String login) throws Exception {
        var participation = (ProgrammingExerciseStudentParticipation) participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, login);
        var repository = RepositoryExportTestUtil.seedStudentRepositoryForParticipation(localVCLocalCITestService, participation);
        RepositoryExportTestUtil.writeFilesAndPush(repository, Map.of("README.md", "the exercise"), SET_UP_TEMPLATE_FOR_EXERCISE);
        RepositoryExportTestUtil.writeFilesAndPush(repository, Map.of("src/Main.java", "public class Main {}"), "my solution");
        return studentParticipationTestRepository.save(participation);
    }

    private List<ProgrammingExerciseStudentParticipation> seedStudentParticipations(String... logins) throws Exception {
        List<ProgrammingExerciseStudentParticipation> participations = new ArrayList<>();
        for (String login : logins) {
            var participation = (ProgrammingExerciseStudentParticipation) participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, login);
            var repository = RepositoryExportTestUtil.seedStudentRepositoryForParticipation(localVCLocalCITestService, participation);
            RepositoryExportTestUtil.writeFilesAndPush(repository, Map.of("src/Main.java", "public class Main {}"), "initial commit");
            participations.add(studentParticipationTestRepository.save(participation));
        }
        return participations;
    }

    /** Creates the template, solution and tests repositories for the exercise and pushes one commit into each. */
    private RepositoryExportTestUtil.BaseRepositories createAndSeedBaseRepositories() throws Exception {
        var baseRepositories = RepositoryExportTestUtil.createAndWireBaseRepositoriesWithHandles(localVCLocalCITestService, programmingExercise);
        RepositoryExportTestUtil.writeFilesAndPush(baseRepositories.templateRepository(), Map.of("src/Main.java", "public class Main {}"), "template");
        RepositoryExportTestUtil.writeFilesAndPush(baseRepositories.solutionRepository(), Map.of("src/Main.java", "public class Main { int solved; }"), "solution");
        RepositoryExportTestUtil.writeFilesAndPush(baseRepositories.testsRepository(), Map.of("test/MainTest.java", "public class MainTest {}"), "tests");
        return baseRepositories;
    }

    /**
     * A repository is not only its default branch. Anyone with push access can add a branch or a tag, and the clone this
     * export replaced carried both into the archive. Packing only the exported branch would silently drop commits that
     * are reachable from nowhere else, which is the one thing an archive must not do.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportProgrammingExerciseRepositories_shouldKeepSecondaryBranchesAndTags() throws Exception {
        var baseRepositories = createAndSeedBaseRepositories();
        var templateRepository = baseRepositories.templateRepository();
        var workingCopy = templateRepository.workingCopy();
        String defaultBranch = workingCopy.getRepository().getBranch();

        workingCopy.tag().setName("v1.0").setMessage("annotated release tag").setAnnotated(true).call();
        workingCopy.checkout().setCreateBranch(true).setName("feature").call();
        RepositoryExportTestUtil.writeFilesAndPush(templateRepository, Map.of("src/OnlyOnFeature.java", "public class OnlyOnFeature {}"), "only on feature");
        var featureCommit = workingCopy.getRepository().resolve("HEAD");
        workingCopy.checkout().setName(defaultBranch).call();
        workingCopy.push().setRemote("origin").setPushAll().setPushTags().call();

        Path outputDir = tempFileUtilService.createTempDirectory("instructor-export-refs");
        List<Path> exportedRepositories = programmingExerciseExportService.exportProgrammingExerciseRepositories(programmingExercise, false, false, outputDir, new ArrayList<>(),
                new ArrayList<>());

        Path templateArchive = exportedRepositories.stream().filter(path -> path.getFileName().toString().contains("-exercise")).findFirst().orElseThrow();
        Path extractedDir = tempFileUtilService.createTempDirectory("instructor-export-refs-extracted");
        ZipTestUtil.extractZip(Files.readAllBytes(templateArchive), extractedDir);

        try (Git git = Git.open(extractedDir.toFile())) {
            var repository = git.getRepository();
            assertThat(repository.resolve("refs/heads/feature")).as("the secondary branch must survive the export").isEqualTo(featureCommit);
            assertThat(repository.resolve("refs/tags/v1.0")).as("the annotated tag must survive the export").isNotNull();
            // The ref alone is worthless if the commit it names is not in the pack.
            assertThat(repository.getObjectDatabase().has(featureCommit)).as("the commit reachable only from the secondary branch must be in the pack").isTrue();
            assertThat(git.log().add(featureCommit).call()).as("the secondary branch history must be walkable").isNotEmpty();
        }
    }

    /**
     * An exercise can carry auxiliary repositories next to template, solution and tests, and they take the same
     * clone-free path. They are exported through a branch of their own, so the other instructor repositories passing
     * says nothing about them.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportProgrammingExerciseRepositories_shouldExportAuxiliaryRepositories() throws Exception {
        createAndSeedBaseRepositories();
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        seedAuxiliaryRepository("solutionhints", Map.of("hints/Hint.java", "public class Hint {}"));

        Path outputDir = tempFileUtilService.createTempDirectory("instructor-export-auxiliary");
        List<String> exportErrors = new ArrayList<>();

        List<Path> exportedRepositories = programmingExerciseExportService.exportProgrammingExerciseRepositories(programmingExercise, false, false, outputDir, exportErrors,
                new ArrayList<>());

        assertThat(exportErrors).isEmpty();
        Path auxiliaryArchive = exportedRepositories.stream().filter(path -> path.getFileName().toString().contains("solutionhints")).findFirst()
                .orElseThrow(() -> new AssertionError("the auxiliary repository is missing from the export: " + exportedRepositories));
        // The archive has to carry the auxiliary repository's own content, not merely exist under the right name.
        byte[] zipContent = Files.readAllBytes(auxiliaryArchive);
        assertThat(ZipTestUtil.readEntryAsString(zipContent, "hints/Hint.java")).isEqualTo("public class Hint {}");
        ZipTestUtil.verifyZipContainsGitDirectory(zipContent);
    }

    /** Creates a LocalVC repository for an auxiliary repository of the exercise, pushes one commit and persists it. */
    private void seedAuxiliaryRepository(String name, Map<String, String> files) throws Exception {
        String projectKey = programmingExercise.getProjectKey();
        String repositorySlug = programmingExercise.generateRepositoryName(name);
        var repository = RepositoryExportTestUtil.seedBareRepository(localVCLocalCITestService, projectKey, repositorySlug, null);
        RepositoryExportTestUtil.writeFilesAndPush(repository, files, "auxiliary content");

        AuxiliaryRepository auxiliaryRepository = new AuxiliaryRepository();
        auxiliaryRepository.setName(name);
        auxiliaryRepository.setDescription("an auxiliary repository");
        auxiliaryRepository.setCheckoutDirectory(name);
        auxiliaryRepository.setRepositoryUri(localVCLocalCITestService.buildLocalVCUri(null, null, projectKey, repositorySlug));
        auxiliaryRepository.setExercise(programmingExercise);
        // The association is an ordered list, so the child has to be saved through the exercise: persisting it on its
        // own leaves the order column null and every later read of the exercise fails.
        programmingExercise.setAuxiliaryRepositories(new ArrayList<>(List.of(auxiliaryRepository)));
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
    }

    /**
     * Exercises from old courses can have a participation without a repository URI. The export has to report that and
     * carry on with the remaining repositories rather than failing outright.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportProgrammingExerciseRepositories_shouldReportMissingRepositoryUrisAsErrors() throws Exception {
        createAndSeedBaseRepositories();
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        // The export reloads the exercise, so the URI has to be cleared in the database rather than on this instance.
        var templateParticipation = templateParticipationTestRepository.findByProgrammingExerciseId(programmingExercise.getId()).orElseThrow();
        templateParticipation.setRepositoryUri((String) null);
        templateParticipationTestRepository.save(templateParticipation);

        Path outputDir = tempFileUtilService.createTempDirectory("instructor-export-missing");
        List<String> exportErrors = new ArrayList<>();

        List<Path> exportedRepositories = programmingExerciseExportService.exportProgrammingExerciseRepositories(programmingExercise, false, false, outputDir, exportErrors,
                new ArrayList<>());

        assertThat(exportErrors).anyMatch(error -> error.contains("the repository uri is not defined"));
        // The solution and tests repositories are unaffected and still make it into the export.
        assertThat(exportedRepositories).hasSize(2);
    }

    /**
     * The export options that rewrite a repository - adding the participant name, combining commits, anonymizing and
     * normalizing the code style - are the only ones that need a checkout, and the manual repository download is the
     * caller that sets them. Everything the archiving tests above cover runs on the other path, so none of this code is
     * reached by them.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportStudentRepositoriesToZipFile_withTheRewritingOptions_producesOneArchiveNamedAfterTheParticipants() throws Exception {
        List<ProgrammingExerciseStudentParticipation> participations = List.of(seedStudentParticipationOnTopOfTheExerciseSetup(TEST_PREFIX + "student1"),
                seedStudentParticipationOnTopOfTheExerciseSetup(TEST_PREFIX + "student2"));
        RepositoryExportOptionsDTO rewritingOptions = new RepositoryExportOptionsDTO(true, false, false, null, false, true, true, false, true);

        File exportedArchive = programmingExerciseExportService.exportStudentRepositoriesToZipFile(programmingExercise, participations, rewritingOptions, Map.of());

        assertThat(exportedArchive).as("the download hands back one archive for the whole exercise").isNotNull();
        assertThat(exportedArchive.toPath()).isRegularFile();
        assertThat(exportedArchive.getName()).as("the archive is named after the course and the exercise")
                .startsWith(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getShortName() + "-" + programmingExercise.getShortName()).endsWith(".zip");
        byte[] zipContent = Files.readAllBytes(exportedArchive.toPath());
        for (ProgrammingExerciseStudentParticipation participation : participations) {
            assertThat(ZipTestUtil.readEntryAsString(zipContent, participation.getParticipantIdentifier() + "/src/Main.java"))
                    .as("the repository of %s is part of the archive, under a directory named after them", participation.getParticipantIdentifier())
                    .isEqualTo("public class Main {}");
        }
        // An export that is not anonymized still drops the remote, so nothing in the archive points back at the Artemis instance it came from.
        assertThat(ZipTestUtil.readEntryAsString(zipContent, participations.getFirst().getParticipantIdentifier() + "/.git/config")).as("the exported clone keeps no remote")
                .isNotNull().doesNotContain("[remote");
    }

    /**
     * Anonymizing is the one rewriting option that has to change what the archive reveals: the participant must not be
     * identifiable from it, neither through the directory name nor through the commit authors.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportStudentRepositoriesToZipFile_whenAnonymizing_namesNoParticipant() throws Exception {
        List<ProgrammingExerciseStudentParticipation> participations = List.of(seedStudentParticipationOnTopOfTheExerciseSetup(TEST_PREFIX + "student1"));
        RepositoryExportOptionsDTO anonymizingOptions = new RepositoryExportOptionsDTO(true, false, false, null, false, false, true, true, false);

        File exportedArchive = programmingExerciseExportService.exportStudentRepositoriesToZipFile(programmingExercise, participations, anonymizingOptions, Map.of());

        assertThat(exportedArchive).as("anonymizing still produces an archive").isNotNull();
        byte[] zipContent = Files.readAllBytes(exportedArchive.toPath());
        assertThat(ZipTestUtil.readEntryAsString(zipContent, "-student-submission.git/src/Main.java")).as("the submitted work is exported under an anonymous directory")
                .isEqualTo("public class Main {}");
        assertThat(ZipTestUtil.listEntryNames(zipContent)).as("no entry names the student").noneMatch(name -> name.contains(TEST_PREFIX + "student1"));

        // The directory name is the smaller half of anonymizing. The identity a reviewer would actually go looking for sits in the commits themselves.
        Path extractedDir = tempFileUtilService.createTempDirectory("anonymized-export-extracted");
        ZipTestUtil.extractZip(zipContent, extractedDir);
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        try (var repositoryDirectories = Files.list(extractedDir)) {
            Path exportedRepository = repositoryDirectories.findFirst().orElseThrow();
            try (Git git = Git.open(exportedRepository.toFile())) {
                assertThat(git.log().call()).as("the exported history is not empty").isNotEmpty().allSatisfy(commit -> {
                    assertThat(commit.getAuthorIdent().getName()).as("no commit is authored by the student").isNotEqualTo(student.getName());
                    assertThat(commit.getAuthorIdent().getEmailAddress()).as("no commit carries the student's address").isNotEqualTo(student.getEmail());
                    assertThat(commit.getCommitterIdent().getName()).as("no commit is committed by the student").isNotEqualTo(student.getName());
                    assertThat(commit.getCommitterIdent().getEmailAddress()).as("no commit carries the student's address as committer").isNotEqualTo(student.getEmail());
                });
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportStudentRepositoriesToZipFile_withoutAnyParticipation_handsBackNothing() {
        // Nothing to zip is not an error: an instructor can filter the export down to a set of students that turns out to be empty.
        File exportedArchive = programmingExerciseExportService.exportStudentRepositoriesToZipFile(programmingExercise, List.of(), ARCHIVAL_OPTIONS, Map.of());

        assertThat(exportedArchive).as("an export without a single repository produces no archive").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportStudentRepositories_shouldSkipAParticipationWithoutARepository() throws Exception {
        // Participations of old exercises can be stored without a repository URI. There is nothing to export for them, and nothing to report either.
        List<ProgrammingExerciseStudentParticipation> participations = seedStudentParticipations(TEST_PREFIX + "student1");
        var participationWithoutRepository = (ProgrammingExerciseStudentParticipation) participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise,
                TEST_PREFIX + "student2");
        participationWithoutRepository.setRepositoryUri((String) null);
        participations = new ArrayList<>(participations);
        participations.add(studentParticipationTestRepository.save(participationWithoutRepository));
        Path outputDir = tempFileUtilService.createTempDirectory("export-without-repository");
        List<String> exportErrors = new ArrayList<>();

        List<Path> exportedRepositories = programmingExerciseExportService.exportStudentRepositories(programmingExercise, participations, Map.of(), outputDir, exportErrors,
                ARCHIVAL_OPTIONS, RepositoryExportContent.WORKING_TREE_ONLY);

        assertThat(exportedRepositories).as("only the participation that has a repository is exported").hasSize(1);
        assertThat(exportErrors).as("a participation without a repository is skipped, not reported as a failure").isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportStudentRepositories_shouldSkipPracticeParticipationsWhenTheyAreExcluded() throws Exception {
        List<ProgrammingExerciseStudentParticipation> participations = new ArrayList<>(seedStudentParticipations(TEST_PREFIX + "student1"));
        var practiceParticipation = participations.getFirst();
        practiceParticipation.setPracticeMode(true);
        participations.set(0, studentParticipationTestRepository.save(practiceParticipation));
        Path outputDir = tempFileUtilService.createTempDirectory("export-without-practice");
        RepositoryExportOptionsDTO withoutPracticeSubmissions = new RepositoryExportOptionsDTO(true, false, false, null, true, false, false, false, false);
        List<String> exportErrors = new ArrayList<>();

        List<Path> exportedRepositories = programmingExerciseExportService.exportStudentRepositories(programmingExercise, participations, Map.of(), outputDir, exportErrors,
                withoutPracticeSubmissions, RepositoryExportContent.WORKING_TREE_ONLY);

        assertThat(exportedRepositories).as("a practice repository is left out when practice submissions are excluded").isEmpty();
        assertThat(exportErrors).as("excluding a participation is not a failure").isEmpty();
        assertThat(outputDir).as("nothing is written for an excluded participation").isEmptyDirectory();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportProgrammingExerciseForDownload_writesTheRepositoriesTheProblemStatementAndTheExerciseDetails() throws Exception {
        createAndSeedBaseRepositories();
        programmingExercise.setProblemStatement("Implement the sorting strategies.");
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        List<String> exportErrors = new ArrayList<>();

        Path exportedArchive = programmingExerciseExportService.exportProgrammingExerciseForDownload(programmingExercise, exportErrors);

        assertThat(exportErrors).as("a complete exercise exports without errors").isEmpty();
        assertThat(exportedArchive).isRegularFile();
        assertThat(exportedArchive.getFileName().toString()).as("the archive is named after the course and the exercise")
                .startsWith("Material-" + programmingExercise.getCourseViaExerciseGroupOrCourseMember().getShortName()).endsWith(".zip");
        byte[] zipContent = Files.readAllBytes(exportedArchive);
        assertThat(ZipTestUtil.readEntryAsString(zipContent, "Problem-Statement-" + programmingExercise.getSanitizedExerciseTitle() + ".md"))
                .as("the problem statement is part of the material").isEqualTo("Implement the sorting strategies.");
        assertThat(ZipTestUtil.readEntryAsString(zipContent, "Exercise-Details-" + programmingExercise.getTitle() + ".json")).as("the exercise details are part of the material")
                .isNotNull().contains(programmingExercise.getTitle());
    }

    /**
     * The exercise details file is the create form of the import from file and of the sharing import: the client parses
     * it and posts it back, so every field those imports bind has to survive the export.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportProgrammingExerciseForDownload_writesDetailsTheImportBindsBack() throws Exception {
        createAndSeedBaseRepositories();
        programmingExercise.setProblemStatement("Implement the sorting strategies.");
        programmingExercise.setCategories(new HashSet<>(Set.of("{\"category\":\"homework\"}")));
        programmingExercise.setPlagiarismDetectionConfig(PlagiarismDetectionConfig.createDefault());
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        // the export endpoint hands the service an exercise loaded with its configurations, so the test does the same
        var exerciseToExport = programmingExerciseRepository
                .findByIdWithPlagiarismDetectionConfigTeamConfigBuildConfigGradingCriteriaAndCategoriesElseThrow(programmingExercise.getId());

        Path exportedArchive = programmingExerciseExportService.exportProgrammingExerciseForDownload(exerciseToExport, new ArrayList<>());

        String details = ZipTestUtil.readEntryAsString(Files.readAllBytes(exportedArchive), "Exercise-Details-" + programmingExercise.getTitle() + ".json");
        // the client compares the discriminator with the exercise type it imports, and refuses the archive without it
        assertThat(details).as("the details keep the exercise type discriminator").contains("\"type\":\"programming\"");
        assertThat(details).as("no student data and no entity back references are written").doesNotContain("studentParticipations").doesNotContain("\"exercises\"");

        var importRequest = JsonObjectMapper.get().readValue(details, ImportProgrammingExerciseRequestDTO.class);
        assertThat(importRequest.title()).isEqualTo(programmingExercise.getTitle());
        assertThat(importRequest.shortName()).isEqualTo(programmingExercise.getShortName());
        assertThat(importRequest.problemStatement()).isEqualTo("Implement the sorting strategies.");
        assertThat(importRequest.categories()).isEqualTo(programmingExercise.getCategories());
        assertThat(importRequest.maxPoints()).isEqualTo(programmingExercise.getMaxPoints());
        assertThat(importRequest.programmingLanguage()).isEqualTo(programmingExercise.getProgrammingLanguage());
        assertThat(importRequest.projectType()).isEqualTo(programmingExercise.getProjectType());
        assertThat(importRequest.packageName()).isEqualTo(programmingExercise.getPackageName());
        assertThat(importRequest.allowOfflineIde()).isEqualTo(programmingExercise.isAllowOfflineIde());
        assertThat(importRequest.buildConfig()).as("the build configuration is part of the create form").isNotNull();
        assertThat(importRequest.buildConfig().buildScript()).isEqualTo(exerciseToExport.getBuildConfig().getBuildScript());

        // The import creates a new exercise, so it must build fresh configurations instead of adopting the ids of the
        // exported exercise's configuration rows.
        var importedExercise = importRequest.toEntity();
        assertThat(importedExercise.getPlagiarismDetectionConfig()).isNotNull();
        assertThat(importedExercise.getPlagiarismDetectionConfig().getId()).as("the imported plagiarism configuration does not adopt an id").isNull();
        assertThat(importedExercise.getPlagiarismDetectionConfig().getSimilarityThreshold()).isEqualTo(exerciseToExport.getPlagiarismDetectionConfig().getSimilarityThreshold());
    }

    /**
     * The file itself carries no configuration id either, because the importer reading it is not necessarily this
     * version: every released version copies the id of the plagiarism configuration from the request onto the exercise
     * it creates and clears it only after that exercise was saved, and the association cascades ALL.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportProgrammingExerciseForDownload_writesNoConfigurationIds() throws Exception {
        createAndSeedBaseRepositories();
        programmingExercise.setPlagiarismDetectionConfig(PlagiarismDetectionConfig.createDefault());
        programmingExercise.setTeamAssignmentConfig(teamAssignmentConfig());
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        var exerciseToExport = programmingExerciseRepository
                .findByIdWithPlagiarismDetectionConfigTeamConfigBuildConfigGradingCriteriaAndCategoriesElseThrow(programmingExercise.getId());
        assertThat(exerciseToExport.getPlagiarismDetectionConfig().getId()).isNotNull();
        assertThat(exerciseToExport.getTeamAssignmentConfig().getId()).isNotNull();

        Path exportedArchive = programmingExerciseExportService.exportProgrammingExerciseForDownload(exerciseToExport, new ArrayList<>());

        String details = ZipTestUtil.readEntryAsString(Files.readAllBytes(exportedArchive), "Exercise-Details-" + programmingExercise.getTitle() + ".json");
        JsonNode written = JsonObjectMapper.get().readTree(details);
        assertThat(written.get("plagiarismDetectionConfig").get("id")).as("the exported plagiarism configuration carries no id").isNull();
        assertThat(written.get("teamAssignmentConfig").get("id")).as("the exported team assignment configuration carries no id").isNull();
        // the settings themselves survive, only the identity is gone
        assertThat(written.get("plagiarismDetectionConfig").get("similarityThreshold").asInt()).isEqualTo(exerciseToExport.getPlagiarismDetectionConfig().getSimilarityThreshold());
        assertThat(written.get("teamAssignmentConfig").get("maxTeamSize").asInt()).isEqualTo(10);
    }

    /**
     * An importer that has not upgraded copies the id of the plagiarism configuration out of the request and saves the
     * exercise before clearing it. Such a file therefore has to import into a configuration that is still transient,
     * so that the save inserts a row instead of reaching persistence with the identity of the exported exercise's row.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportProgrammingExerciseForDownload_detailsImportUnderAnIdCopyingImporter() throws Exception {
        createAndSeedBaseRepositories();
        programmingExercise.setPlagiarismDetectionConfig(PlagiarismDetectionConfig.createDefault());
        programmingExercise.setTeamAssignmentConfig(teamAssignmentConfig());
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        var exerciseToExport = programmingExerciseRepository
                .findByIdWithPlagiarismDetectionConfigTeamConfigBuildConfigGradingCriteriaAndCategoriesElseThrow(programmingExercise.getId());
        Long sourceConfigId = exerciseToExport.getPlagiarismDetectionConfig().getId();

        Path exportedArchive = programmingExerciseExportService.exportProgrammingExerciseForDownload(exerciseToExport, new ArrayList<>());

        String details = ZipTestUtil.readEntryAsString(Files.readAllBytes(exportedArchive), "Exercise-Details-" + programmingExercise.getTitle() + ".json");
        var importRequest = JsonObjectMapper.get().readValue(details, ImportProgrammingExerciseRequestDTO.class);

        // what the older mapper does with the request: copy the id, then copy the settings
        PlagiarismDetectionConfig adopted = new PlagiarismDetectionConfig();
        adopted.setId(importRequest.plagiarismDetectionConfig().id());
        importRequest.plagiarismDetectionConfig().applyTo(adopted);
        assertThat(adopted.getId()).as("the copied configuration stays transient, so the importing instance inserts its own row").isNull();

        ProgrammingExercise imported = importRequest.toEntity();
        imported.setPlagiarismDetectionConfig(adopted);
        imported.setId(null);
        imported.setShortName(programmingExercise.getShortName() + "imp");
        imported.setTitle(programmingExercise.getTitle() + " imported");
        imported.setCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        imported.setBuildConfig(programmingExerciseBuildConfigRepository.save(new ProgrammingExerciseBuildConfig()));
        var saved = programmingExerciseRepository.save(imported);

        assertThat(saved.getPlagiarismDetectionConfig().getId()).as("the import created its own configuration row").isNotNull().isNotEqualTo(sourceConfigId);
        var source = programmingExerciseRepository.findByIdWithPlagiarismDetectionConfigTeamConfigBuildConfigGradingCriteriaAndCategoriesElseThrow(programmingExercise.getId());
        assertThat(source.getPlagiarismDetectionConfig().getId()).as("the exported exercise keeps its own configuration row").isEqualTo(sourceConfigId);
    }

    /**
     * The sharing import posts the details file back as the create form and drops nothing but the top-level id on the
     * way (see {@code ExerciseSharingService#getExerciseDetailsFromBasket}). An auxiliary repository that still carried
     * its id would therefore reach the creation, which rejects it with {@code INVALID_AUXILIARY_REPOSITORY_ID}: a
     * shared exercise with an auxiliary repository could not be imported at all.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportProgrammingExerciseForDownload_detailsWithAuxiliaryRepositoriesImportIntoTheSharingCreation() throws Exception {
        createAndSeedBaseRepositories();
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        seedAuxiliaryRepository("solutionhints", Map.of("hints/Hint.java", "public class Hint {}"));
        var exerciseToExport = programmingExerciseRepository
                .findByIdWithPlagiarismDetectionConfigTeamConfigBuildConfigGradingCriteriaAndCategoriesElseThrow(programmingExercise.getId());

        Path exportedArchive = programmingExerciseExportService.exportProgrammingExerciseForDownload(exerciseToExport, new ArrayList<>());

        String details = ZipTestUtil.readEntryAsString(Files.readAllBytes(exportedArchive), "Exercise-Details-" + programmingExercise.getTitle() + ".json");
        // what the sharing import does to the file before it binds it: the exercise id goes, the nested ones stay
        ObjectNode written = (ObjectNode) JsonObjectMapper.get().readTree(details);
        written.remove("id");
        assertThat(written.get("auxiliaryRepositories")).as("the auxiliary repository is part of the create form").hasSize(1);
        assertThat(written.get("auxiliaryRepositories").get(0).get("id")).as("the exported auxiliary repository carries no id").isNull();

        var importRequest = JsonObjectMapper.get().treeToValue(written, ImportProgrammingExerciseRequestDTO.class);
        ProgrammingExercise imported = importRequest.toEntity();
        assertThatCode(() -> auxiliaryRepositoryService.validateAndAddAuxiliaryRepositoriesOfProgrammingExercise(imported, imported.getAuxiliaryRepositories()))
                .as("the shared exercise passes the validation the creation runs").doesNotThrowAnyException();
        // the repository itself survives, only the identity is gone
        assertThat(imported.getAuxiliaryRepositories()).hasSize(1);
        assertThat(imported.getAuxiliaryRepositories().getFirst().getName()).isEqualTo("solutionhints");
        assertThat(imported.getAuxiliaryRepositories().getFirst().getCheckoutDirectory()).isEqualTo("solutionhints");
    }

    /**
     * The details file is read back by another instance, which creates a new exercise from it, and every nested record
     * is bound by a {@code toEntity()} that copies the id through. One id that slips into the file therefore lets an
     * import write onto a row of the exporting instance, so the file carries no nested id at all: the exercise's own
     * id and the references to its course and exercise group are the only ones, and the import replaces those.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportProgrammingExerciseForDownload_writesNoNestedIds() throws Exception {
        createAndSeedBaseRepositories();
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        seedAuxiliaryRepository("solutionhints", Map.of("hints/Hint.java", "public class Hint {}"));
        programmingExercise.setPlagiarismDetectionConfig(PlagiarismDetectionConfig.createDefault());
        programmingExercise.setTeamAssignmentConfig(teamAssignmentConfig());
        programmingExercise.setGradingCriteria(new HashSet<>(Set.of(criterionWithInstruction())));
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        var exerciseToExport = programmingExerciseRepository
                .findByIdWithPlagiarismDetectionConfigTeamConfigBuildConfigGradingCriteriaAndCategoriesElseThrow(programmingExercise.getId());
        // No export query loads participations today. They are put on the exercise here so that the file is what the
        // projection decides rather than what a query happened to fetch: a wider graph must not leak student work.
        var studentParticipations = seedStudentParticipations(TEST_PREFIX + "student1");
        exerciseToExport.setStudentParticipations(new HashSet<>(studentParticipations));
        var templateParticipation = templateParticipationTestRepository.findByProgrammingExerciseId(programmingExercise.getId()).orElseThrow();
        ProgrammingSubmission templateSubmission = new ProgrammingSubmission();
        templateSubmission.setCommitHash("cafebabe1234");
        templateParticipation.setSubmissions(Set.of(templateSubmission));
        exerciseToExport.setTemplateParticipation(templateParticipation);

        Path exportedArchive = programmingExerciseExportService.exportProgrammingExerciseForDownload(exerciseToExport, new ArrayList<>());

        String details = ZipTestUtil.readEntryAsString(Files.readAllBytes(exportedArchive), "Exercise-Details-" + programmingExercise.getTitle() + ".json");
        JsonNode written = JsonObjectMapper.get().readTree(details);
        // the fixture has to reach the file, otherwise the sweep below would pass on an empty exercise
        assertThat(written.get("buildConfig")).as("the build configuration is part of the exported details").isNotNull();
        assertThat(written.get("gradingCriteria")).as("the rubric is part of the exported details").hasSize(1);
        assertThat(written.get("auxiliaryRepositories")).as("the auxiliary repository is part of the exported details").hasSize(1);
        assertThat(written.get("plagiarismDetectionConfig")).as("the plagiarism configuration is part of the exported details").isNotNull();
        assertThat(written.get("teamAssignmentConfig")).as("the team assignment configuration is part of the exported details").isNotNull();

        assertThat(nestedIdPaths(written, "")).as("no nested id is written into the details file").isEmpty();
        assertThat(written.has("studentParticipations")).as("no student participation is written into the details file").isFalse();
        assertThat(details).as("no login of a student is written into the details file").doesNotContain(TEST_PREFIX + "student1");
        assertThat(details).as("no submission and no build result is written into the details file").doesNotContain("submissions").doesNotContain("cafebabe1234");
        assertThat(written.get("id")).as("the exercise's own id stays, the import drops it").isNotNull();
    }

    /**
     * Collects the path of every {@code id} below the root of the given details node. The course and the exercise group
     * are references to rows the import replaces with its own target, so their ids are not part of the result.
     */
    private static List<String> nestedIdPaths(JsonNode node, String path) {
        List<String> found = new ArrayList<>();
        if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                found.addAll(nestedIdPaths(node.get(index), path + "[" + index + "]"));
            }
            return found;
        }
        if (!node.isObject()) {
            return found;
        }
        for (var property : node.properties()) {
            String childPath = path.isEmpty() ? property.getKey() : path + "." + property.getKey();
            if ("course".equals(property.getKey()) || "exerciseGroup".equals(property.getKey())) {
                continue;
            }
            if ("id".equals(property.getKey())) {
                if (!path.isEmpty() && !property.getValue().isNull()) {
                    found.add(childPath);
                }
                continue;
            }
            found.addAll(nestedIdPaths(property.getValue(), childPath));
        }
        return found;
    }

    private static TeamAssignmentConfig teamAssignmentConfig() {
        TeamAssignmentConfig config = new TeamAssignmentConfig();
        config.setMinTeamSize(1);
        config.setMaxTeamSize(10);
        return config;
    }

    /**
     * The download export nulls the criterion and instruction ids so that the import can persist them, which strips the
     * only thing that told two otherwise identical criteria apart. The exported rubric still has to keep both rows.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportProgrammingExerciseForDownload_keepsIdenticalGradingCriteria() throws Exception {
        createAndSeedBaseRepositories();
        Set<GradingCriterion> criteria = new HashSet<>();
        criteria.add(criterionWithInstruction());
        criteria.add(criterionWithInstruction());
        programmingExercise.setGradingCriteria(criteria);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        var exerciseToExport = programmingExerciseRepository
                .findByIdWithPlagiarismDetectionConfigTeamConfigBuildConfigGradingCriteriaAndCategoriesElseThrow(programmingExercise.getId());
        assertThat(exerciseToExport.getGradingCriteria()).hasSize(2);

        Path exportedArchive = programmingExerciseExportService.exportProgrammingExerciseForDownload(exerciseToExport, new ArrayList<>());

        String details = ZipTestUtil.readEntryAsString(Files.readAllBytes(exportedArchive), "Exercise-Details-" + programmingExercise.getTitle() + ".json");
        assertThat(JsonObjectMapper.get().readTree(details).get("gradingCriteria").size()).as("both stored criteria are exported").isEqualTo(2);

        // the import reads the same file, so the second criterion has to survive the request binding and the database
        var importRequest = JsonObjectMapper.get().readValue(details, ImportProgrammingExerciseRequestDTO.class);
        assertThat(importRequest.gradingCriteria()).as("both criteria survive the request binding").hasSize(2);
        var importedCriteria = importRequest.toEntity().getGradingCriteria();
        assertThat(importedCriteria).as("both criteria become their own entity").hasSize(2);

        importedCriteria.forEach(criterion -> criterion.setExercise(programmingExercise));
        gradingCriterionRepository.saveAll(importedCriteria);
        assertThat(gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(programmingExercise.getId())).as("both imported criteria are stored as their own row")
                .hasSize(4);
    }

    private static GradingCriterion criterionWithInstruction() {
        GradingCriterion criterion = new GradingCriterion();
        criterion.setTitle("same title");
        GradingInstruction instruction = new GradingInstruction();
        instruction.setCredits(1.0);
        instruction.setGradingScale("good");
        instruction.setInstructionDescription("same description");
        instruction.setFeedback("same feedback");
        instruction.setUsageCount(1);
        criterion.addStructuredGradingInstruction(instruction);
        return criterion;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportProgrammingExerciseForArchival_writesTheInstructorAndTheStudentRepositoriesIntoTheGivenDirectory() throws Exception {
        createAndSeedBaseRepositories();
        programmingExercise.setProblemStatement("Implement the sorting strategies.");
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        seedStudentParticipations(TEST_PREFIX + "student1");
        Path exportDir = tempFileUtilService.createTempDirectory("archival");
        List<String> exportErrors = new ArrayList<>();
        List<ArchivalReportEntry> archivalReportEntries = new ArrayList<>();

        Optional<Path> exportedExercise = programmingExerciseExportService.exportProgrammingExerciseForArchival(programmingExercise, exportErrors, Optional.of(exportDir),
                archivalReportEntries);

        assertThat(exportErrors).as("a complete exercise archives without errors").isEmpty();
        assertThat(exportedExercise).as("archiving writes into the directory it was given").contains(exportDir);
        List<String> exportedFileNames = listFileNames(exportDir);
        assertThat(exportedFileNames).as("the three instructor repositories are archived").anyMatch(name -> name.contains("-exercise")).anyMatch(name -> name.contains("-solution"))
                .anyMatch(name -> name.contains("-tests"));
        assertThat(exportedFileNames).as("the student repository is archived as well").anyMatch(name -> name.contains(TEST_PREFIX + "student1"));
        assertThat(exportedFileNames).as("the problem statement is archived next to the repositories")
                .contains("Problem-Statement-" + programmingExercise.getSanitizedExerciseTitle() + ".md");
        assertThat(archivalReportEntries).as("the archive reports what it contained").isNotEmpty();
    }

    private static List<String> listFileNames(Path directory) throws IOException {
        try (var entries = Files.list(directory)) {
            return entries.map(path -> path.getFileName().toString()).toList();
        }
    }

    /**
     * Filtering late submissions moves the exported repository back to the last commit that was made before the
     * deadline, which is how an export for grading stays faithful to what was submitted in time.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportStudentRepositories_whenFilteringLateSubmissions_exportsTheStateOfTheCommitBeforeTheDeadline() throws Exception {
        var participation = (ProgrammingExerciseStudentParticipation) participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise,
                TEST_PREFIX + "student1");
        var repository = RepositoryExportTestUtil.seedStudentRepositoryForParticipation(localVCLocalCITestService, participation);
        var inTimeCommit = RepositoryExportTestUtil.writeFilesAndPush(repository, Map.of("src/Main.java", "public class Main {}"), "in time");
        RepositoryExportTestUtil.writeFilesAndPush(repository, Map.of("src/Late.java", "public class Late {}"), "after the deadline");
        participation = studentParticipationTestRepository.save(participation);
        Path outputDir = tempFileUtilService.createTempDirectory("export-filter-late");
        RepositoryExportOptionsDTO filterLateSubmissions = new RepositoryExportOptionsDTO(true, true, false, DEADLINE, false, false, false, false, false);
        List<String> exportErrors = new ArrayList<>();

        List<Path> exportedRepositories = programmingExerciseExportService.exportStudentRepositories(programmingExercise, List.of(participation),
                Map.of(participation.getId(), inTimeCommit.getName()), outputDir, exportErrors, filterLateSubmissions, RepositoryExportContent.WITH_HISTORY);

        assertThat(exportErrors).isEmpty();
        assertThat(exportedRepositories).hasSize(1);
        Path exportedRepository = exportedRepositories.getFirst();
        assertThat(exportedRepository).as("filtering rewrites the repository, so it is exported as a directory").isDirectory();
        assertThat(exportedRepository.resolve("src/Main.java")).as("what was submitted in time is exported").isRegularFile();
        assertThat(exportedRepository.resolve("src/Late.java")).as("what was submitted after the deadline is not").doesNotExist();
    }
}
