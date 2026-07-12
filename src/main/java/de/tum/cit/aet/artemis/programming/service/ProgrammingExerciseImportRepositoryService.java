package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingExerciseImportRepositoryService {

    private final RepositoryService repositoryService;

    private final GitService gitService;

    public ProgrammingExerciseImportRepositoryService(RepositoryService repositoryService, GitService gitService) {
        this.repositoryService = repositoryService;
        this.gitService = gitService;
    }

    /**
     * Imports the repositories from the extracted zip file.
     *
     * @param newExercise the new programming exercise to which the repositories should be imported
     * @param basePath    the path to the extracted zip file
     * @param user        the user performing the import
     */
    public void importRepositoriesFromFile(ProgrammingExercise newExercise, Path basePath, User user) throws IOException, GitAPIException {
        Repository templateRepo = gitService.getOrCheckoutRepository(new LocalVCRepositoryUri(newExercise.getTemplateRepositoryUri()), false, true);
        Repository solutionRepo = gitService.getOrCheckoutRepository(new LocalVCRepositoryUri(newExercise.getSolutionRepositoryUri()), false, true);
        Repository testRepo = gitService.getOrCheckoutRepository(new LocalVCRepositoryUri(newExercise.getTestRepositoryUri()), false, true);
        List<Repository> auxiliaryRepositories = new ArrayList<>();
        for (AuxiliaryRepository auxiliaryRepository : newExercise.getAuxiliaryRepositories()) {
            auxiliaryRepositories.add(gitService.getOrCheckoutRepository(auxiliaryRepository.getVcsRepositoryUri(), false, true));
        }

        copyImportedExerciseContentToRepositories(templateRepo, solutionRepo, testRepo, auxiliaryRepositories, basePath);

        gitService.stageAllChanges(templateRepo);
        gitService.stageAllChanges(solutionRepo);
        gitService.stageAllChanges(testRepo);
        for (Repository auxRepo : auxiliaryRepositories) {
            gitService.stageAllChanges(auxRepo);
        }

        gitService.commitAndPush(templateRepo, "Import template from file", true, user);
        gitService.commitAndPush(solutionRepo, "Import solution from file", true, user);
        gitService.commitAndPush(testRepo, "Import tests from file", true, user);
        for (Repository auxRepo : auxiliaryRepositories) {
            gitService.commitAndPush(auxRepo, "Import auxiliary repo from file", true, user);
        }
    }

    private void copyImportedExerciseContentToRepositories(Repository templateRepo, Repository solutionRepo, Repository testRepo, List<Repository> auxiliaryRepositories,
            Path basePath) throws IOException {
        repositoryService.deleteAllContentInRepository(templateRepo);
        repositoryService.deleteAllContentInRepository(solutionRepo);
        repositoryService.deleteAllContentInRepository(testRepo);
        for (Repository auxRepo : auxiliaryRepositories) {
            repositoryService.deleteAllContentInRepository(auxRepo);
        }

        copyExerciseContentToRepository(templateRepo, RepositoryType.TEMPLATE.getName(), basePath);
        copyExerciseContentToRepository(solutionRepo, RepositoryType.SOLUTION.getName(), basePath);
        copyExerciseContentToRepository(testRepo, RepositoryType.TESTS.getName(), basePath);
        for (Repository auxRepo : auxiliaryRepositories) {
            String[] parts = auxRepo.getLocalPath().toString().split("-");
            var auxRepoName = String.join("-", Arrays.copyOfRange(parts, 1, parts.length));
            copyExerciseContentToRepository(auxRepo, auxRepoName, basePath);
        }
    }

    /**
     * Copies everything from the extracted zip file to the repository, except the .git folder
     *
     * @param repository the repository to which the content should be copied
     * @param repoName   the name of the repository
     * @param basePath   the path to the extracted zip file
     */
    private void copyExerciseContentToRepository(Repository repository, String repoName, Path basePath) throws IOException {
        // @formatter:off
        FileUtils.copyDirectory(
            retrieveRepositoryDirectoryPath(basePath, repoName).toFile(),
            repository.getLocalPath().toFile(),
            new NotFileFilter(new NameFileFilter(".git"))
        );
        // @formatter:on

        try (var files = Files.walk(repository.getLocalPath())) {
            files.filter(file -> "gradlew".equals(file.getFileName().toString())).forEach(file -> file.toFile().setExecutable(true));
        }
    }

    private Path retrieveRepositoryDirectoryPath(Path dirPath, String repoName) {
        List<Path> result;
        try (Stream<Path> walk = Files.walk(dirPath)) {
            result = walk.filter(Files::isDirectory).filter(file -> file.getFileName().toString().endsWith("-" + repoName)).toList();
        }
        catch (IOException e) {
            throw new BadRequestAlertException("Could not read the directory", "programmingExercise", "couldnotreaddirectory");
        }
        if (result.size() != 1) {
            throw new IllegalArgumentException(
                    "There are either no or more than one sub-directories containing " + repoName + " in their name. Please make sure that there is exactly one.");
        }

        return result.getFirst();
    }
}
