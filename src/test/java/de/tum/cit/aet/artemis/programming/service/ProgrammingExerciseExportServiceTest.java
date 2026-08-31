package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
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
                ARCHIVAL_OPTIONS);

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
                ARCHIVAL_OPTIONS);

        assertThat(exportedRepositories).hasSize(1);
        byte[] zipContent = Files.readAllBytes(exportedRepositories.getFirst());
        ZipTestUtil.verifyZipDoesNotContainGitDirectory(zipContent);
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
                exportErrors, ARCHIVAL_OPTIONS);

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
                ARCHIVAL_OPTIONS);

        assertThat(exportedRepositories.getFirst().getFileName().toString()).contains(TEST_PREFIX + "student1");
    }

    /**
     * The exercise export puts one zip per repository into the output directory. Instructor repositories keep their
     * history so the exported material can be re-imported and inspected, which is what the import expects to find.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportProgrammingExerciseRepositories_shouldWriteOneZipPerRepositoryWithHistory() throws Exception {
        RepositoryExportTestUtil.createAndWireBaseRepositoriesWithHandles(localVCLocalCITestService, programmingExercise);
        seedBaseRepositoryContent();
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
     * The only remote URL available at export time is the server's own path to the bare repository, which the archive
     * must not disclose and which is unusable on the machine that extracts it.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportProgrammingExerciseRepositories_shouldNotDiscloseTheInternalRepositoryPath() throws Exception {
        RepositoryExportTestUtil.createAndWireBaseRepositoriesWithHandles(localVCLocalCITestService, programmingExercise);
        seedBaseRepositoryContent();
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

    private void seedBaseRepositoryContent() throws Exception {
        var baseRepositories = RepositoryExportTestUtil.createAndWireBaseRepositoriesWithHandles(localVCLocalCITestService, programmingExercise);
        RepositoryExportTestUtil.writeFilesAndPush(baseRepositories.templateRepository(), Map.of("src/Main.java", "public class Main {}"), "template");
        RepositoryExportTestUtil.writeFilesAndPush(baseRepositories.solutionRepository(), Map.of("src/Main.java", "public class Main { int solved; }"), "solution");
        RepositoryExportTestUtil.writeFilesAndPush(baseRepositories.testsRepository(), Map.of("test/MainTest.java", "public class MainTest {}"), "tests");
    }

    /** Kept to document that the export must not fail when a repository URI was never configured (legacy courses). */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportProgrammingExerciseRepositories_shouldReportMissingRepositoryUrisAsErrors() throws IOException {
        programmingExercise.setTemplateParticipation(null);
        Path outputDir = tempFileUtilService.createTempDirectory("instructor-export-missing");
        List<String> exportErrors = new ArrayList<>();

        programmingExerciseExportService.exportProgrammingExerciseRepositories(programmingExercise, false, false, outputDir, exportErrors, new ArrayList<>());

        assertThat(exportErrors).isNotEmpty();
    }
}
