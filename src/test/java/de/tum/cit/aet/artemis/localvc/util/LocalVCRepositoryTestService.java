package de.tum.cit.aet.artemis.localvc.util;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.localvc.service.vcs.VersionControlService;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Creates the LocalVC repositories that fixtures need, using the same production calls the server uses.
 * <p>
 * Test fixtures such as {@code ProgrammingExerciseUtilService} build a programming exercise directly in the database instead of going through
 * {@code ProgrammingExerciseCreationUpdateService}. They set repository URIs on the exercise and its participations, but no repository exists behind those URIs. This
 * service closes that gap: it creates the missing repository with {@link VersionControlService#createRepository} and gives it an initial commit with
 * {@link GitService#commitAndPush}, mirroring {@code ProgrammingExerciseRepositoryService#createAndInitializeAuxiliaryRepository}.
 * <p>
 * Tests therefore never construct a git repository themselves. A test that wants the full creation flow (template content, initial submissions, build plans) should post to
 * {@code /api/programming/programming-exercises/setup} instead.
 */
@Service
@Profile(SPRING_PROFILE_TEST)
@Lazy
public class LocalVCRepositoryTestService {

    private static final Logger log = LoggerFactory.getLogger(LocalVCRepositoryTestService.class);

    private static final String SETUP_COMMIT_MESSAGE = "Setup";

    @Autowired
    private ObjectProvider<VersionControlService> versionControlServiceProvider;

    @Autowired
    private ObjectProvider<GitService> gitServiceProvider;

    @Value("${artemis.version-control.url}")
    private URI localVCBaseUri;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private Path localVCBasePath;

    /**
     * Creates the template, solution and tests repositories of the given exercise, plus one repository per configured auxiliary repository.
     * <p>
     * Does nothing for an exercise without a project key, and nothing for a repository that already exists.
     *
     * @param exercise the exercise whose repositories should exist
     */
    public void ensureExerciseRepositoriesExist(ProgrammingExercise exercise) {
        if (exercise == null || exercise.getProjectKey() == null) {
            return;
        }
        for (RepositoryType repositoryType : new RepositoryType[] { RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS }) {
            ensureRepositoryExists(exercise.getProjectKey(), exercise.generateRepositoryName(repositoryType));
        }
        if (exercise.getAuxiliaryRepositories() != null) {
            for (AuxiliaryRepository auxiliaryRepository : exercise.getAuxiliaryRepositories()) {
                if (auxiliaryRepository.getName() != null) {
                    ensureRepositoryExists(exercise.getProjectKey(), exercise.generateRepositoryName(auxiliaryRepository.getName()));
                }
            }
        }
    }

    /**
     * Creates the repository the given LocalVC URI points at, unless it already exists.
     *
     * @param repositoryUri the URI a fixture assigned to an exercise or participation
     */
    public void ensureRepositoryExists(LocalVCRepositoryUri repositoryUri) {
        if (repositoryUri == null) {
            return;
        }
        String slugWithGit = repositoryUri.getRelativeRepositoryPath().getFileName().toString();
        String repositorySlug = slugWithGit.endsWith(".git") ? slugWithGit.substring(0, slugWithGit.length() - 4) : slugWithGit;
        ensureRepositoryExists(repositoryUri.getProjectKey(), repositorySlug);
    }

    /**
     * Creates the repository for the given project key and slug, unless it already exists.
     *
     * @param projectKey     the project key of the exercise the repository belongs to
     * @param repositorySlug the slug of the repository, without the {@code .git} suffix
     */
    public void ensureRepositoryExists(String projectKey, String repositorySlug) {
        if (projectKey == null || repositorySlug == null || localVCBasePath == null) {
            return;
        }
        LocalVCRepositoryUri repositoryUri = new LocalVCRepositoryUri(localVCBaseUri, projectKey, repositorySlug);
        if (Files.exists(repositoryUri.getLocalRepositoryPath(localVCBasePath))) {
            return;
        }
        VersionControlService versionControlService = versionControlServiceProvider.getIfAvailable();
        GitService gitService = gitServiceProvider.getIfAvailable();
        if (versionControlService == null || gitService == null) {
            // The active test profile has no version control, so there is nothing for the URI to point at.
            log.debug("Skipping repository creation for {}/{}: no version control in this profile", projectKey, repositorySlug);
            return;
        }
        try {
            versionControlService.createRepository(projectKey, repositorySlug);
            // getOrCheckoutRepository keeps one checkout per repository URI and only pulls it. A test that recreates a repository under the same URI would otherwise get
            // the checkout of the previous repository, and pulling it fails with RefNotAdvertisedException because the new repository has no branch yet.
            gitService.deleteLocalRepository(repositoryUri);
            // A freshly created bare repository has no branch yet. Production creates the first commit the same way for auxiliary repositories.
            gitService.commitAndPush(gitService.getOrCheckoutRepository(repositoryUri, true, true), SETUP_COMMIT_MESSAGE, true, null);
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to create the LocalVC repository " + projectKey + "/" + repositorySlug, e);
        }
        finally {
            // Leave no checkout behind: the server keeps one per repository URI, and a test that finds a warm one sees different behaviour than a user would on a cold
            // server. AuxiliaryRepositoryResourceIntegrationTest deletes a repository and expects the failure a missing repository produces, not the failure a stale
            // checkout of it produces.
            gitService.deleteLocalRepository(repositoryUri);
        }
    }

    /**
     * Writes the given files into an existing LocalVC repository and pushes them, so that the content is visible to everything reading the repository through the server.
     * <p>
     * The repository is checked out with {@link GitService}, exactly as the server does when it needs a working copy, so no test-owned working copy is involved.
     *
     * @param repositoryUri the repository to write to
     * @param files         file paths relative to the repository root, mapped to their content
     * @param message       the commit message
     * @return the hash of the commit that was pushed
     */
    public String writeFilesAndPush(LocalVCRepositoryUri repositoryUri, Map<String, String> files, String message) {
        GitService gitService = gitServiceProvider.getIfAvailable();
        if (gitService == null) {
            throw new IllegalStateException("Cannot write to " + repositoryUri.getURI() + ": the active test profile has no GitService");
        }
        try {
            var repository = gitService.getOrCheckoutRepository(repositoryUri, true, true);
            for (Map.Entry<String, String> file : files.entrySet()) {
                Path filePath = repository.getLocalPath().resolve(file.getKey());
                Files.createDirectories(filePath.getParent());
                Files.writeString(filePath, file.getValue());
            }
            gitService.stageAllChanges(repository);
            return gitService.commitAndPush(repository, message, false, null);
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to write files to the LocalVC repository " + repositoryUri.getURI(), e);
        }
    }

    /**
     * Builds the LocalVC URI of a repository, without touching the file system.
     *
     * @param projectKey     the project key of the exercise the repository belongs to
     * @param repositorySlug the slug of the repository, without the {@code .git} suffix
     * @return the LocalVC URI of that repository
     */
    public LocalVCRepositoryUri repositoryUri(String projectKey, String repositorySlug) {
        return new LocalVCRepositoryUri(localVCBaseUri, projectKey, repositorySlug);
    }
}
