package de.tum.cit.aet.artemis.buildagent.service.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

class KubernetesBuildArchiveServiceTest {

    @TempDir
    private Path temporaryDirectory;

    private Path archiveDirectory;

    private KubernetesBuildArchiveService archiveService;

    @BeforeEach
    void setUp() {
        archiveDirectory = temporaryDirectory.resolve("archives");
        archiveService = new KubernetesBuildArchiveService(new TempFileUtilService(archiveDirectory));
    }

    @Test
    void createsArchiveWithRepositoriesAndExecutableScript() throws Exception {
        Path assignment = repository("assignment", "src/main.c", "int main() { return 0; }");
        Path tests = repository("tests", "test/test.c", "test");
        Path solution = repository("solution", "solution.c", "solution");
        Path auxiliary = repository("auxiliary", "data/value.txt", "42");
        var preparedJob = new PreparedBuildJob(assignment, tests, solution, List.of(auxiliary));

        Path archive = archiveService.createInputArchive(buildJob("student", "tests", "solution", new String[] { "fixtures" }), preparedJob);
        Map<String, byte[]> entries = readEntries(archive);

        assertThat(entries).containsKeys("testing-dir/student/src/main.c", "testing-dir/tests/test/test.c", "testing-dir/solution/solution.c",
                "testing-dir/fixtures/data/value.txt", "script.sh");
        assertThat(new String(entries.get("script.sh"), StandardCharsets.UTF_8)).isEqualTo("echo hello");
        assertThat(Files.size(archive)).isGreaterThan(0);
        Files.delete(archive);
    }

    @Test
    void rejectsPathsThatCouldEscapeTheWorkspace() {
        assertThatThrownBy(() -> KubernetesBuildArchiveService.validateRelativePath("../outside")).isInstanceOf(LocalCIException.class);
        assertThatThrownBy(() -> KubernetesBuildArchiveService.validateRelativePath("/absolute")).isInstanceOf(LocalCIException.class);
        assertThatThrownBy(() -> KubernetesBuildArchiveService.validateRelativePath("path with spaces")).isInstanceOf(LocalCIException.class);
    }

    @Test
    void rejectsMismatchedAuxiliaryRepositoryConfiguration() throws Exception {
        Path assignment = repository("assignment", "main.c", "main");
        Path tests = repository("tests", "test.c", "test");
        var preparedJob = new PreparedBuildJob(assignment, tests, null, List.of(repository("auxiliary", "data.txt", "data")));
        String buildJobId = "mismatched-" + UUID.randomUUID();

        assertThat(temporaryArchives(buildJobId)).isEmpty();
        assertThatThrownBy(() -> archiveService.createInputArchive(buildJob(buildJobId, "student", "tests", "solution", new String[0]), preparedJob))
                .isInstanceOf(LocalCIException.class).hasMessageContaining("number of auxiliary repositories");
        assertThat(temporaryArchives(buildJobId)).isEmpty();
    }

    @Test
    void checksOutRepositoriesIntoTheLanguageDefaultPathsWhenTheExerciseDoesNotCustomiseThem() throws Exception {
        Path assignment = repository("assignment", "Main.hs", "main");
        Path tests = repository("tests", "Spec.hs", "spec");
        Path solution = repository("solution", "Solution.hs", "solution");
        var preparedJob = new PreparedBuildJob(assignment, tests, solution, List.of());

        Path archive = archiveService.createInputArchive(buildJob(null, null, null, new String[0]), preparedJob);
        Map<String, byte[]> entries = readEntries(archive);

        // The Haskell defaults are "assignment" and "solution", while the test repository has no default subdirectory and is checked out into the working directory itself.
        assertThat(entries).containsKeys("testing-dir/assignment/Main.hs", "testing-dir/Spec.hs", "testing-dir/solution/Solution.hs", "script.sh");
        Files.delete(archive);
    }

    @Test
    void skipsSymbolicLinksThatPointOutsideTheRepository() throws Exception {
        Path assignment = repository("assignment", "src/main.c", "main");
        Path outside = repository("outside", "secret.txt", "secret");
        Files.createSymbolicLink(assignment.resolve("escape"), outside);
        Files.createSymbolicLink(assignment.resolve("root-link"), Path.of("/"));
        Path tests = repository("tests", "test.c", "test");

        Path archive = archiveService.createInputArchive(buildJob("student", "tests", "solution", new String[0]), new PreparedBuildJob(assignment, tests, null, List.of()));

        Map<String, byte[]> entries = readEntries(archive);
        assertThat(entries).containsKey("testing-dir/student/src/main.c");
        assertThatThrownBy(() -> readEntry(archive, "testing-dir/student/escape")).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> readEntry(archive, "testing-dir/student/root-link")).isInstanceOf(AssertionError.class);
        Files.delete(archive);
    }

    @Test
    void preservesRepositorySymbolicLinks() throws Exception {
        Path assignment = repository("assignment", "src/main.c", "main");
        Files.createSymbolicLink(assignment.resolve("main-link.c"), Path.of("src/main.c"));
        Path tests = repository("tests", "test.c", "test");

        Path archive = archiveService.createInputArchive(buildJob("student", "tests", "solution", new String[0]), new PreparedBuildJob(assignment, tests, null, List.of()));

        ArchiveEntry link = readEntry(archive, "testing-dir/student/main-link.c");
        assertThat(link.symbolicLink()).isTrue();
        assertThat(link.linkName()).isEqualTo("src/main.c");
        assertThat(link.size()).isZero();
        Files.delete(archive);
    }

    private Path repository(String directory, String file, String content) throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve(directory));
        Path target = root.resolve(file);
        Files.createDirectories(target.getParent());
        FileUtils.writeStringToFile(target.toFile(), content, StandardCharsets.UTF_8);
        return root;
    }

    private static ArchiveEntry readEntry(Path archive, String entryName) throws Exception {
        try (InputStream input = Files.newInputStream(archive); TarArchiveInputStream tar = new TarArchiveInputStream(input)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    return new ArchiveEntry(entry.isSymbolicLink(), entry.getLinkName(), entry.getSize());
                }
            }
        }
        throw new AssertionError("Archive entry not found: " + entryName);
    }

    private static Map<String, byte[]> readEntries(Path archive) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (InputStream input = Files.newInputStream(archive); TarArchiveInputStream tar = new TarArchiveInputStream(input)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), tar.readAllBytes());
                }
            }
        }
        return entries;
    }

    private static BuildJobQueueItem buildJob(String assignmentPath, String testPath, String solutionPath, String[] auxiliaryPaths) {
        return buildJob("job", assignmentPath, testPath, solutionPath, auxiliaryPaths);
    }

    private static BuildJobQueueItem buildJob(String buildJobId, String assignmentPath, String testPath, String solutionPath, String[] auxiliaryPaths) {
        var repositoryInfo = new RepositoryInfo("repository", RepositoryType.USER, RepositoryType.USER, "assignment", "tests", "solution", new String[auxiliaryPaths.length],
                auxiliaryPaths);
        var timing = new JobTimingInfo(ZonedDateTime.now(), null, null, null, 60);
        var buildConfig = new BuildConfig("echo hello", "ubuntu:24.04", "commit", "assignment-commit", "test-commit", "main", ProgrammingLanguage.HASKELL, null, false, false,
                List.of(), 60, assignmentPath, testPath, solutionPath, new DockerRunConfig(List.of(), null, 0, 0, 0));
        return new BuildJobQueueItem(buildJobId, "job", new BuildAgentDTO(null, null, null), 1, 2, 3, 0, 1, null, repositoryInfo, timing, buildConfig, null);
    }

    private List<Path> temporaryArchives(String buildJobId) throws Exception {
        if (Files.notExists(archiveDirectory)) {
            return List.of();
        }
        try (var files = Files.list(archiveDirectory)) {
            return files.filter(path -> path.getFileName().toString().startsWith("artemis-localci-" + buildJobId + "-")).toList();
        }
    }

    private record ArchiveEntry(boolean symbolicLink, String linkName, long size) {
    }
}
