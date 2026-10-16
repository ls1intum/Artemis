package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.dto.CommitInfoDTO;
import de.tum.cit.aet.artemis.programming.exception.GitException;

/**
 * Everything Artemis does to the bare repositories the local version control server serves: opening one, judging whether it can be served, and
 * copying one to create another.
 * <p>
 * These repositories have no working tree. They live under the local VC base path, they are what a student or a build agent clones from, and nothing
 * outside this class writes to them. That is the line between this service and {@link GitService}, which checks repositories out and works on the copy.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class BareGitRepositoryService extends AbstractGitService {

    private static final Logger log = LoggerFactory.getLogger(BareGitRepositoryService.class);

    /** Marks the directory a repository copy is built in before it is published at its final path, see {@link #buildAndPublishBareRepository}. */
    private static final String BUILD_DIRECTORY_SUFFIX = ".building-";

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private Path localVCBasePath;

    // The two overrides below read the same as GitService's on purpose: both run on the server, where a repository is a path. They cannot move up into
    // AbstractGitService, because BuildJobGitService runs on a build agent and reaches the same repositories over SSH or HTTP with credentials attached.

    @Override
    protected URI getGitUri(@NonNull LocalVCRepositoryUri vcsRepositoryUri) {
        return vcsRepositoryUri.getLocalRepositoryPath(localVCBasePath).toUri();
    }

    @Override
    protected LsRemoteCommand lsRemoteCommand() {
        return Git.lsRemoteRepository();
    }

    /**
     * Retrieves a bare JGit repository based on a remote repository URI. This method is functional only when LocalVC is active.
     * It uses the default branch, also see {@link #getBareRepository(LocalVCRepositoryUri, String, boolean)} for more details.
     *
     * @param repositoryUri The URI of the remote VCS repository, not null.
     * @param writeAccess   Whether we write to the repository or not. If true, the git config will be set.
     * @return The initialized bare Repository instance.
     * @throws GitException If the repository cannot be created due to I/O errors or invalid reference names.
     */
    @NonNull
    public Repository getBareRepository(LocalVCRepositoryUri repositoryUri, boolean writeAccess) {
        return getBareRepository(repositoryUri, defaultBranch, writeAccess);
    }

    /**
     * Retrieves a bare JGit repository based on a remote repository URI. This method is functional only when LocalVC is active.
     * It translates a remote repository URI into a local repository path, attempting to create a repository at this location.
     * This method delegates the creation of the repository to {@code linkRepositoryForExistingGit}, which sets up the repository without a working
     * directory (bare repository).
     * <p>
     * It handles exceptions related to repository creation by throwing a {@code GitException}, providing a more specific error context.
     * Note: This method requires that LocalVC is actively managing the local version control environment to operate correctly.
     *
     * @param repositoryUri The URI of the remote VCS repository, not null.
     * @param branch        The branch to be used for the bare repository, typically the default branch.
     * @param writeAccess   Whether we write to the repository or not. If true, the git config will be set.
     * @return The initialized bare Repository instance.
     * @throws GitException If the repository cannot be created due to I/O errors or invalid reference names.
     */
    public Repository getBareRepository(LocalVCRepositoryUri repositoryUri, String branch, boolean writeAccess) {
        var localRepoUri = new LocalVCRepositoryUri(repositoryUri.toString());
        var localPath = localRepoUri.getLocalRepositoryPath(localVCBasePath);
        try {
            return linkRepositoryForExistingGit(localPath, repositoryUri, branch, true, writeAccess);
        }
        catch (IOException | InvalidRefNameException e) {
            log.error("Could not create the bare repository with uri {}", repositoryUri, e);
            throw new GitException("Could not create the bare repository", e);
        }
    }

    /**
     * Retrieves an existing bare JGit repository based on a remote repository URI. This method is functional only when LocalVC is active.
     * It checks if the repository already exists in the local file system and returns it if available.
     * If the repository does not exist, it attempts to create it using the provided branch.
     *
     * @param repositoryUri The URI of the remote VCS repository, not null.
     * @param branch        The branch to be used for the bare repository, typically the default branch.
     * @return The initialized bare Repository instance.
     * @throws GitException If the repository cannot be created due to I/O errors or invalid reference names.
     */
    public Repository getExistingBareRepository(LocalVCRepositoryUri repositoryUri, String branch) {
        var localRepoUri = new LocalVCRepositoryUri(repositoryUri.toString());
        var localPath = localRepoUri.getLocalRepositoryPath(localVCBasePath);
        try {
            return getExistingBareRepository(localPath, repositoryUri, branch);
        }
        catch (IOException | InvalidRefNameException e) {
            log.error("Could not create the bare repository with uri {}", repositoryUri, e);
            throw new GitException("Could not create the bare repository", e);
        }
    }

    /**
     * Checks whether the bare repository at the given URI is healthy, i.e. it can be opened and has at least one branch.
     * <p>
     * An unhealthy repository is either corrupt (e.g. a half-deleted skeleton without HEAD and config, as left behind by a
     * partially failed deletion) or unborn (created, but it never received its initial branch, e.g. after a failed ref
     * update during a repository copy). In both cases the repository does not contain any commits on any branch, so it can
     * safely be deleted and recreated without losing data. False is only returned for these definitive diagnoses; an
     * unexpected failure during the check (e.g. a transient I/O error) throws instead, so that callers do not mistake a
     * healthy repository for a corrupt one and delete it.
     *
     * @param repositoryUri the URI of the bare repository to check
     * @return true if the repository can be opened and has at least one branch (loose or packed), false if it is definitively unborn or corrupt
     * @throws GitException if the health of the repository could not be determined
     */
    public boolean isBareRepositoryHealthy(LocalVCRepositoryUri repositoryUri) {
        var localPath = repositoryUri.getLocalRepositoryPath(localVCBasePath);
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        builder.setBare().setGitDir(localPath.toFile()).setMustExist(true);
        try (org.eclipse.jgit.lib.Repository repository = builder.build()) {
            return !repository.getRefDatabase().getRefsByPrefix(Constants.R_HEADS).isEmpty();
        }
        catch (RepositoryNotFoundException e) {
            // The directory is definitively not a git repository (e.g. a half-deleted skeleton without HEAD and config)
            log.warn("Bare repository at {} is not a valid git repository, considering it corrupt: {}", localPath, e.getMessage());
            return false;
        }
        catch (IOException | RuntimeException e) {
            throw new GitException("Could not check the health of the bare repository at " + localPath, e);
        }
    }

    /**
     * Creates a new bare Git repository at the specified target location,
     * containing a single commit on the configured default branch that includes all files from the source repository.
     * <p>
     * The history of the source repository is not preserved; instead, a new commit is created
     * with a fresh tree built from the source repository's latest state. The commit's author and
     * committer information is taken from the first commit of the source repository.
     * <p>
     * This method avoids cloning the source repository and directly works with its object database for performance reasons.
     *
     * @param sourceRepoUri the URI of the source bare repository to copy from
     * @param targetRepoUri the URI where the new bare repository will be created
     * @param sourceBranch  the name of the branch to copy (e.g., "main" or "master")
     * @return a Repository object representing the newly created bare repository
     * @throws IOException if there is an error accessing the repositories or creating the new commit
     */
    public Repository copyBareRepositoryWithoutHistory(LocalVCRepositoryUri sourceRepoUri, LocalVCRepositoryUri targetRepoUri, String sourceBranch) throws IOException {
        log.debug("copy bare repository without history from {} to {} for source branch {}", sourceRepoUri, targetRepoUri, sourceBranch);
        // Closed once the copy is done: it holds an object database and its pack descriptors, and a copy runs on every exercise creation and every first start of
        // a participation, so leaving it open leaks one reader per copy for the lifetime of the node.
        try (Repository sourceRepo = getExistingBareRepository(sourceRepoUri, sourceBranch)) {
            logCommits(sourceRepoUri, sourceBranch, sourceRepo);

            return buildAndPublishBareRepository(targetRepoUri, buildPath -> {
                try (org.eclipse.jgit.lib.Repository targetRepo = FileRepositoryBuilder.create(buildPath.toFile())) {

                    targetRepo.create(true); // true for bare

                    // Get the HEAD tree of the source
                    ObjectId commitId = sourceRepo.resolve("refs/heads/" + sourceBranch + "^{commit}");
                    if (commitId == null) {
                        throw new IOException("Branch " + sourceBranch + " not found in " + sourceRepoUri);
                    }

                    // Both the inserter and the walk hold open pack files and buffers, so they belong in the try-with-resources
                    // rather than being left to the garbage collector: this method runs once per exercise creation on the git
                    // server, and a leaked descriptor there accumulates for the lifetime of the node.
                    try (ObjectInserter inserter = targetRepo.newObjectInserter(); RevWalk walk = new RevWalk(sourceRepo)) {
                        RevCommit headCommit = walk.parseCommit(commitId);
                        walk.markStart(headCommit);

                        RevTree headTree = headCommit.getTree();

                        // Get PersonIdent from the very first commit
                        ObjectId branchHead = sourceRepo.resolve("refs/heads/" + sourceBranch);
                        // TODO: consider to have a back up here, e.g. the first instructor of the course
                        PersonIdent personIdent = getFirstCommitPersonIdent(sourceRepo, branchHead);

                        // Walk the tree, insert blobs into target repo, and build a new tree
                        ObjectId newTreeId = buildCleanTreeFromSource(sourceRepo, inserter, headTree);
                        log.debug("found newTreeId {} for target repository {}", newTreeId, targetRepoUri);
                        inserter.flush();

                        // Create commit with the clean tree
                        CommitBuilder commitBuilder = new CommitBuilder();
                        commitBuilder.setTreeId(newTreeId);
                        commitBuilder.setMessage(de.tum.cit.aet.artemis.core.config.Constants.SET_UP_TEMPLATE_FOR_EXERCISE);

                        // Set author and committer information based on the first commit in the source repo
                        commitBuilder.setAuthor(personIdent);
                        commitBuilder.setCommitter(personIdent);
                        ObjectId newCommitId = inserter.insert(commitBuilder);
                        inserter.flush();

                        // Publish on the default branch, matching the copied participation and the configured HEAD.
                        RefUpdate refUpdate = targetRepo.updateRef("refs/heads/" + defaultBranch);
                        refUpdate.setNewObjectId(newCommitId);
                        refUpdate.setForceUpdate(true);
                        verifyRefUpdateResult(refUpdate.update(), "refs/heads/" + defaultBranch, targetRepoUri);
                    }
                }
            });
        }
    }

    /**
     * Creates a new bare Git repository at the specified target location, copying all commits
     * and history from the source branch onto the configured default branch.
     * <p>
     * This method efficiently duplicates the entire commit history from the source to the target
     * repository by directly transferring Git objects (commits, trees, and blobs) without checking
     * out any working tree. It is designed for bare repositories, ensuring that the complete
     * history is preserved in the new repository.
     *
     * @param sourceRepoUri the URI of the source bare repository to copy from
     * @param targetRepoUri the URI where the new bare repository will be created
     * @param sourceBranch  the name of the branch to copy (e.g., "main" or "master")
     * @return a Repository object representing the newly created bare repository
     * @throws IOException if there is an error accessing the repositories or creating the new commit
     */
    public Repository copyBareRepositoryWithHistory(LocalVCRepositoryUri sourceRepoUri, LocalVCRepositoryUri targetRepoUri, String sourceBranch) throws IOException {
        log.debug("Copying full history from {} to {} for branch {}", sourceRepoUri, targetRepoUri, sourceBranch);
        // Closed once the copy is done: it holds an object database and its pack descriptors, and a copy runs on every exercise creation and every first start of
        // a participation, so leaving it open leaks one reader per copy for the lifetime of the node.
        try (Repository sourceRepo = getExistingBareRepository(sourceRepoUri, sourceBranch)) {
            logCommits(sourceRepoUri, sourceBranch, sourceRepo);

            // Resolve the HEAD commit of the branch
            ObjectId headCommitId = sourceRepo.resolve("refs/heads/" + sourceBranch + "^{commit}");
            if (headCommitId == null) {
                throw new IOException("Source branch " + sourceBranch + " not found in " + sourceRepoUri);
            }

            return buildAndPublishBareRepository(targetRepoUri, buildPath -> {
                try (org.eclipse.jgit.lib.Repository targetRepo = FileRepositoryBuilder.create(buildPath.toFile())) {
                    targetRepo.create(true); // bare = true

                    try (ObjectInserter inserter = targetRepo.newObjectInserter(); RevWalk revWalk = new RevWalk(sourceRepo)) {

                        Set<ObjectId> copiedObjects = new HashSet<>();
                        Deque<ObjectId> toProcess = new ArrayDeque<>();
                        toProcess.add(headCommitId);

                        while (!toProcess.isEmpty()) {
                            ObjectId current = toProcess.poll();
                            if (!copiedObjects.add(current)) {
                                continue; // already processed
                            }

                            ObjectLoader loader = sourceRepo.open(current);
                            // The stream belongs to this loop rather than to the inserter, which reads it but does not close it. A full-history copy opens one per
                            // reachable object, so leaving them to the garbage collector holds the whole repository's worth of readers open at once.
                            try (ObjectStream objectStream = loader.openStream()) {
                                inserter.insert(loader.getType(), loader.getSize(), objectStream);
                            }

                            // If this is a commit, enqueue parents and tree
                            if (loader.getType() == Constants.OBJ_COMMIT) {
                                RevCommit commit = revWalk.parseCommit(current);
                                toProcess.add(commit.getTree().getId());
                                for (RevCommit parent : commit.getParents()) {
                                    toProcess.add(parent.getId());
                                }
                            }

                            // If this is a tree, enqueue its entries (subtrees and blobs)
                            if (loader.getType() == Constants.OBJ_TREE) {
                                try (TreeWalk treeWalk = new TreeWalk(sourceRepo)) {
                                    treeWalk.addTree(current);
                                    treeWalk.setRecursive(false);
                                    while (treeWalk.next()) {
                                        toProcess.add(treeWalk.getObjectId(0));
                                    }
                                }
                            }
                        }

                        inserter.flush();

                        // Imported exercises use the default branch, which is also the target of the configured HEAD.
                        RefUpdate refUpdate = targetRepo.updateRef("refs/heads/" + defaultBranch);
                        refUpdate.setNewObjectId(headCommitId);
                        refUpdate.setForceUpdate(true);
                        verifyRefUpdateResult(refUpdate.update(), "refs/heads/" + defaultBranch, targetRepoUri);
                    }
                }
            });
        }
    }

    /**
     * Builds a bare repository in a directory of its own and moves it to the target path in a single atomic rename.
     * <p>
     * Two requests can ask for the same student repository at the same time, and a repository that is built at its final path is visible there while it is still being
     * written: it has no branch yet, which is indistinguishable from the broken leftover of an earlier failed copy. The second request would classify it as such and delete
     * the directory the first one is writing into, which fails both requests (issue #13870). Building elsewhere and publishing with one rename removes that state entirely:
     * the target path either does not exist or holds a complete repository, whatever the interleaving and whichever node the request is served by. The request that renames
     * second finds the target taken and keeps the repository that is already there, since both copies have the same content.
     *
     * @param targetRepoUri the URI the finished repository is published at
     * @param build         builds the bare repository at the path it is given
     * @return the published repository, which is the one this call built or the one a concurrent call published first
     * @throws IOException if the repository could not be built or published
     */
    private Repository buildAndPublishBareRepository(LocalVCRepositoryUri targetRepoUri, BareRepositoryBuilder build) throws IOException {
        Path targetPath = new LocalVCRepositoryUri(targetRepoUri.toString()).getLocalRepositoryPath(localVCBasePath);
        // A sibling of the target so that the rename stays within one file system, and without the ".git" suffix that the git server resolves a repository URL to, so that
        // the repository cannot be served while it is incomplete.
        Path buildPath = targetPath.resolveSibling(targetPath.getFileName() + BUILD_DIRECTORY_SUFFIX + UUID.randomUUID());
        try {
            Files.createDirectories(buildPath);
            build.buildInto(buildPath);
            // Apply the repository configuration while the repository is still private to this call. Doing it through the published path instead would have every request
            // that copied the same participation write config.lock in the same repository at the same time, which JGit refuses with a LockFailedException.
            try {
                linkRepositoryForExistingGit(buildPath, targetRepoUri, defaultBranch, true, true).close();
            }
            catch (InvalidRefNameException e) {
                throw new IOException("Could not configure the copy of repository " + targetRepoUri, e);
            }
            try {
                FileUtil.publishAtomically(buildPath, targetPath);
            }
            catch (FileSystemException renameFailed) {
                // A rename onto a path that is taken reports "directory not empty" or "file exists", depending on the platform, so what the target looks like now decides
                // rather than the exception type: anything at the target path is a repository another request published, and reporting that one is what the caller needs.
                if (!Files.exists(targetPath)) {
                    throw renameFailed;
                }
                log.info("Repository {} was published by a concurrent request while this copy was running, keeping the repository that is already there", targetRepoUri);
            }
        }
        finally {
            // Nothing is left to delete once the repository was published, and a copy that failed or lost the race must not leave its build directory behind.
            FileUtils.deleteQuietly(buildPath.toFile());
        }
        // Read-only: the configuration is already written, and the caller only reads the repository's URI off this handle. Opening for writing would put the config write
        // back into the published repository, where concurrent requests collide on it.
        return getBareRepository(targetRepoUri, false);
    }

    /**
     * Builds a bare repository at the given path, which is not the path the repository is finally published at.
     */
    @FunctionalInterface
    private interface BareRepositoryBuilder {

        void buildInto(Path buildPath) throws IOException;
    }

    /**
     * Verifies that a ref update during a bare-repository copy succeeded. Acceptable results are NEW (fresh repository),
     * FORCED and FAST_FORWARD (forced update of an existing ref) and NO_CHANGE (idempotent re-copy). Any other result
     * (e.g. REJECTED, LOCK_FAILURE, IO_FAILURE) does not throw on its own but would leave the target repository unborn
     * (without any branch), which breaks every subsequent access to it. Failing the copy instead lets the caller clean
     * up the broken target repository.
     *
     * @param result        the result of the ref update
     * @param refName       the name of the ref that was updated, e.g. "refs/heads/main"
     * @param targetRepoUri the URI of the repository the ref belongs to
     * @throws IOException if the ref update failed, so that the caller cleans up the broken target repository instead of returning it
     */
    // package-private for testing
    static void verifyRefUpdateResult(RefUpdate.Result result, String refName, LocalVCRepositoryUri targetRepoUri) throws IOException {
        switch (result) {
            case NEW, FORCED, FAST_FORWARD, NO_CHANGE -> log.debug("Updated {} in {} with result {}", refName, targetRepoUri, result);
            default -> throw new IOException("Could not update " + refName + " in target repository " + targetRepoUri + ": ref update result was " + result);
        }
    }

    private static void logCommits(LocalVCRepositoryUri sourceRepoUri, String sourceBranch, Repository sourceRepo) throws IOException {
        if (log.isDebugEnabled()) {
            // Log how many commits the source repository has
            try (RevWalk walk = new RevWalk(sourceRepo)) {
                ObjectId debugCommitId = sourceRepo.resolve("refs/heads/" + sourceBranch + "^{commit}");
                if (debugCommitId == null) {
                    log.error("Source repo [{}] has no head commit in branch [{}]", sourceRepoUri, sourceBranch);
                    // Walking from a commit that is not there fails with a NullPointerException, which would replace the copy's own report of the missing branch with an
                    // error that says nothing - and only when debug logging happens to be on.
                    return;
                }
                RevCommit headCommit = walk.parseCommit(debugCommitId);
                walk.markStart(headCommit);
                int commitCount = 0;
                for (RevCommit ignored : walk) {
                    commitCount++;
                }
                log.debug("Source repository {} has {} commits", sourceRepoUri, commitCount);
                if (commitCount == 0) {
                    log.error("Source repository {} is empty, no commits to copy. This operation will fail", sourceRepoUri);
                }
            }
        }
    }

    /**
     * Retrieves the PersonIdent (author) from the first (root) commit of the specified branch.
     * <p>
     * This method walks through the commit history of the provided branch and returns
     * the PersonIdent (author) of the commit that has no parents (i.e., the very first commit).
     * <p>
     * Note: If the branch is empty or no commits are found, an IOException is thrown.
     *
     * @param repo       the JGit repository object to read from
     * @param branchHead the ObjectId representing the branch head (e.g., resolve("refs/heads/main"))
     * @return the PersonIdent of the author of the first commit in the branch
     * @throws IOException if the first commit cannot be found or a repository error occurs
     */
    private PersonIdent getFirstCommitPersonIdent(Repository repo, ObjectId branchHead) throws IOException {
        try (RevWalk walk = new RevWalk(repo)) {
            walk.markStart(walk.parseCommit(branchHead));

            for (RevCommit commit : walk) {
                if (commit.getParentCount() == 0) {
                    return commit.getAuthorIdent();
                }
            }
        }
        throw new IOException("First commit not found");
    }

    /**
     * Builds a clean tree from the source repository's tree, copying blobs and subtrees.
     *
     * @param sourceRepo The source repository from which to copy the tree.
     * @param inserter   The ObjectInserter to insert objects into the target repository.
     * @param sourceTree The source tree to copy from.
     * @return The ObjectId of the newly created clean tree in the target repository.
     * @throws IOException If an I/O error occurs during the copying process.
     */
    private ObjectId buildCleanTreeFromSource(Repository sourceRepo, ObjectInserter inserter, RevTree sourceTree) throws IOException {
        TreeFormatter treeFormatter = new TreeFormatter();

        // The walk holds an object reader, and this method recurses once per directory, so leaving it open leaks a reader for every directory of every
        // repository ever copied rather than just one.
        try (TreeWalk treeWalk = new TreeWalk(sourceRepo)) {
            treeWalk.addTree(sourceTree);
            treeWalk.setRecursive(false);

            while (treeWalk.next()) {
                ObjectId objectId = treeWalk.getObjectId(0);
                FileMode mode = treeWalk.getFileMode(0);
                String name = treeWalk.getNameString();

                if (mode == FileMode.TREE) {
                    // Recursively copy subtrees. The walk is closed per subtree, for the same reason as above.
                    try (RevWalk subTreeWalk = new RevWalk(sourceRepo)) {
                        RevTree subTree = subTreeWalk.parseTree(objectId);
                        ObjectId newSubTreeId = buildCleanTreeFromSource(sourceRepo, inserter, subTree);
                        treeFormatter.append(name, FileMode.TREE, newSubTreeId);
                    }
                }
                else {
                    // Read blob from source and insert into target
                    ObjectLoader loader = sourceRepo.open(objectId);
                    ObjectId newBlobId = inserter.insert(Constants.OBJ_BLOB, loader.getBytes());
                    treeFormatter.append(name, mode, newBlobId);
                }
            }
        }

        return inserter.insert(treeFormatter);
    }

    /**
     * Reads the git log of a repository the local version control server holds. The repository is read where it lies, without checking it out.
     *
     * @param vcsRepositoryUri the repository uri for which the git log should be retrieved
     * @return a list of commit info DTOs containing author, timestamp, commit message, and hash
     * @throws GitAPIException if an error occurs while retrieving the git log
     */
    public List<CommitInfoDTO> getCommitInfos(LocalVCRepositoryUri vcsRepositoryUri) throws GitAPIException {
        List<CommitInfoDTO> commitInfos = new ArrayList<>();
        log.debug("Using local VCS for getting commit info on repo {}", vcsRepositoryUri);
        try (var repo = getBareRepository(vcsRepositoryUri, false); var git = new Git(repo)) {
            Iterable<RevCommit> commits = git.log().call();
            commits.forEach(commit -> {
                var commitInfo = CommitInfoDTO.of(commit);
                commitInfos.add(commitInfo);
            });
        }

        return commitInfos;
    }
}
