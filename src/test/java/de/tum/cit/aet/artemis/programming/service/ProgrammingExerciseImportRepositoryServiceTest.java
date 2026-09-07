package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;

/**
 * Unit tests for importing a programming exercise from an uploaded zip file.
 * <p>
 * The zip carries one directory per repository, and this is what puts their content into the repositories the new
 * exercise was created with. Two things about that are easy to get wrong and expensive when they are: copying the
 * {@code .git} folder out of the zip would overwrite the repository's own history with the one of the exercise it was
 * exported from, and a {@code gradlew} that arrives without its executable bit makes every build of the imported
 * exercise fail with a message that says nothing about the import.
 */
@ExtendWith(MockitoExtension.class)
class ProgrammingExerciseImportRepositoryServiceTest {

    @TempDir
    Path baseDir;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private GitService gitService;

    private ProgrammingExerciseImportRepositoryService importService;

    private ProgrammingExercise exercise;

    private Path extractedZip;

    private User user;

    @BeforeEach
    void setUp() throws Exception {
        importService = new ProgrammingExerciseImportRepositoryService(repositoryService, gitService);
        exercise = new ProgrammingExercise();
        exercise.setId(7L);
        // The template and solution URIs live on the participations, so the exercise needs them before the setters do anything.
        exercise.setTemplateParticipation(new de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation());
        exercise.setSolutionParticipation(new de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation());
        exercise.setTemplateRepositoryUri("https://artemis.example.com/git/ABC/abc-exercise.git");
        exercise.setSolutionRepositoryUri("https://artemis.example.com/git/ABC/abc-solution.git");
        exercise.setTestRepositoryUri("https://artemis.example.com/git/ABC/abc-tests.git");
        extractedZip = Files.createDirectories(baseDir.resolve("extracted"));
        user = new User();
        user.setLogin("ge12abc");
    }

    /**
     * One directory of the extracted zip, named the way the export writes it.
     */
    private Path zipDirectoryFor(String repoName) throws Exception {
        return Files.createDirectories(extractedZip.resolve("abc-" + repoName));
    }

    /**
     * @param folder the checkout folder relative to the temporary directory; may contain directories above the checkout
     */
    private Repository checkoutAt(String folder) throws Exception {
        Path path = Files.createDirectories(baseDir.resolve(folder));
        var repository = new Repository(path.resolve(".git").toString(), new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", path.getFileName().toString()));
        ReflectionTestUtils.setField(repository, "localPath", path);
        return repository;
    }

    /**
     * The three repositories a new exercise always has, checked out and ready to be filled.
     */
    private Repository[] withCheckouts() throws Exception {
        Repository templateRepo = checkoutAt("abc-exercise");
        Repository solutionRepo = checkoutAt("abc-solution");
        Repository testRepo = checkoutAt("abc-tests");
        when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), anyBoolean(), anyBoolean())).thenReturn(templateRepo, solutionRepo, testRepo);
        return new Repository[] { templateRepo, solutionRepo, testRepo };
    }

    private void withZipContent() throws Exception {
        FileUtils.write(zipDirectoryFor("exercise").resolve("Main.java").toFile(), "the template", StandardCharsets.UTF_8);
        FileUtils.write(zipDirectoryFor("solution").resolve("Main.java").toFile(), "the solution", StandardCharsets.UTF_8);
        FileUtils.write(zipDirectoryFor("tests").resolve("MainTest.java").toFile(), "the tests", StandardCharsets.UTF_8);
    }

    @Test
    void theContentOfTheZipEndsUpInTheMatchingRepository() throws Exception {
        var repositories = withCheckouts();
        withZipContent();

        importService.importRepositoriesFromFile(exercise, extractedZip, user);

        assertThat(repositories[0].getLocalPath().resolve("Main.java")).content(StandardCharsets.UTF_8).isEqualTo("the template");
        assertThat(repositories[1].getLocalPath().resolve("Main.java")).content(StandardCharsets.UTF_8).isEqualTo("the solution");
        assertThat(repositories[2].getLocalPath().resolve("MainTest.java")).content(StandardCharsets.UTF_8).isEqualTo("the tests");
    }

    @Test
    void whateverWasInTheRepositoriesBeforeIsRemovedFirst() throws Exception {
        // The repositories were created with the default template, which would otherwise be mixed with the imported content.
        var repositories = withCheckouts();
        withZipContent();

        importService.importRepositoriesFromFile(exercise, extractedZip, user);

        verify(repositoryService).deleteAllContentInRepository(repositories[0]);
        verify(repositoryService).deleteAllContentInRepository(repositories[1]);
        verify(repositoryService).deleteAllContentInRepository(repositories[2]);
    }

    @Test
    void theGitFolderOfTheExportedExerciseIsNotCopiedIntoTheNewRepository() throws Exception {
        // Copying it would replace the new repository's history with the history of the exercise the zip came from.
        var repositories = withCheckouts();
        withZipContent();
        FileUtils.write(zipDirectoryFor("exercise").resolve(".git/config").toFile(), "[remote \"origin\"]", StandardCharsets.UTF_8);

        importService.importRepositoriesFromFile(exercise, extractedZip, user);

        assertThat(repositories[0].getLocalPath().resolve(".git/config")).doesNotExist();
        assertThat(repositories[0].getLocalPath().resolve("Main.java")).exists();
    }

    @Test
    void theGradleWrapperArrivesExecutable() throws Exception {
        // A gradlew without its executable bit makes every build of the imported exercise fail for a reason that has
        // nothing to do with the student's code.
        var repositories = withCheckouts();
        withZipContent();
        Path gradlew = zipDirectoryFor("exercise").resolve("gradlew");
        FileUtils.write(gradlew.toFile(), "#!/bin/sh", StandardCharsets.UTF_8);
        assertThat(gradlew.toFile().setExecutable(false)).isTrue();

        importService.importRepositoriesFromFile(exercise, extractedZip, user);

        assertThat(repositories[0].getLocalPath().resolve("gradlew").toFile().canExecute()).isTrue();
    }

    @Test
    void everyRepositoryIsStagedAndPushedUnderTheImportingUser() throws Exception {
        // The import is only visible to anything reading the repository once it has been pushed.
        var repositories = withCheckouts();
        withZipContent();

        importService.importRepositoriesFromFile(exercise, extractedZip, user);

        verify(gitService).stageAllChanges(repositories[0]);
        verify(gitService).stageAllChanges(repositories[1]);
        verify(gitService).stageAllChanges(repositories[2]);
        verify(gitService).commitAndPush(eq(repositories[0]), any(), anyBoolean(), eq(user));
        verify(gitService).commitAndPush(eq(repositories[1]), any(), anyBoolean(), eq(user));
        verify(gitService).commitAndPush(eq(repositories[2]), any(), anyBoolean(), eq(user));
    }

    @Test
    void anAuxiliaryRepositoryIsImportedFromTheDirectoryNamedAfterIt() throws Exception {
        // The auxiliary repository is looked up by the name in its checkout folder, so this also pins that the lookup does
        // not depend on where the server keeps its checkouts.
        Repository templateRepo = checkoutAt("abc-exercise");
        Repository solutionRepo = checkoutAt("abc-solution");
        Repository testRepo = checkoutAt("abc-tests");
        // Deliberately under a directory whose name contains a dash: the parser this replaced split the whole checkout path,
        // so it derived "runner/abc-helpers" here, which no directory name can end with. Without the dash above the folder
        // the old parser would derive "helpers" too and the test would pass either way.
        Repository auxRepo = checkoutAt("ci-runner/abc-helpers");
        var auxiliaryRepository = new AuxiliaryRepository();
        auxiliaryRepository.setName("helpers");
        auxiliaryRepository.setRepositoryUri("https://artemis.example.com/git/ABC/abc-helpers.git");
        auxiliaryRepository.setExercise(exercise);
        exercise.setAuxiliaryRepositories(List.of(auxiliaryRepository));
        when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), anyBoolean(), anyBoolean())).thenReturn(templateRepo, solutionRepo, testRepo, auxRepo);
        withZipContent();
        FileUtils.write(zipDirectoryFor("helpers").resolve("Helper.java").toFile(), "the helpers", StandardCharsets.UTF_8);

        importService.importRepositoriesFromFile(exercise, extractedZip, user);

        assertThat(auxRepo.getLocalPath().resolve("Helper.java")).content(StandardCharsets.UTF_8).isEqualTo("the helpers");
        verify(repositoryService).deleteAllContentInRepository(auxRepo);
        verify(gitService).stageAllChanges(auxRepo);
        verify(gitService).commitAndPush(eq(auxRepo), any(), anyBoolean(), eq(user));
    }

    @Test
    void aZipWithoutADirectoryForOneOfTheRepositoriesIsRejected() throws Exception {
        // Importing what is there and silently leaving one repository empty would produce an exercise nobody can build.
        withCheckouts();
        FileUtils.write(zipDirectoryFor("exercise").resolve("Main.java").toFile(), "the template", StandardCharsets.UTF_8);

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> importService.importRepositoriesFromFile(exercise, extractedZip, user))
                .withMessageContaining("solution");
    }

    @Test
    void aZipWithTwoDirectoriesForOneRepositoryIsRejected() throws Exception {
        // There is no way to tell which of them was meant, and picking one at random would import the wrong content.
        withCheckouts();
        withZipContent();
        Files.createDirectories(extractedZip.resolve("another-exercise"));

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> importService.importRepositoriesFromFile(exercise, extractedZip, user))
                .withMessageContaining("exercise");
    }

    @Test
    void aZipThatCannotBeReadIsReportedAsABadRequest() throws Exception {
        withCheckouts();

        assertThatExceptionOfType(de.tum.cit.aet.artemis.core.exception.BadRequestAlertException.class)
                .isThrownBy(() -> importService.importRepositoriesFromFile(exercise, baseDir.resolve("does-not-exist"), user)).withMessageContaining("Could not read the directory")
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("couldnotreaddirectory"));
    }
}
