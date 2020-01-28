package de.tum.in.www1.artemis.service.connectors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.HiddenFileFilter;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exception.GitException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class GitService {

    private final Logger log = LoggerFactory.getLogger(GitService.class);

    @Value("${artemis.version-control.user}")
    private String GIT_USER;

    @Value("${artemis.version-control.password}")
    private String GIT_PASSWORD;

    @Value("${artemis.repo-clone-path}")
    private String REPO_CLONE_PATH;

    @Value("${artemis.git.name}")
    private String ARTEMIS_GIT_NAME;

    @Value("${artemis.git.email}")
    private String ARTEMIS_GIT_EMAIL;

    private final Map<Path, Repository> cachedRepositories = new ConcurrentHashMap<>();

    private final Map<Path, Path> cloneInProgressOperations = new ConcurrentHashMap<>();

    public GitService() {
        log.info("Default Charset=" + Charset.defaultCharset());
        log.info("file.encoding=" + System.getProperty("file.encoding"));
        log.info("sun.jnu.encoding=" + System.getProperty("sun.jnu.encoding"));
        log.info("Default Charset=" + Charset.defaultCharset());
        log.info("Default Charset in Use=" + new OutputStreamWriter(new ByteArrayOutputStream()).getEncoding());
    }

    /**
     * Get the local repository for a given participation. If the local repo does not exist yet, it will be checked out.
     * Saves the local repo in the default path.
     *
     * @param participation Participation the remote repository belongs to.
     * @return the repository if it could be checked out
     * @throws InterruptedException if the repository could not be checked out.
     * @throws GitAPIException if the repository could not be checked out.
     */
    public Repository getOrCheckoutRepository(ProgrammingExerciseParticipation participation) throws InterruptedException, GitAPIException {
        return getOrCheckoutRepository(participation, REPO_CLONE_PATH);
    }

    /**
     * Get the local repository for a given participation. If the local repo does not exist yet, it will be checked out.
     * Saves the local repo in the default path.
     *
     * @param participation Participation the remote repository belongs to.
     * @param targetPath path where the repo is located on disk
     * @return the repository if it could be checked out
     * @throws InterruptedException if the repository could not be checked out.
     * @throws GitAPIException if the repository could not be checked out.
     */
    public Repository getOrCheckoutRepository(ProgrammingExerciseParticipation participation, String targetPath) throws InterruptedException, GitAPIException {
        URL repoUrl = participation.getRepositoryUrlAsUrl();
        Repository repository = getOrCheckoutRepository(repoUrl, true, targetPath);
        repository.setParticipation(participation);
        return repository;
    }

    /**
     * Get the local repository for a given remote repository URL. If the local repo does not exist yet, it will be checked out.
     * Saves the repo in the default path
     *
     * @param repoUrl   The remote repository.
     * @param pullOnGet Pull from the remote on the checked out repository, if it does not need to be cloned.
     * @return the repository if it could be checked out.
     * @throws InterruptedException if the repository could not be checked out.
     * @throws GitAPIException if the repository could not be checked out.
     */
    public Repository getOrCheckoutRepository(URL repoUrl, boolean pullOnGet) throws InterruptedException, GitAPIException {
        return getOrCheckoutRepository(repoUrl, pullOnGet, REPO_CLONE_PATH);
    }

    /**
     * Get the local repository for a given remote repository URL. If the local repo does not exist yet, it will be checked out.
     *
     * @param repoUrl   The remote repository.
     * @param pullOnGet Pull from the remote on the checked out repository, if it does not need to be cloned.
     * @param targetPath path where the repo is located on disk
     * @return the repository if it could be checked out.
     * @throws InterruptedException if the repository could not be checked out.
     * @throws GitAPIException if the repository could not be checked out.
     */
    public Repository getOrCheckoutRepository(URL repoUrl, boolean pullOnGet, String targetPath) throws InterruptedException, GitAPIException {

        Path localPath = new java.io.File(targetPath + folderNameForRepositoryUrl(repoUrl)).toPath();

        // First try to just retrieve the git repository from our server, as it might already be checked out.
        Repository repository = getRepositoryByLocalPath(localPath);
        if (repository != null) {
            if (pullOnGet) {
                pull(repository);
            }
            return repository;
        }
        // If the git repository can't be found on our server, clone it from the remote.
        else {
            int numberOfAttempts = 5;
            // Make sure that multiple clone operations for the same repository cannot happen at the same time.
            while (cloneInProgressOperations.containsKey(localPath)) {
                log.warn("Clone is already in progress. This will lead to an error. Wait for a second");
                Thread.sleep(1000);
                if (numberOfAttempts == 0) {
                    throw new GitException("Cannot clone the same repository multiple times");
                }
                else {
                    numberOfAttempts--;
                }
            }
            // Clone repository.
            try {
                log.debug("Cloning from " + repoUrl + " to " + localPath);
                cloneInProgressOperations.put(localPath, localPath);
                Git result = Git.cloneRepository().setURI(repoUrl.toString()).setCredentialsProvider(new UsernamePasswordCredentialsProvider(GIT_USER, GIT_PASSWORD))
                        .setDirectory(localPath.toFile()).call();
                result.close();
            }
            catch (GitAPIException | RuntimeException e) {
                log.error("Exception during clone " + e);
                // cleanup the folder to avoid problems in the future
                localPath.toFile().delete();
                throw new GitException(e);
            }
            finally {
                // make sure that cloneInProgress is released
                cloneInProgressOperations.remove(localPath);
            }
            return getRepositoryByLocalPath(localPath);
        }
    }

    /**
     * Get a git repository that is checked out on the server. Throws immediately an exception if the localPath does not exist. Will first try to retrieve a cached repository from
     * cachedRepositories. Side effect: This method caches retrieved repositories in a HashMap, so continuous retrievals can be avoided (reduces load).
     *
     * @param localPath to git repo on server.
     * @return the git repository in the localPath or null if it does not exist on the server.
     */
    public Repository getRepositoryByLocalPath(Path localPath) {
        // Check if there is a folder with the provided path of the git repository.
        if (!Files.exists(localPath)) {
            // In this case we should remove the repository if cached, because it can't exist anymore.
            cachedRepositories.remove(localPath);
            return null;
        }
        // Check if the repository is already cached in the server's session.
        Repository cachedRepository = cachedRepositories.get(localPath);
        if (cachedRepository != null) {
            return cachedRepository;
        }
        // Else try to retrieve the git repository from our server. It could e.g. be the case that the folder is there, but there is no .git folder in it!
        try {
            // Open the repository from the filesystem
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            builder.setGitDir(new java.io.File(localPath + "/.git")).readEnvironment() // scan environment GIT_* variables
                    .findGitDir().setup();
            // Create the JGit repository object
            Repository repository = new Repository(builder);
            repository.setLocalPath(localPath);
            // disable auto garbage collection because it can lead to problems
            repository.getConfig().setString("gc", null, "auto", "0");
            // Cache the JGit repository object for later use
            // Avoids the expensive re-opening of local repositories
            cachedRepositories.put(localPath, repository);
            return repository;
        }
        catch (IOException ex) {
            return null;
        }
    }

    /**
     * Commits with the given message into the repository.
     *
     * @param repo    Local Repository Object.
     * @param message Commit Message
     * @throws GitAPIException if the commit failed.
     */
    public void commit(Repository repo, String message) throws GitAPIException {
        Git git = new Git(repo);
        git.commit().setMessage(message).setAllowEmpty(true).setCommitter(ARTEMIS_GIT_NAME, ARTEMIS_GIT_EMAIL).call();
        git.close();
    }

    /**
     * Commits with the given message into the repository and pushes it to the remote.
     *
     * @param repo      Local Repository Object.
     * @param message   Commit Message
     * @param user      The user who should initiate the commit. If the user is null, the artemis user will be used
     * @throws GitAPIException if the commit failed.
     */
    public void commitAndPush(Repository repo, String message, @Nullable User user) throws GitAPIException {
        var name = user != null ? user.getName() : ARTEMIS_GIT_NAME;
        var email = user != null ? user.getEmail() : ARTEMIS_GIT_EMAIL;
        Git git = new Git(repo);
        git.commit().setMessage(message).setAllowEmpty(true).setCommitter(name, email).call();
        git.push().setCredentialsProvider(new UsernamePasswordCredentialsProvider(GIT_USER, GIT_PASSWORD)).call();
        git.close();
    }

    /**
     * Stage all files in the repo including new files.
     *
     * @param repo Local Repository Object.
     * @throws GitAPIException if the staging failed.
     */
    public void stageAllChanges(Repository repo) throws GitAPIException {
        Git git = new Git(repo);
        // stage deleted files: http://stackoverflow.com/a/35601677/4013020
        git.add().setUpdate(true).addFilepattern(".").call();
        // stage new files
        git.add().addFilepattern(".").call();
        git.close();
    }

    /**
     * Resets local repository to ref.
     *
     * @param repo Local Repository Object.
     * @param ref  the ref to reset to, e.g. "origin/master"
     * @throws GitAPIException if the reset failed.
     */
    public void reset(Repository repo, String ref) throws GitAPIException {
        Git git = new Git(repo);
        git.reset().setMode(ResetCommand.ResetType.HARD).setRef(ref).call();
        git.close();
    }

    /**
     * git fetch
     *
     * @param repo Local Repository Object.
     * @throws GitAPIException if the fetch failed.
     */
    public void fetchAll(Repository repo) throws GitAPIException {
        Git git = new Git(repo);
        git.fetch().setForceUpdate(true).setRemoveDeletedRefs(true).setCredentialsProvider(new UsernamePasswordCredentialsProvider(GIT_USER, GIT_PASSWORD)).call();
        git.close();
    }

    /**
     * Pulls from remote repository. Does not throw any exceptions when pulling, e.g. CheckoutConflictException or WrongRepositoryStateException.
     *
     * @param repo Local Repository Object.
     */
    public void pullIgnoreConflicts(Repository repo) {
        try {
            Git git = new Git(repo);
            // flush cache of files
            repo.setContent(null);
            git.pull().setCredentialsProvider(new UsernamePasswordCredentialsProvider(GIT_USER, GIT_PASSWORD)).call();
        }
        catch (GitAPIException ex) {
            log.error("Cannot pull the repo " + repo.getLocalPath() + " due to the following exception: " + ex);
            // TODO: we should send this error to the client and let the user handle it there, e.g. by choosing to reset the repository
        }
    }

    /**
     * Pulls from remote repository.
     *
     * @param repo Local Repository Object.
     * @return The PullResult which contains FetchResult and MergeResult.
     * @throws GitAPIException if the pull failed.
     */
    public PullResult pull(Repository repo) throws GitAPIException {
        Git git = new Git(repo);
        // flush cache of files
        repo.setContent(null);
        return git.pull().setCredentialsProvider(new UsernamePasswordCredentialsProvider(GIT_USER, GIT_PASSWORD)).call();
    }

    /**
     * Hard reset local repository to origin/master.
     *
     * @param repo Local Repository Object.
     */
    public void resetToOriginMaster(Repository repo) {
        try {
            fetchAll(repo);
            reset(repo, "origin/master");
        }
        catch (GitAPIException | JGitInternalException ex) {
            log.error("Cannot hard reset the repo " + repo.getLocalPath() + " to origin/master due to the following exception: " + ex.getMessage());
        }
    }

    /**
     * checkout branch
     *
     * @param repo Local Repository Object.
     */
    public void checkoutBranch(Repository repo) {
        try {
            Git git = new Git(repo);
            git.checkout().setForceRefUpdate(true).setName("master").call();
            git.close();
        }
        catch (GitAPIException ex) {
            log.error("Cannot checkout branch in repo " + repo.getLocalPath() + " due to the following exception: " + ex);
        }
    }

    /**
     * Remove branch from local repository.
     *
     * @param repo Local Repository Object.
     * @param branch to delete from the repo.
     */
    public void deleteLocalBranch(Repository repo, String branch) {
        try {
            Git git = new Git(repo);
            git.branchDelete().setBranchNames(branch).setForce(true).call();
            git.close();
        }
        catch (GitAPIException ex) {
            log.error("Cannot remove branch " + branch + " from the repo " + repo.getLocalPath() + " due to the following exception: " + ex);
        }
    }

    /**
     * Get last commit hash from master
     *
     * @param repoUrl to get the latest hash from.
     * @return the latestHash of the given repo.
     * @throws EntityNotFoundException if retrieving the latestHash from the git repo failed.
     */
    public ObjectId getLastCommitHash(URL repoUrl) throws EntityNotFoundException {
        if (repoUrl == null) {
            return null;
        }
        // Get refs of repo without cloning it locally
        Collection<Ref> refs;
        try {
            refs = Git.lsRemoteRepository().setRemote(repoUrl.toString()).setCredentialsProvider(new UsernamePasswordCredentialsProvider(GIT_USER, GIT_PASSWORD)).call();
        }
        catch (GitAPIException ex) {
            throw new EntityNotFoundException("Could not retrieve the last commit hash for repoUrl " + repoUrl + " due to the following exception: " + ex);
        }
        for (Ref ref : refs) {
            // We are looking for the latest commit hash of the master branch
            if (ref.getName().equalsIgnoreCase("refs/heads/master")) {
                return ref.getObjectId();
            }
        }
        return null;
    }

    /**
     * Stager Task #3: Filter late submissions Filter all commits after exercise due date
     *
     * @param repository Local Repository Object.
     * @param lastValidSubmission The last valid submission from the database or empty, if not found
     * @param filterLateSubmissionsDate the date after which all submissions should be filtered out (may be null)
     */
    @Transactional(readOnly = true)
    public void filterLateSubmissions(Repository repository, Optional<Submission> lastValidSubmission, ZonedDateTime filterLateSubmissionsDate) {
        if (filterLateSubmissionsDate == null) {
            // No date set in client and exercise has no due date
            return;
        }

        try {
            Git git = new Git(repository);

            String commitHash;

            if (lastValidSubmission.isPresent()) {
                log.debug("Last valid submission for participation {} is {}", lastValidSubmission.get().getParticipation().getId(), lastValidSubmission.get().toString());
                ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) lastValidSubmission.get();
                commitHash = programmingSubmission.getCommitHash();
            }
            else {
                log.debug("Last valid submission is not present for participation");
                // Get last commit before deadline
                Date since = Date.from(Instant.EPOCH);
                Date until = Date.from(filterLateSubmissionsDate.toInstant());
                RevFilter between = CommitTimeRevFilter.between(since, until);
                Iterable<RevCommit> commits = git.log().setRevFilter(between).call();
                RevCommit latestCommitBeforeDeadline = commits.iterator().next();
                commitHash = latestCommitBeforeDeadline.getId().getName();
            }
            log.debug("Last commit hash is {}", commitHash);

            git.close();

            reset(repository, commitHash);
        }
        catch (GitAPIException | JGitInternalException ex) {
            log.warn("Cannot filter the repo " + repository.getLocalPath() + " due to the following exception: " + ex.getMessage());
        }
    }

    /**
     * Stager Task #6: Combine all commits after last instructor commit
     *
     * @param repository Local Repository Object.
     * @param programmingExercise   ProgrammingExercise associated with this repo.
     */
    public void combineAllStudentCommits(Repository repository, ProgrammingExercise programmingExercise) {
        try {
            Git studentGit = new Git(repository);
            // Get last commit hash from template repo
            ObjectId latestHash = getLastCommitHash(programmingExercise.getTemplateRepositoryUrlAsUrl());

            if (latestHash == null) {
                // Template Repository is somehow empty. Should never happen
                log.info("Cannot find a commit in the template repo for:" + repository.getLocalPath());
                return;
            }

            // flush cache of files
            repository.setContent(null);

            // checkout own local "diff" branch
            studentGit.checkout().setCreateBranch(true).setName("diff").call();

            studentGit.reset().setMode(ResetCommand.ResetType.SOFT).setRef(latestHash.getName()).call();
            studentGit.add().addFilepattern(".").call();
            var student = ((StudentParticipation) repository.getParticipation()).getStudent();
            var name = student != null ? student.getName() : ARTEMIS_GIT_NAME;
            var email = student != null ? student.getEmail() : ARTEMIS_GIT_EMAIL;
            studentGit.commit().setMessage("All student changes in one commit").setCommitter(name, email).call();

            // if repo is not closed, it causes weird IO issues when trying to delete the repo again
            // java.io.IOException: Unable to delete file: ...\.git\objects\pack\...
            repository.close();
        }
        catch (EntityNotFoundException | GitAPIException | JGitInternalException ex) {
            log.warn("Cannot reset the repo " + repository.getLocalPath() + " due to the following exception: " + ex.getMessage());
        }
    }

    /**
     * List all files and folders in the repository
     *
     * @param repo Local Repository Object.
     * @return Collection of File objects
     */
    public HashMap<File, FileType> listFilesAndFolders(Repository repo) {
        // Check if list of files is already cached
        if (repo.getContent() == null) {
            Iterator<java.io.File> itr = FileUtils.iterateFilesAndDirs(repo.getLocalPath().toFile(), HiddenFileFilter.VISIBLE, HiddenFileFilter.VISIBLE);
            HashMap<File, FileType> files = new HashMap<>();

            while (itr.hasNext()) {
                File nextFile = new File(itr.next(), repo);
                files.put(nextFile, nextFile.isFile() ? FileType.FILE : FileType.FOLDER);
            }

            // Cache the list of files
            // Avoid expensive rescanning
            repo.setContent(files);
        }
        return repo.getContent();
    }

    /**
     * List all files in the repository. In an empty git repo, this method returns 0.
     *
     * @param repo Local Repository Object.
     * @return Collection of File objects
     */
    public Collection<File> listFiles(Repository repo) {
        // Check if list of files is already cached
        if (repo.getFiles() == null) {
            Iterator<java.io.File> itr = FileUtils.iterateFiles(repo.getLocalPath().toFile(), HiddenFileFilter.VISIBLE, HiddenFileFilter.VISIBLE);
            Collection<File> files = new LinkedList<>();

            while (itr.hasNext()) {
                files.add(new File(itr.next(), repo));
            }

            // Cache the list of files
            // Avoid expensive rescanning
            repo.setFiles(files);
        }
        return repo.getFiles();
    }

    /**
     * Get a specific file by name. Makes sure the file is actually part of the repository.
     *
     * @param repo     Local Repository Object.
     * @param filename String of zje filename (including path)
     * @return The File object
     */
    public Optional<File> getFileByName(Repository repo, String filename) {

        // Makes sure the requested file is part of the scanned list of files.
        // Ensures that it is not possible to do bad things like filename="../../passwd"

        for (File file : listFilesAndFolders(repo).keySet()) {
            if (file.toString().equals(filename)) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if no differences exist between the working-tree, the index, and the current HEAD.
     *
     * @param repo Local Repository Object.
     * @return True if the status is clean
     * @throws GitAPIException if the state of the repository could not be retrieved.
     */
    public Boolean isClean(Repository repo) throws GitAPIException {
        Git git = new Git(repo);
        Status status = git.status().call();
        return status.isClean();
    }

    /**
     * Combines all commits in the selected repo into the first commit, keeping its commit message. Executes a hard reset to remote before the combine to avoid conflicts.
     * 
     * @param repo to combine commits for
     * @throws GitAPIException       on io errors or git exceptions.
     * @throws IllegalStateException if there is no commit in the git repository.
     */
    public void combineAllCommitsIntoInitialCommit(Repository repo) throws IllegalStateException, GitAPIException {
        Git git = new Git(repo);
        try {
            resetToOriginMaster(repo);
            List<RevCommit> commits = StreamSupport.stream(git.log().call().spliterator(), false).collect(Collectors.toList());
            RevCommit firstCommit = commits.get(commits.size() - 1);
            // If there is a first commit, combine all other commits into it.
            if (firstCommit != null) {
                git.reset().setMode(ResetCommand.ResetType.SOFT).setRef(firstCommit.getId().getName()).call();
                git.add().addFilepattern(".").call();
                git.commit().setAmend(true).setMessage(firstCommit.getFullMessage()).call();
                git.push().setForce(true).setCredentialsProvider(new UsernamePasswordCredentialsProvider(GIT_USER, GIT_PASSWORD)).call();
                git.close();
            }
            else {
                // Normally there always has to be a commit, so we throw an error in case none can be found.
                throw new IllegalStateException();
            }
        }
        // This exception occurrs when there was no change to the repo and a commit is done, so it is ignored.
        catch (JGitInternalException ex) {
            log.debug("Did not combine the repository {} as there were no changes to commit.", repo);
        }
        catch (GitAPIException ex) {
            log.error("Could not combine repository {} due to exception: {}", repo, ex);
            throw (ex);
        }
    }

    /**
     * Deletes a local repository folder.
     *
     * @param repo Local Repository Object.
     * @throws IOException if the deletion of the repository failed.
     */
    public void deleteLocalRepository(Repository repo) throws IOException {
        Path repoPath = repo.getLocalPath();
        cachedRepositories.remove(repoPath);
        repo.close();
        FileUtils.deleteDirectory(repoPath.toFile());
        repo.setContent(null);
        log.debug("Deleted Repository at " + repoPath);
    }

    /**
     * Deletes a local repository folder for a Participation (expected in default path).
     *
     * @param participation Participation Object.
     * @throws IOException if the deletion of the repository failed.
     */
    public void deleteLocalRepository(ProgrammingExerciseParticipation participation) throws IOException {
        deleteLocalRepository(participation, REPO_CLONE_PATH);
    }

    /**
     * Deletes a local repository folder for a Participation.
     *
     * @param participation Participation Object.
     * @param targetPath path where the repo is located on disk
     * @throws IOException if the deletion of the repository failed.
     */
    public void deleteLocalRepository(ProgrammingExerciseParticipation participation, String targetPath) throws IOException {
        Path repoPath = new java.io.File(targetPath + folderNameForRepositoryUrl(participation.getRepositoryUrlAsUrl())).toPath();
        cachedRepositories.remove(repoPath);
        if (Files.exists(repoPath)) {
            FileUtils.deleteDirectory(repoPath.toFile());
            log.debug("Deleted Repository at " + repoPath);
        }
    }

    /**
     * Deletes a local repository folder for a repoUrl (expected in default path).
     *
     * @param repoUrl url of the repository.
     * @throws IOException if the deletion of the repository failed.
     */
    public void deleteLocalRepository(URL repoUrl) {
        deleteLocalRepository(repoUrl, REPO_CLONE_PATH);
    }

    /**
     * Deletes a local repository folder for a repoUrl.
     *
     * @param repoUrl url of the repository.
     * @param targetPath path where the repo is located on disk
     * @throws IOException if the deletion of the repository failed.
     */
    public void deleteLocalRepository(URL repoUrl, String targetPath) {
        Path repoPath = new java.io.File(targetPath + folderNameForRepositoryUrl(repoUrl)).toPath();
        cachedRepositories.remove(repoPath);
        if (Files.exists(repoPath)) {
            try {
                FileUtils.deleteDirectory(repoPath.toFile());
                log.info("Deleted Repository at " + repoPath);
            }
            catch (IOException e) {
                log.error("Could not delete repository at " + repoPath, e);
            }
        }
    }

    /**
     * Zip the content of a git repository (expected in default path).
     *
     * @param repo Local Repository Object.
     * @throws IOException if the zipping process failed.
     * @return path to zip file.
     */
    public Path zipRepository(Repository repo) throws IOException {
        return zipRepository(repo, REPO_CLONE_PATH);
    }

    /**
     * Zip the content of a git repository.
     *
     * @param repo Local Repository Object.
     * @param targetPath path where the repo is located on disk
     * @throws IOException if the zipping process failed.
     * @return path to zip file.
     */
    public Path zipRepository(Repository repo, String targetPath) throws IOException {
        String[] repositoryUrlComponents = repo.getParticipation().getRepositoryUrl().split(File.separator);
        ProgrammingExercise exercise = repo.getParticipation().getProgrammingExercise();
        String courseShortName = exercise.getCourse().getShortName().replaceAll("\\s", "");
        // take the last component
        String zipRepoName = courseShortName + "-" + repositoryUrlComponents[repositoryUrlComponents.length - 1] + ".zip";

        Path repoPath = repo.getLocalPath();
        Path zipFilePath = Paths.get(targetPath, "zippedRepos", zipRepoName);
        Files.createDirectories(Paths.get(targetPath, "zippedRepos"));
        try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
            Files.walk(repoPath).filter(path -> !Files.isDirectory(path)).forEach(path -> {
                ZipEntry zipEntry = new ZipEntry(repoPath.relativize(path).toString());
                try {
                    zs.putNextEntry(zipEntry);
                    Files.copy(path, zs);
                    zs.closeEntry();
                }
                catch (Exception e) {
                    log.error("Create zip file error", e);
                }
            });
        }
        return zipFilePath;
    }

    /**
     * Generates the unique local folder name for a given remote repository URL.
     *
     * @param repoUrl URL of the remote repository.
     * @return the folderName as a string.
     */
    public String folderNameForRepositoryUrl(URL repoUrl) {
        String path = repoUrl.getPath();
        path = path.replaceAll(".git$", "");
        path = path.replaceAll("/$", "");
        path = path.replaceAll("^/.*scm/", "");
        return path;
    }

    /**
     * Checks if repo was already checked out and is present on disk
     *
     * @param repoUrl URL of the remote repository.
     * @return True if repo exists on disk
     */
    public boolean repositoryAlreadyExists(URL repoUrl) {
        Path localPath = new java.io.File(REPO_CLONE_PATH + folderNameForRepositoryUrl(repoUrl)).toPath();
        return Files.exists(localPath);
    }
}
