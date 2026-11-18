package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;

class ProgrammingExerciseRepositoryInitializationTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @CsvSource({ "PYTHON, false", "PYTHON, true", "JAVA, false", "JAVA, true" })
    void setupExerciseTemplate_initializesRemoteRepositoriesDespiteStaleWorkingCopies(ProgrammingLanguage language, boolean staleWorkingCopies) throws Exception {
        Path bareBase = tempDir.resolve("bare");
        Path cloneBase = tempDir.resolve("clones");
        GitService gitService = new GitService();
        ReflectionTestUtils.setField(gitService, "localVCBasePath", bareBase);
        ReflectionTestUtils.setField(gitService, "repoClonePath", cloneBase);
        ReflectionTestUtils.setField(gitService, "defaultBranch", "main");
        ResourceLoaderService resourceLoader = new ResourceLoaderService(new DefaultResourceLoader(), null);
        ReflectionTestUtils.setField(resourceLoader, "templateFileSystemPath", Optional.empty());
        MavenCentralMirrorService mirrorService = new MavenCentralMirrorService();
        ReflectionTestUtils.setField(mirrorService, "mirrorUrl", "");
        var service = new ProgrammingExerciseRepositoryService(gitService, null, resourceLoader, Optional.empty(), mirrorService);

        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setTitle("New exercise");
        exercise.setProgrammingLanguage(language);
        exercise.setPackageName("de.test");
        exercise.setStaticCodeAnalysisEnabled(false);
        exercise.setTemplateParticipation(new TemplateProgrammingExerciseParticipation());
        exercise.setSolutionParticipation(new SolutionProgrammingExerciseParticipation());
        URI baseUri = URI.create("https://artemis.example.com");
        exercise.setTemplateRepositoryUri(new LocalVCRepositoryUri(baseUri, "ABC", "abc-exercise").toString());
        exercise.setSolutionRepositoryUri(new LocalVCRepositoryUri(baseUri, "ABC", "abc-solution").toString());
        exercise.setTestRepositoryUri(new LocalVCRepositoryUri(baseUri, "ABC", "abc-tests").toString());
        var uris = List.of(exercise.getVcsTemplateRepositoryUri(), exercise.getVcsSolutionRepositoryUri(), exercise.getVcsTestRepositoryUri());
        for (LocalVCRepositoryUri uri : uris) {
            Path barePath = uri.getLocalRepositoryPath(bareBase);
            Git.init().setDirectory(barePath.toFile()).setBare(true).setInitialBranch("main").call().close();
            if (staleWorkingCopies) {
                Path checkout = gitService.getDefaultLocalCheckOutPathOfRepo(uri);
                try (Git git = Git.init().setDirectory(checkout.toFile()).setInitialBranch("main").call()) {
                    FileUtils.writeStringToFile(checkout.resolve("stale.txt").toFile(), "Contents of a deleted exercise", StandardCharsets.UTF_8);
                    git.add().addFilepattern(".").call();
                    GitService.commit(git).setMessage("Old exercise").setAuthor("Old Author", "old@example.com").setCommitter("Old Author", "old@example.com").call();
                }
            }
        }
        User creator = new User();
        creator.setFirstName("Exercise");
        creator.setLastName("Creator");
        creator.setEmail("creator@example.com");

        service.setupExerciseTemplate(exercise, new ProgrammingExerciseBuildConfig(), creator, false);

        for (LocalVCRepositoryUri uri : uris) {
            try (Git remote = Git.open(uri.getLocalRepositoryPath(bareBase).toFile())) {
                assertThat(remote.getRepository().resolve("refs/heads/main")).as("the new repository %s has a usable default branch", uri).isNotNull();
                assertThat(remote.log().call()).hasSize(1);
                assertThat(remote.log().call().iterator().next().getFullMessage()).contains("Template pushed by Artemis");
            }
            try (Repository checkout = gitService.getOrCheckoutRepository(uri, false, true)) {
                assertThat(checkout.resolve(Constants.HEAD)).isNotNull();
                assertThat(checkout.getLocalPath().resolve("stale.txt")).doesNotExist();
                assertThat(gitService.getFiles(checkout)).isNotEmpty();
            }
        }
    }
}
