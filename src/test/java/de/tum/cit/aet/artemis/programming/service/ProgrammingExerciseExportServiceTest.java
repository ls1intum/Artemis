package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Constants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.dto.RepositoryExportOptionsDTO;
import de.tum.cit.aet.artemis.core.service.ArchivalReportEntry;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.localci.service.LocalVCLocalCITestService;
import de.tum.cit.aet.artemis.localvc.service.GitRepositoryExportService.RepositoryExportContent;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
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
     * asks for, so those callers keep the checkout and the directory layout they have always produced.
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
        }
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
        var workingCopy = templateRepository.workingCopyGitRepo;
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
}
