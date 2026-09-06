package de.tum.cit.aet.artemis.localvc.util;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;
import static de.tum.cit.aet.artemis.core.config.Constants.SETUP_COMMIT_MESSAGE;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
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
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;

/**
 * Creates the LocalVC repositories that fixtures need, using the same production calls the server uses.
 * <p>
 * Test fixtures such as {@code ProgrammingExerciseUtilService} build a programming exercise directly in the database instead of going through
 * {@code ProgrammingExerciseCreationUpdateService}. They set repository URIs on the exercise and its participations, but no repository exists behind those URIs. This
 * service closes that gap: it creates the missing repository with {@link VersionControlService#createRepository} and gives it the same empty setup commit
 * {@code ProgrammingExerciseRepositoryService#createAndInitializeAuxiliaryRepository} creates, written straight into the bare repository.
 * <p>
 * Tests therefore never construct a git repository themselves. A test that wants the full creation flow (template content, initial submissions, build plans) should post to
 * {@code /api/programming/programming-exercises/setup} instead.
 */
@Service
@Profile(SPRING_PROFILE_TEST)
@Lazy
public class LocalVCRepositoryTestService {

    private static final Logger log = LoggerFactory.getLogger(LocalVCRepositoryTestService.class);

    @Autowired
    private ObjectProvider<VersionControlService> versionControlServiceProvider;

    @Autowired
    private ObjectProvider<GitService> gitServiceProvider;

    @Value("${artemis.version-control.url}")
    private URI localVCBaseUri;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private Path localVCBasePath;

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    @Value("${artemis.git.name}")
    private String artemisGitName;

    @Value("${artemis.git.email}")
    private String artemisGitEmail;

    /**
     * One monitor per repository path, so that two fixtures asking for the same repository in parallel create it once.
     */
    private static final Map<String, Object> CREATION_LOCKS = new ConcurrentHashMap<>();

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
        Path bareRepositoryPath = repositoryUri.getLocalRepositoryPath(localVCBasePath);
        GitService gitService = gitServiceProvider.getIfAvailable();
        if (gitService == null) {
            log.debug("Skipping repository creation for {}/{}: no git service in this profile", projectKey, repositorySlug);
            return;
        }
        VersionControlService versionControlService = versionControlServiceProvider.getIfAvailable();
        // Test classes run in parallel in one JVM, so two fixtures can ask for the same repository at the same time. Without this lock both would pass the existence
        // check below, and the second would seed a repository the first is still seeding.
        synchronized (lockFor(bareRepositoryPath)) {
            if (Files.exists(bareRepositoryPath)) {
                return;
            }
            try {
                if (versionControlService != null) {
                    versionControlService.createRepository(projectKey, repositorySlug);
                }
                else {
                    // Profiles without the localvc profile have no LocalVCService to delegate to, but their tests still read the repository off disk, so it has to exist.
                    // Create it the way LocalVCService.createRepository would: a bare repository whose HEAD points at the default branch.
                    createBareRepositoryDirectly(bareRepositoryPath, projectKey, repositorySlug);
                }
                // Register the repository as soon as it exists. Seeding it below can fail, and a half-initialised repository that nothing owns would stay on disk and be
                // returned by the existence check of a later test.
                RepositoryExportTestUtil.trackBareRepository(bareRepositoryPath);
                // A freshly created bare repository has no branch yet, so it gets the same empty setup commit production creates for an auxiliary repository.
                writeSetupCommitIntoBareRepository(bareRepositoryPath);
            }
            catch (Exception e) {
                throw new IllegalStateException("Failed to create the LocalVC repository " + projectKey + "/" + repositorySlug, e);
            }
        }
        // getOrCheckoutRepository keeps one checkout per repository URI and only pulls it, so a test that recreates a repository under the same URI would otherwise get the
        // checkout of the previous one. Dropping it here also means no test finds a warm checkout: AuxiliaryRepositoryResourceIntegrationTest deletes a repository and
        // expects the failure a missing repository produces, not the failure a stale checkout of it produces.
        gitService.deleteLocalRepository(repositoryUri);
    }

    /**
     * Gives the repository at the given path the empty initial commit that makes its default branch exist.
     * <p>
     * The commit is written straight into the bare repository rather than by cloning it, committing in the working copy and pushing back. The checkout route shares one
     * directory per repository URI with every other caller of {@link GitService}, and it deletes that directory when it is done, so two fixtures running in parallel could
     * delete each other's working copy mid-commit - which surfaced as {@code ObjectDirectoryInserter} failing with "No such file or directory" because the object store it
     * was writing into had just been removed. Writing the commit here touches only this repository, needs no working copy and no push, and cannot be disturbed by another
     * test.
     *
     * @param bareRepositoryPath the bare repository that was just created
     * @throws IOException if the commit could not be written
     */
    private void writeSetupCommitIntoBareRepository(Path bareRepositoryPath) throws IOException {
        try (Repository repository = new FileRepositoryBuilder().setGitDir(bareRepositoryPath.toFile()).build(); ObjectInserter inserter = repository.newObjectInserter()) {
            // The setup commit production creates is empty, so the commit points at an empty tree.
            ObjectId treeId = inserter.insert(new TreeFormatter());
            PersonIdent artemis = new PersonIdent(artemisGitName, artemisGitEmail);
            CommitBuilder commit = new CommitBuilder();
            commit.setTreeId(treeId);
            commit.setAuthor(artemis);
            commit.setCommitter(artemis);
            commit.setMessage(SETUP_COMMIT_MESSAGE);
            ObjectId commitId = inserter.insert(commit);
            inserter.flush();

            RefUpdate branchUpdate = repository.updateRef(Constants.R_HEADS + defaultBranch);
            branchUpdate.setNewObjectId(commitId);
            branchUpdate.setForceUpdate(true);
            RefUpdate.Result result = branchUpdate.update();
            if (result != RefUpdate.Result.NEW && result != RefUpdate.Result.FORCED && result != RefUpdate.Result.FAST_FORWARD) {
                throw new IOException("Could not point " + defaultBranch + " at the setup commit of " + bareRepositoryPath + ": " + result);
            }
        }
    }

    /**
     * @param bareRepositoryPath the repository to guard
     * @return the monitor that guards creating exactly this repository
     */
    private static Object lockFor(Path bareRepositoryPath) {
        return CREATION_LOCKS.computeIfAbsent(bareRepositoryPath.toAbsolutePath().normalize().toString(), path -> new Object());
    }

    /**
     * Creates a bare repository on disk for a test profile that has no version control service, mirroring what {@code LocalVCService.createRepository} does.
     *
     * @param repositoryPath where the bare repository should be created
     * @param projectKey     the project key, for the error message
     * @param repositorySlug the repository slug, for the error message
     */
    private void createBareRepositoryDirectly(Path repositoryPath, String projectKey, String repositorySlug) {
        try {
            Files.createDirectories(repositoryPath);
            try (Git git = Git.init().setDirectory(repositoryPath.toFile()).setBare(true).call()) {
                RefUpdate headUpdate = git.getRepository().getRefDatabase().newUpdate(Constants.HEAD, false);
                headUpdate.setForceUpdate(true);
                headUpdate.link("refs/heads/" + defaultBranch);
            }
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to create the repository " + projectKey + "/" + repositorySlug + " without a version control service", e);
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
                // FileUtils.write creates the parent directories, so a nested path such as "test/<package>/test.json" works.
                FileUtils.write(repository.getLocalPath().resolve(file.getKey()).toFile(), file.getValue(), StandardCharsets.UTF_8);
            }
            gitService.stageAllChanges(repository);
            return gitService.commitAndPush(repository, message, false, null);
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to write files to the LocalVC repository " + repositoryUri.getURI(), e);
        }
        finally {
            // Same reason as in ensureRepositoryExists: seeding is fixture work and must not leave a warm checkout for the server to find.
            gitService.deleteLocalRepository(repositoryUri);
        }
    }

    /**
     * Lists the paths of every file a repository holds at its current head.
     * <p>
     * The listing is read straight from the bare repository, so it shows what was actually pushed and no working copy is created, checked out or left behind. Directories
     * are not listed: git tracks files, and an empty directory is not part of a commit.
     *
     * @param repositoryUri the repository to read
     * @return the paths of all files, relative to the repository root, or an empty list if nothing was ever pushed
     */
    public List<String> listFilePaths(LocalVCRepositoryUri repositoryUri) {
        GitService gitService = gitServiceProvider.getIfAvailable();
        if (gitService == null) {
            throw new IllegalStateException("Cannot read " + repositoryUri.getURI() + ": the active test profile has no GitService");
        }
        try (var bareRepository = gitService.getBareRepository(repositoryUri, false)) {
            ObjectId head = bareRepository.resolve(Constants.HEAD);
            if (head == null) {
                return List.of();
            }
            try (RevWalk revWalk = new RevWalk(bareRepository); TreeWalk treeWalk = new TreeWalk(bareRepository)) {
                treeWalk.addTree(revWalk.parseCommit(head).getTree());
                treeWalk.setRecursive(true);
                List<String> filePaths = new ArrayList<>();
                while (treeWalk.next()) {
                    filePaths.add(treeWalk.getPathString());
                }
                return filePaths;
            }
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to read the LocalVC repository " + repositoryUri.getURI(), e);
        }
    }

    /**
     * Reads one file of a repository at its current head, so that a test can assert on content the production code wrote.
     *
     * @param repositoryUri the repository to read
     * @param filePath      the path of the file, relative to the repository root
     * @return the content of the file
     */
    public String readFile(LocalVCRepositoryUri repositoryUri, String filePath) {
        GitService gitService = gitServiceProvider.getIfAvailable();
        if (gitService == null) {
            throw new IllegalStateException("Cannot read " + repositoryUri.getURI() + ": the active test profile has no GitService");
        }
        try (var bareRepository = gitService.getBareRepository(repositoryUri, false)) {
            ObjectId head = bareRepository.resolve(Constants.HEAD);
            if (head == null) {
                throw new IllegalStateException("Cannot read " + filePath + " from " + repositoryUri.getURI() + ": nothing was ever pushed to it");
            }
            try (RevWalk revWalk = new RevWalk(bareRepository); TreeWalk treeWalk = TreeWalk.forPath(bareRepository, filePath, revWalk.parseCommit(head).getTree())) {
                if (treeWalk == null) {
                    throw new IllegalStateException("The repository " + repositoryUri.getURI() + " does not contain " + filePath);
                }
                return new String(bareRepository.open(treeWalk.getObjectId(0)).getBytes(), StandardCharsets.UTF_8);
            }
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed to read " + filePath + " from the LocalVC repository " + repositoryUri.getURI(), e);
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
