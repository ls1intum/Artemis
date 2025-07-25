package de.tum.cit.aet.artemis.programming.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTheiaConfigDTO;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.TemplateProgrammingExerciseParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
import de.tum.cit.aet.artemis.programming.util.LocalRepositoryUriUtil;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseParticipationUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ProgrammingExerciseResourceTest extends AbstractSpringIntegrationIndependentTest {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseResourceTest.class);

    private static final String TEST_PREFIX = "programmingexerciseresource";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    protected UserTestRepository userTestRepository;

    @Autowired
    protected ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    protected ProgrammingExerciseParticipationUtilService programmingExerciseParticipationUtilService;

    @Autowired
    protected ParticipationUtilService participationUtilService;

    @Autowired
    protected RequestUtilService request;

    @Autowired
    private TemplateProgrammingExerciseParticipationTestRepository templateProgrammingExerciseParticipationTestRepo;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private GitService gitService;

    protected Course course;

    protected ProgrammingExercise programmingExercise;

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    @BeforeEach
    void setup() {
        String studentParticipationGroupName = TEST_PREFIX + "studentParticipationGroup";

        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        Set<String> student1Groups = new HashSet<>(student1.getGroups());
        student1Groups.add(studentParticipationGroupName);
        student1.setGroups(student1Groups);
        userTestRepository.save(student1);

        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        course.setStudentGroupName(studentParticipationGroupName);
        course = courseRepository.save(course);

        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void testBuildConfigOnlyReturnsRestrictedSetOfInformation() throws Exception {
        ProgrammingExerciseTheiaConfigDTO imageDTO = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/theia-config", HttpStatus.OK,
                ProgrammingExerciseTheiaConfigDTO.class);

        // Count the number of fields in the record, this makes sure that only the expected fields are returned
        assertThat(imageDTO.getClass().getDeclaredFields().length).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testExportRepositoryMemory() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        course.setInstructorGroupName(instructor.getGroups().iterator().next());
        courseRepository.save(course);

        var localRepo = new LocalRepository(defaultBranch);
        var originRepoPath = java.nio.file.Files.createTempDirectory("testOriginRepo");
        localRepo.configureRepos(originRepoPath, "testLocalRepo", "testOriginRepo");

        programmingExercise = programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);

        var templateParticipation = templateProgrammingExerciseParticipationTestRepo.findByProgrammingExerciseId(programmingExercise.getId()).orElseThrow();
        templateParticipation
                .setRepositoryUri(new LocalVCRepositoryUri(LocalRepositoryUriUtil.convertToLocalVcUriString(localRepo.workingCopyGitRepoFile, originRepoPath)).getURI().toString());
        templateProgrammingExerciseParticipationTestRepo.save(templateParticipation);

        programmingExercise = programmingExerciseRepository.findByIdWithTemplateParticipationElseThrow(programmingExercise.getId());

        // Mock the getBareRepository call to return a proper repository
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(localRepo.workingCopyGitRepoFile.toPath(), null)).when(gitService).getBareRepository(any());

        byte[] result = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repository-snapshot/" + RepositoryType.TEMPLATE.name(),
                HttpStatus.OK, byte[].class);

        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);

        // Verify that file is a valid ZIP file
        assertThat(result[0]).isEqualTo((byte) 0x50); // 'P'
        assertThat(result[1]).isEqualTo((byte) 0x4B); // 'K'

        verifyZipStructureAndContent(result);

        // Clean up
        localRepo.resetLocalRepo();
    }

    private void verifyZipStructureAndContent(byte[] zipContent) throws Exception {
        boolean foundFiles = false;
        int fileCount = 0;
        Set<String> repositoryFiles = new HashSet<>();

        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipContent))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();

                if (!entry.isDirectory()) {
                    foundFiles = true;
                    fileCount++;
                    repositoryFiles.add(entryName);

                    // Validate that we can read the file content (ensures ZIP is not corrupted)
                    byte[] fileContent = zipInputStream.readAllBytes();
                    assertThat(fileContent).as("File content should be readable for: " + entryName).isNotNull();

                    // For text files, verify they contain reasonable content (allow empty files for test purposes)
                    if (entryName.endsWith(".java") || entryName.endsWith(".md") || entryName.endsWith(".xml")) {
                        String textContent = new String(fileContent);
                        assertThat(textContent).as("Text file should have actual content: " + entryName).isNotBlank();
                    }
                    // Note: .txt files can be empty in test repositories, so we don't validate their content
                }
            }
        }

        assertThat(foundFiles).as("ZIP should contain actual files, not just directories").isTrue();
        assertThat(fileCount).as("ZIP should contain at least one file from the repository").isGreaterThan(0);
        assertThat(repositoryFiles).as("Should have repository files").isNotEmpty();

        // Verify ZIP is substantial (not just empty structure)
        assertThat(zipContent.length).as("ZIP file should be substantial in size").isGreaterThan(100);

        // Verify filename structure is reasonable (no null filenames)
        for (String filename : repositoryFiles) {
            assertThat(filename).as("Filename should not be null or empty").isNotBlank();
            assertThat(filename).as("Filename should not contain invalid characters").doesNotContain("\0");
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testExportRepositoryWithFullHistory() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        course.setInstructorGroupName(instructor.getGroups().iterator().next());
        courseRepository.save(course);

        var localRepo = new LocalRepository(defaultBranch);
        var originRepoPath = java.nio.file.Files.createTempDirectory("testOriginRepo");
        localRepo.configureRepos(originRepoPath, "testLocalRepo", "testOriginRepo");

        programmingExercise = programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);

        var templateParticipation = templateProgrammingExerciseParticipationTestRepo.findByProgrammingExerciseId(programmingExercise.getId()).orElseThrow();
        templateParticipation
                .setRepositoryUri(new LocalVCRepositoryUri(LocalRepositoryUriUtil.convertToLocalVcUriString(localRepo.workingCopyGitRepoFile, originRepoPath)).getURI().toString());
        templateProgrammingExerciseParticipationTestRepo.save(templateParticipation);

        programmingExercise = programmingExerciseRepository.findByIdWithTemplateParticipationElseThrow(programmingExercise.getId());

        // Mock the getBareRepository and getOrCheckoutRepository calls
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(localRepo.workingCopyGitRepoFile.toPath(), null)).when(gitService).getBareRepository(any());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(localRepo.workingCopyGitRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(any(), any());

        byte[] result = request.get(
                "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repository-with-full-history/" + RepositoryType.TEMPLATE.name(), HttpStatus.OK,
                byte[].class);

        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);

        // Verify it's a valid ZIP file
        assertThat(result[0]).isEqualTo((byte) 0x50); // 'P'
        assertThat(result[1]).isEqualTo((byte) 0x4B); // 'K'

        // Verify that the zip contains the .git directory
        verifyZipContainsGitDirectory(result);

        // Clean up
        localRepo.resetLocalRepo();
    }

    private void verifyZipContainsGitDirectory(byte[] zipContent) throws Exception {
        boolean foundGitDirectory = false;
        boolean foundOtherFiles = false;
        boolean foundGitConfig = false;
        boolean foundGitHead = false;
        boolean foundGitRefs = false;
        boolean foundGitObjects = false;

        Set<String> gitFiles = new HashSet<>();
        Set<String> repositoryFiles = new HashSet<>();

        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipContent))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();

                if (entryName.contains(".git/")) {
                    foundGitDirectory = true;
                    gitFiles.add(entryName);

                    // Check for specific important git files
                    if (entryName.endsWith(".git/config")) {
                        foundGitConfig = true;
                        // Validate git config content
                        String configContent = new String(zipInputStream.readAllBytes());
                        assertThat(configContent).as("Git config should contain repository information").containsAnyOf("[core]", "[remote", "repositoryformatversion");
                    }
                    else if (entryName.endsWith(".git/HEAD")) {
                        foundGitHead = true;
                        // Validate HEAD content
                        String headContent = new String(zipInputStream.readAllBytes());
                        assertThat(headContent).as("Git HEAD should reference a branch").containsAnyOf("ref: refs/heads/", "refs/heads/main", "refs/heads/master");
                    }
                    else if (entryName.contains(".git/refs/")) {
                        foundGitRefs = true;
                    }
                    else if (entryName.contains(".git/objects/")) {
                        foundGitObjects = true;
                    }
                }
                else if (!entryName.endsWith("/")) {
                    foundOtherFiles = true;
                    repositoryFiles.add(entryName);
                }
            }
        }

        // Assertions for git directory structure
        assertThat(foundGitDirectory).as("Zip should contain .git directory files").isTrue();
        assertThat(foundGitConfig).as("Zip should contain .git/config file").isTrue();
        assertThat(foundGitHead).as("Zip should contain .git/HEAD file").isTrue();
        assertThat(foundGitRefs).as("Zip should contain .git/refs/ directory with references").isTrue();
        assertThat(foundGitObjects).as("Zip should contain .git/objects/ directory with git objects").isTrue();

        // Assertions for repository content
        assertThat(foundOtherFiles).as("Zip should contain other repository files").isTrue();

        // Additional validations
        assertThat(gitFiles).as("Should have multiple git-related files").hasSizeGreaterThan(3);
        assertThat(repositoryFiles).as("Should have some repository files").isNotEmpty();

        // Log the found files for debugging
        log.info("Found git files: " + gitFiles.size());
        log.info("Found repository files: " + repositoryFiles.size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testExportRepositoryBundle() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        course.setInstructorGroupName(instructor.getGroups().iterator().next());
        courseRepository.save(course);

        var localRepo = new LocalRepository(defaultBranch);
        var originRepoPath = java.nio.file.Files.createTempDirectory("testOriginRepo");
        localRepo.configureRepos(originRepoPath, "testLocalRepo", "testOriginRepo");

        programmingExercise = programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);

        var templateParticipation = templateProgrammingExerciseParticipationTestRepo.findByProgrammingExerciseId(programmingExercise.getId()).orElseThrow();
        templateParticipation
                .setRepositoryUri(new LocalVCRepositoryUri(LocalRepositoryUriUtil.convertToLocalVcUriString(localRepo.workingCopyGitRepoFile, originRepoPath)).getURI().toString());
        templateProgrammingExerciseParticipationTestRepo.save(templateParticipation);

        programmingExercise = programmingExerciseRepository.findByIdWithTemplateParticipationElseThrow(programmingExercise.getId());

        // Mock the getBareRepository call to return a proper repository
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(localRepo.workingCopyGitRepoFile.toPath(), null)).when(gitService).getBareRepository(any());

        byte[] result = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repository-bundle/" + RepositoryType.TEMPLATE.name(),
                HttpStatus.OK, byte[].class);

        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);

        // Git bundles start with specific signature
        // Check for Git bundle signature "# v2 git bundle"
        String bundleHeader = new String(result, 0, Math.min(result.length, 20));
        assertThat(bundleHeader).startsWith("# v2 git bundle");

        // Additional validation: check that bundle contains more than just header
        assertThat(result.length).isGreaterThan(100); // Bundle should be substantial

        // Clean up
        localRepo.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testExportRepositoryBundleWithHistory() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        course.setInstructorGroupName(instructor.getGroups().iterator().next());
        courseRepository.save(course);

        var localRepo = new LocalRepository(defaultBranch);
        var originRepoPath = java.nio.file.Files.createTempDirectory("testOriginRepo");
        localRepo.configureRepos(originRepoPath, "testLocalRepo", "testOriginRepo");

        // Add multiple commits to create history
        createAndCommitFile(localRepo, "file1.txt", "Initial content", "Initial commit");
        createAndCommitFile(localRepo, "file2.txt", "Second file", "Second commit");
        createAndCommitFile(localRepo, "file1.txt", "Modified content", "Third commit");

        programmingExercise = programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);

        var templateParticipation = templateProgrammingExerciseParticipationTestRepo.findByProgrammingExerciseId(programmingExercise.getId()).orElseThrow();
        templateParticipation
                .setRepositoryUri(new LocalVCRepositoryUri(LocalRepositoryUriUtil.convertToLocalVcUriString(localRepo.workingCopyGitRepoFile, originRepoPath)).getURI().toString());
        templateProgrammingExerciseParticipationTestRepo.save(templateParticipation);

        programmingExercise = programmingExerciseRepository.findByIdWithTemplateParticipationElseThrow(programmingExercise.getId());

        // Mock the getBareRepository call to return a proper repository
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(localRepo.workingCopyGitRepoFile.toPath(), null)).when(gitService).getBareRepository(any());

        byte[] result = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repository-bundle/" + RepositoryType.TEMPLATE.name(),
                HttpStatus.OK, byte[].class);

        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);

        // Git bundles start with specific signature
        String bundleHeader = new String(result, 0, Math.min(result.length, 20));
        assertThat(bundleHeader).startsWith("# v2 git bundle");

        // Bundle should be larger when it contains history
        assertThat(result.length).isGreaterThan(200); // Should be substantial with history

        // Clean up
        localRepo.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void testRepositoryExportPerformance() throws Exception {
        final int PERFORMANCE_TEST_ITERATIONS = 30;

        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        course.setInstructorGroupName(instructor.getGroups().iterator().next());
        courseRepository.save(course);

        var localRepo = new LocalRepository(defaultBranch);
        var originRepoPath = Files.createTempDirectory("testOriginRepo");
        localRepo.configureRepos(originRepoPath, "testLocalRepo", "testOriginRepo");

        createTestRepositoryContent(localRepo);

        programmingExercise = programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);

        var templateParticipation = templateProgrammingExerciseParticipationTestRepo.findByProgrammingExerciseId(programmingExercise.getId()).orElseThrow();
        templateParticipation
                .setRepositoryUri(new LocalVCRepositoryUri(LocalRepositoryUriUtil.convertToLocalVcUriString(localRepo.workingCopyGitRepoFile, originRepoPath)).getURI().toString());
        templateProgrammingExerciseParticipationTestRepo.save(templateParticipation);

        programmingExercise = programmingExerciseRepository.findByIdWithTemplateParticipationElseThrow(programmingExercise.getId());

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(localRepo.workingCopyGitRepoFile.toPath(), null)).when(gitService).getBareRepository(any());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(localRepo.workingCopyGitRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(any(), any());

        var snapshotStats = testEndpointWithWarmup("Repository Snapshot",
                "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repository-snapshot/" + RepositoryType.TEMPLATE.name(),
                PERFORMANCE_TEST_ITERATIONS);

        var fullHistoryStats = testEndpointWithWarmup("Repository with Full History",
                "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repository-with-full-history/" + RepositoryType.TEMPLATE.name(),
                PERFORMANCE_TEST_ITERATIONS);

        var bundleStats = testEndpointWithWarmup("Repository Bundle",
                "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repository-bundle/" + RepositoryType.TEMPLATE.name(),
                PERFORMANCE_TEST_ITERATIONS);

        generateSimpleReport(snapshotStats, fullHistoryStats, bundleStats, PERFORMANCE_TEST_ITERATIONS);

        assertThat(fullHistoryStats.avgFileSize).isGreaterThan(snapshotStats.avgFileSize);

        localRepo.resetLocalRepo();
    }

    private void createTestRepositoryContent(LocalRepository localRepo) throws Exception {
        createAndCommitFile(localRepo, "README.md", "# Test Repository\nThis is a test repository for performance testing.\n", "Initial README");
        createAndCommitFile(localRepo, "src/main/java/Main.java",
                "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Hello World!\");\n    }\n}\n", "Add Main class");
        createAndCommitFile(localRepo, "config/application.properties", "config.property1=value1\nconfig.property2=value2\n", "Add configuration");
    }

    private SimpleStats testEndpointWithWarmup(String endpointName, String url, int iterations) throws Exception {
        // Warmup phase - 3 calls to eliminate first-call overhead
        log.info("Warming up {} endpoint (3 rounds)...", endpointName);
        for (int i = 0; i < 3; i++) {
            request.get(url, HttpStatus.OK, byte[].class);
        }
        log.info("Warmup completed for {}", endpointName);

        List<Long> times = new ArrayList<>();
        List<Long> sizes = new ArrayList<>();

        log.info("Testing {} endpoint ({} iterations)...", endpointName, iterations);
        for (int i = 0; i < iterations; i++) {
            long startTime = System.nanoTime();
            byte[] result = request.get(url, HttpStatus.OK, byte[].class);
            long endTime = System.nanoTime();

            times.add((endTime - startTime) / 1_000_000); // Convert to milliseconds
            sizes.add((long) result.length);

            assertThat(result).isNotNull();
            assertThat(result.length).isGreaterThan(0);
        }

        var timeStats = times.stream().mapToLong(Long::longValue).summaryStatistics();
        var sizeStats = sizes.stream().mapToLong(Long::longValue).summaryStatistics();

        return new SimpleStats(endpointName, timeStats.getMin(), timeStats.getMax(), timeStats.getAverage(), sizeStats.getAverage());
    }

    private void generateSimpleReport(SimpleStats snapshot, SimpleStats fullHistory, SimpleStats bundle, int iterations) throws IOException {
        List<String> report = new ArrayList<>();

        report.add("Repository Export Performance Test Report");
        report.add("Iterations: " + iterations);
        report.add("=" + "=".repeat(50));
        report.add("");

        addSimpleResults(report, snapshot);
        addSimpleResults(report, fullHistory);
        addSimpleResults(report, bundle);

        // Create build/reports/performance-test directory if it doesn't exist
        Path reportsDir = Path.of("build", "reports", "performance-test");
        FileUtils.forceMkdir(reportsDir.toFile());

        // Generate timestamp for unique filename
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path reportPath = reportsDir.resolve("export_performance_report_" + timestamp + ".txt");
        FileUtils.writeLines(reportPath.toFile(), report);

        log.info("Performance report written to: {}", reportPath.toAbsolutePath());
        log.info(String.format("%s: %.1fms avg, %.1fKB avg", snapshot.name, snapshot.avgTime, snapshot.avgFileSize / 1024.0));
        log.info(String.format("%s: %.1fms avg, %.1fKB avg", fullHistory.name, fullHistory.avgTime, fullHistory.avgFileSize / 1024.0));
        log.info(String.format("%s: %.1fms avg, %.1fKB avg", bundle.name, bundle.avgTime, bundle.avgFileSize / 1024.0));
    }

    private void addSimpleResults(List<String> report, SimpleStats stats) {
        report.add(stats.name + ":");
        report.add(String.format("  Time: min=%dms, max=%dms, avg=%.1fms", stats.minTime, stats.maxTime, stats.avgTime));
        report.add(String.format("  Size: avg=%.1fKB", stats.avgFileSize / 1024.0));
        report.add("");
    }

    private static class SimpleStats {

        final String name;

        final long minTime, maxTime;

        final double avgTime, avgFileSize;

        SimpleStats(String name, long minTime, long maxTime, double avgTime, double avgFileSize) {
            this.name = name;
            this.minTime = minTime;
            this.maxTime = maxTime;
            this.avgTime = avgTime;
            this.avgFileSize = avgFileSize;
        }
    }

    private void createAndCommitFile(LocalRepository localRepository, String filename, String content, String commitMessage) throws Exception {
        var file = Path.of(localRepository.workingCopyGitRepoFile.toPath().toString(), filename);
        FileUtils.writeStringToFile(file.toFile(), content, "UTF-8");
        localRepository.workingCopyGitRepo.add().addFilepattern(filename).call();
        GitService.commit(localRepository.workingCopyGitRepo).setMessage(commitMessage).call();
    }
}
