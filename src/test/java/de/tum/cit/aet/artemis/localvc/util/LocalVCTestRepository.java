package de.tum.cit.aet.artemis.localvc.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * A LocalVC repository together with a working copy cloned from it.
 * <p>
 * The bare repository is the repository the server itself serves: it lives at {@code <localVCBasePath>/<PROJECT_KEY>/<slug>.git} and was created by
 * {@code VersionControlService.createRepository}, not by the test. The working copy is an ordinary {@code git clone} of it, which is what a test needs in order to commit and
 * push like a user would.
 * <p>
 * Obtain instances from {@code LocalVCLocalCITestService}; there is deliberately no way to point this at a repository outside the LocalVC folder structure, and no way to
 * derive a repository URI from a filesystem path.
 *
 * @param projectKey         the project key of the exercise the repository belongs to
 * @param repositorySlug     the slug of the repository, without the {@code .git} suffix
 * @param bareRepositoryPath the path of the bare repository inside the LocalVC folder structure
 * @param bareRepository     a git handle on the bare repository
 * @param workingCopyPath    the path of the working copy
 * @param workingCopy        a git handle on the working copy
 */
public record LocalVCTestRepository(String projectKey, String repositorySlug, Path bareRepositoryPath, Git bareRepository, Path workingCopyPath, Git workingCopy)
        implements AutoCloseable {

    /**
     * Returns the commits reachable from the working copy's current branch, newest first.
     *
     * @return the commits in the working copy
     * @throws GitAPIException if reading the log fails
     */
    public List<RevCommit> workingCopyCommits() throws GitAPIException {
        return StreamSupport.stream(workingCopy.log().call().spliterator(), false).toList();
    }

    /**
     * Returns the commits reachable from the bare repository's current branch, newest first.
     *
     * @return the commits in the bare repository
     * @throws GitAPIException if reading the log fails
     */
    public List<RevCommit> bareRepositoryCommits() throws GitAPIException {
        return StreamSupport.stream(bareRepository.log().call().spliterator(), false).toList();
    }

    /**
     * Closes both git handles. The repositories stay on disk.
     */
    @Override
    public void close() {
        workingCopy.close();
        bareRepository.close();
    }

    /**
     * Closes both git handles and deletes the working copy. The bare repository is left in place, because it belongs to the exercise and is removed with its project.
     *
     * @throws IOException if deleting the working copy fails
     */
    public void deleteWorkingCopy() throws IOException {
        close();
        if (Files.exists(workingCopyPath)) {
            FileUtils.deleteDirectory(workingCopyPath.toFile());
        }
    }
}
