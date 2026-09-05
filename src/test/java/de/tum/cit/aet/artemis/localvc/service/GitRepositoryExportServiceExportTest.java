package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.localvc.service.GitRepositoryExportService.RepositoryExportContent;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;

/**
 * Unit tests for writing a repository out as a directory or a zip file.
 * <p>
 * These exports end up in course archives and in the bundles an instructor downloads, so two properties matter beyond
 * the content itself. An export that fails must not leave a half-written archive behind under the name a later step
 * looks for, because a truncated zip inside an archive is far harder to diagnose than a missing one. And the name of an
 * anonymized export must not identify the student, since that is the only thing standing between a double-blind export
 * and the participant it came from.
 */
@ExtendWith(MockitoExtension.class)
class GitRepositoryExportServiceExportTest {

    @TempDir
    Path baseDir;

    @Mock
    private GitService gitService;

    private GitRepositoryExportService exportService;

    private Path bareRepositoryPath;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() throws Exception {
        exportService = new GitRepositoryExportService(gitService);
        Course course = new Course();
        course.setShortName("course1");
        exercise = new ProgrammingExercise();
        exercise.setId(7L);
        exercise.setTitle("Sorting Algorithms");
        exercise.setCourse(course);
    }

    /**
     * A bare repository with one commit, which is what the exports read from.
     */
    private Repository bareRepositoryWithACommit() throws Exception {
        bareRepositoryPath = baseDir.resolve("bare.git");
        Files.createDirectories(bareRepositoryPath);
        Git.init().setDirectory(bareRepositoryPath.toFile()).setBare(true).setInitialBranch("main").call().close();
        Path seed = baseDir.resolve("seed");
        try (Git clone = Git.cloneRepository().setURI(bareRepositoryPath.toUri().toString()).setDirectory(seed.toFile()).call()) {
            FileUtils.write(seed.resolve("Main.java").toFile(), "public class Main {}", StandardCharsets.UTF_8);
            clone.add().addFilepattern(".").call();
            var ident = new PersonIdent("Artemis", "artemis@example.com");
            GitService.commit(clone).setMessage("initial").setAuthor(ident).setCommitter(ident).call();
            clone.push().setRefSpecs(new RefSpec("HEAD:refs/heads/main")).call();
        }
        FileUtils.deleteDirectory(seed.toFile());
        return openBare();
    }

    private Repository openBare() throws Exception {
        var repository = new Repository(bareRepositoryPath.toString(), new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "abc-student"));
        ReflectionTestUtils.setField(repository, "localPath", bareRepositoryPath);
        return repository;
    }

    private void withBareRepository() throws Exception {
        Repository bare = bareRepositoryWithACommit();
        when(gitService.getBareRepository(any(LocalVCRepositoryUri.class), anyBoolean())).thenAnswer(invocation -> bare);
    }

    private LocalVCRepositoryUri repositoryUri() {
        return new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "abc-student");
    }

    // --- exporting to a zip ----------------------------------------------------------------------------------------

    @Test
    void exportingToAZipWritesTheWorkingTreeOfTheRepository() throws Exception {
        withBareRepository();
        Path target = baseDir.resolve("out");

        Path zip = exportService.exportRepositoryToZipFile(repositoryUri(), target, "export", RepositoryExportContent.WORKING_TREE_ONLY);

        assertThat(zip).exists();
        try (var zipFile = new ZipFile(zip.toFile())) {
            assertThat(zipFile.stream().map(java.util.zip.ZipEntry::getName)).contains("Main.java");
        }
    }

    @Test
    void exportingToAZipAddsTheExtensionWhenTheCallerLeftItOut() throws Exception {
        withBareRepository();

        Path zip = exportService.exportRepositoryToZipFile(repositoryUri(), baseDir.resolve("out"), "export", RepositoryExportContent.WORKING_TREE_ONLY);

        assertThat(zip.getFileName().toString()).isEqualTo("export.zip");
    }

    @Test
    void exportingToAZipDoesNotAddTheExtensionTwice() throws Exception {
        withBareRepository();

        Path zip = exportService.exportRepositoryToZipFile(repositoryUri(), baseDir.resolve("out"), "export.zip", RepositoryExportContent.WORKING_TREE_ONLY);

        assertThat(zip.getFileName().toString()).isEqualTo("export.zip");
    }

    @Test
    void exportingToAZipRemovesTheWhitespaceOfTheRequestedName() throws Exception {
        // The name ends up as a file inside another archive, where spaces make the entry awkward to work with.
        withBareRepository();

        Path zip = exportService.exportRepositoryToZipFile(repositoryUri(), baseDir.resolve("out"), "my export", RepositoryExportContent.WORKING_TREE_ONLY);

        assertThat(zip.getFileName().toString()).as("no whitespace survives into the entry name").doesNotContainAnyWhitespaces().isEqualTo("my_export.zip");
    }

    @Test
    void exportingToAZipWithTheHistoryProducesAnArchiveThatIsStillARepository() throws Exception {
        withBareRepository();

        Path zip = exportService.exportRepositoryToZipFile(repositoryUri(), baseDir.resolve("out"), "export", RepositoryExportContent.WITH_HISTORY);

        try (var zipFile = new ZipFile(zip.toFile())) {
            var names = zipFile.stream().map(java.util.zip.ZipEntry::getName).toList();
            assertThat(names).contains("Main.java");
            assertThat(names).as("the archive carries the git metadata, so it can be cloned again").anyMatch(name -> name.startsWith(".git"));
        }
    }

    @Test
    void anExportOfARepositoryWithoutCommitsIsReportedRatherThanWrittenEmpty() throws Exception {
        // An empty archive is indistinguishable from a student who submitted nothing, so it must not be produced silently.
        bareRepositoryPath = baseDir.resolve("empty.git");
        Files.createDirectories(bareRepositoryPath);
        Git.init().setDirectory(bareRepositoryPath.toFile()).setBare(true).setInitialBranch("main").call().close();
        Repository empty = openBare();
        when(gitService.getBareRepository(any(LocalVCRepositoryUri.class), anyBoolean())).thenReturn(empty);

        assertThatExceptionOfType(IOException.class)
                .isThrownBy(() -> exportService.exportRepositoryToZipFile(repositoryUri(), baseDir.resolve("out"), "export", RepositoryExportContent.WORKING_TREE_ONLY));
    }

    @Test
    void aFailedExportLeavesNoArchiveBehindAtAll() throws Exception {
        // Neither under the final name nor under the staging name: a later step zips the whole directory, and a truncated
        // archive inside it is far harder to diagnose than a repository that is simply missing.
        bareRepositoryPath = baseDir.resolve("empty.git");
        Files.createDirectories(bareRepositoryPath);
        Git.init().setDirectory(bareRepositoryPath.toFile()).setBare(true).setInitialBranch("main").call().close();
        when(gitService.getBareRepository(any(LocalVCRepositoryUri.class), anyBoolean())).thenReturn(openBare());
        Path target = baseDir.resolve("out");

        assertThatExceptionOfType(IOException.class)
                .isThrownBy(() -> exportService.exportRepositoryToZipFile(repositoryUri(), target, "export", RepositoryExportContent.WORKING_TREE_ONLY));

        try (var entries = Files.list(target)) {
            assertThat(entries).as("no archive and no staging file survives a failed export").isEmpty();
        }
    }

    // --- exporting to a directory ----------------------------------------------------------------------------------

    @Test
    void exportingToADirectoryMaterializesTheWorkingTree() throws Exception {
        withBareRepository();
        Path target = baseDir.resolve("out");

        Path directory = exportService.exportRepositoryToDirectory(repositoryUri(), target, "abc-student");

        assertThat(directory.resolve("Main.java")).content(StandardCharsets.UTF_8).isEqualTo("public class Main {}");
    }

    @Test
    void exportingToADirectoryReplacesWhatAnEarlierRunLeftBehind() throws Exception {
        // A staging directory of an earlier attempt would otherwise be written into, and a file that is no longer in the
        // repository would survive into the published export.
        withBareRepository();
        Path target = Files.createDirectories(baseDir.resolve("out"));
        Path leftover = Files.createDirectories(target.resolve("abc-student.partial-export"));
        FileUtils.write(leftover.resolve("Removed.java").toFile(), "from an earlier run", StandardCharsets.UTF_8);

        Path directory = exportService.exportRepositoryToDirectory(repositoryUri(), target, "abc-student");

        assertThat(directory.resolve("Removed.java")).doesNotExist();
        assertThat(directory.resolve("Main.java")).exists();
    }

    // --- the names an export is published under --------------------------------------------------------------------

    private static ProgrammingExerciseStudentParticipation participationOf(String login) {
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(42L);
        var student = new User();
        student.setLogin(login);
        participation.setParticipant(student);
        return participation;
    }

    @Test
    void theNameOfAStudentExportCarriesTheirLogin() {
        var name = exportService.getStudentRepositoryName(exercise, participationOf("ge12abc"), false);

        assertThat(name).contains("course1").contains("42").endsWith("ge12abc");
    }

    @Test
    void theNameOfAnAnonymizedExportDoesNotIdentifyTheStudent() {
        // This name is all that separates a double-blind export from the participant it came from.
        var participation = participationOf("ge12abc");

        var name = exportService.getStudentRepositoryName(exercise, participation, true);

        assertThat(name).doesNotContain("ge12abc").endsWith("-student-submission.git");
    }

    @Test
    void theNameOfAnExportOfAParticipationWithoutAParticipantStillIdentifiesTheParticipation() {
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(42L);

        var name = exportService.getStudentRepositoryName(exercise, participation, false);

        assertThat(name).contains("student-submission42");
    }

    @Test
    void theNameOfAZippedRepositoryIsMadeSafeForAFileSystem() {
        exercise.setTitle("Sorting/Algorithms: part 1");

        var name = exportService.getZippedRepoName(exercise, "tests");

        assertThat(name).doesNotContain("/").doesNotContain(":").contains("course1").contains("tests");
    }

    @Test
    void copyingARepositoryForAnExportNamesTheCopyAfterTheParticipation() throws Exception {
        Path workingCopy = Files.createDirectories(baseDir.resolve("checkout"));
        FileUtils.write(workingCopy.resolve("Main.java").toFile(), "public class Main {}", StandardCharsets.UTF_8);
        var repository = new Repository(workingCopy.resolve(".git").toString(), repositoryUri());
        ReflectionTestUtils.setField(repository, "localPath", workingCopy);
        var participation = participationOf("ge12abc");
        participation.setProgrammingExercise(exercise);
        repository.setParticipation(participation);
        Path exportDirectory = Files.createDirectories(baseDir.resolve("export"));

        Path copied = exportService.getRepositoryWithParticipation(repository, exportDirectory.toString(), false);

        assertThat(copied.getFileName().toString()).endsWith("ge12abc");
        assertThat(copied.resolve("Main.java")).content(StandardCharsets.UTF_8).isEqualTo("public class Main {}");
    }

    @Test
    void copyingARepositoryForAnAnonymizedExportNamesTheCopyWithoutTheStudent() throws Exception {
        Path workingCopy = Files.createDirectories(baseDir.resolve("checkout"));
        FileUtils.write(workingCopy.resolve("Main.java").toFile(), "public class Main {}", StandardCharsets.UTF_8);
        var repository = new Repository(workingCopy.resolve(".git").toString(), repositoryUri());
        ReflectionTestUtils.setField(repository, "localPath", workingCopy);
        var participation = participationOf("ge12abc");
        participation.setProgrammingExercise(exercise);
        repository.setParticipation(participation);
        Path exportDirectory = Files.createDirectories(baseDir.resolve("export"));

        Path copied = exportService.getRepositoryWithParticipation(repository, exportDirectory.toString(), true);

        assertThat(copied.getFileName().toString()).doesNotContain("ge12abc");
    }

    // --- exporting into memory -------------------------------------------------------------------------------------

    @Test
    void exportingASnapshotIntoMemoryYieldsAZipNamedAfterTheRequest() throws Exception {
        withBareRepository();

        var resource = exportService.exportRepositorySnapshot(repositoryUri(), "snapshot");

        assertThat(resource.getFilename()).isEqualTo("snapshot.zip");
        assertThat(resource.contentLength()).isPositive();
    }

    @Test
    void exportingWithTheFullHistoryIntoMemoryYieldsAZipNamedAfterTheRequest() throws Exception {
        withBareRepository();

        var resource = exportService.exportRepositoryWithFullHistoryToMemory(repositoryUri(), "with-history");

        assertThat(resource.getFilename()).isEqualTo("with-history.zip");
        assertThat(resource.contentLength()).isPositive();
    }

    @Test
    void createInMemoryZipArchive_containsTheFilesOfTheHeadCommit() throws Exception {
        Repository bare = bareRepositoryWithACommit();

        byte[] archive = exportService.createInMemoryZipArchive(bare);

        Path written = baseDir.resolve("archive.zip");
        FileUtils.writeByteArrayToFile(written.toFile(), archive);
        try (var zipFile = new ZipFile(written.toFile())) {
            assertThat(zipFile.stream().map(java.util.zip.ZipEntry::getName)).contains("Main.java");
        }
    }

    @Test
    void exportingAnInstructorRepositoryUsesTheNameOfTheExerciseAndTheRepositoryType() throws Exception {
        withBareRepository();
        exercise.setTemplateParticipation(new de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation());
        exercise.setSolutionParticipation(new de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation());
        exercise.setTestRepositoryUri("https://artemis.example.com/git/ABC/abc-tests.git");

        var resource = exportService.exportInstructorRepositoryForExerciseInMemory(exercise, de.tum.cit.aet.artemis.programming.domain.RepositoryType.TESTS);

        assertThat(resource.getFilename()).contains("course1").contains("tests").endsWith(".zip");
    }

    @Test
    void exportingAnAuxiliaryRepositoryUsesItsName() throws Exception {
        withBareRepository();
        var auxiliaryRepository = new de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository();
        auxiliaryRepository.setName("helpers");
        auxiliaryRepository.setRepositoryUri("https://artemis.example.com/git/ABC/abc-helpers.git");
        auxiliaryRepository.setExercise(exercise);
        exercise.setAuxiliaryRepositories(List.of(auxiliaryRepository));

        var resource = exportService.exportInstructorAuxiliaryRepositoryForExerciseInMemory(exercise, auxiliaryRepository);

        assertThat(resource.getFilename()).contains("helpers").endsWith(".zip");
    }
}
