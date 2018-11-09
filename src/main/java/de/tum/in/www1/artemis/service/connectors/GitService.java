package de.tum.in.www1.artemis.service.connectors;

import de.tum.in.www1.artemis.domain.File;
import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.exception.GitException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.HiddenFileFilter;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


@Service
public class GitService {

    private final Logger log = LoggerFactory.getLogger(GitService.class);

    @Value("${artemis.version-control.user}")
    private String GIT_USER;

    @Value("${artemis.version-control.secret}")
    private String GIT_PASSWORD;

    @Value("${artemis.repo-clone-path}")
    private String REPO_CLONE_PATH;

    @Value("${artemis.git.name}")
    private String GIT_NAME;

    @Value("${artemis.git.email}")
    private String GIT_EMAIL;

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
     * Get the local repository for a given participation.
     * If the local repo does not exist yet, it will be checked out.
     *
     * @param participation Participation the remote repository belongs to.
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public Repository getOrCheckoutRepository(Participation participation) throws IOException, InterruptedException {
        URL repoUrl = participation.getRepositoryUrlAsUrl();
        Repository repository = getOrCheckoutRepository(repoUrl);
        repository.setParticipation(participation);
        return repository;
    }




    /**
     * Get the local repository for a given remote repository URL.
     * If the local repo does not exist yet, it will be checked out.
     *
     * @param repoUrl The remote repository.
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public Repository getOrCheckoutRepository(URL repoUrl) throws IOException, InterruptedException {

        Path localPath = new java.io.File(REPO_CLONE_PATH + folderNameForRepositoryUrl(repoUrl)).toPath();

        // check if Repository object already created and available in cachedRepositories
        if (cachedRepositories.containsKey(localPath)) {
            // in this case we pull for changes to make sure the Git repo is up to date
            Repository repository = cachedRepositories.get(localPath);
            pull(repository);
            return repository;
        }

        // make sure that multiple clone operations for the same repository cannot happen at the same time
        int numberOfAttempts = 5;
        while (cloneInProgressOperations.containsKey(localPath)) {
            log.warn("Clone is already in progress. This will lead to an error. Wait for a second");
            Thread.sleep(1000);
            if (numberOfAttempts == 0) {
                throw new GitException("Cannot clone the same repository multiple times");
            } else {
              numberOfAttempts--;
            }
        }
        boolean shouldPullChanges = false;

        // Check if the repository is already checked out on the server
        if (!Files.exists(localPath)) {
            // Repository is not yet available on the server
            // We need to check it out from the remote repository
            try {
                log.info("Cloning from " + repoUrl + " to " + localPath);
                cloneInProgressOperations.put(localPath, localPath);
                Git result = Git.cloneRepository()
                    .setURI(repoUrl.toString())
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(GIT_USER, GIT_PASSWORD))
                    .setDirectory(localPath.toFile())
                    .call();
                result.close();
            }
            catch (GitAPIException | RuntimeException e) {
                log.error("Exception during clone " + e);
                //cleanup the folder to avoid problems in the future
                localPath.toFile().delete();
                throw new GitException(e);
            }
            finally {
                //make sure that cloneInProgress is released
                log.info("Remove " + localPath + " from cloneInProgressMap: ");
                cloneInProgressOperations.remove(localPath);
            }
        }
        else {
            log.debug("Repository at " + localPath + " already exists");
            // in this case we pull for changes to make sure the Git repo is up to date
            shouldPullChanges = true;
        }

        // Open the repository from the filesystem
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        builder.setGitDir(new java.io.File(localPath + "/.git"))
            .readEnvironment() // scan environment GIT_* variables
            .findGitDir()
            .setup();


        // Create the JGit repository object
        Repository repository = new Repository(builder);
        repository.setLocalPath(localPath);

        if (shouldPullChanges) {
            pull(repository);
        }

        // Cache the JGit repository object for later use
        // Avoids the expensive re-opening of local repositories
        cachedRepositories.put(localPath, repository);

        return repository;
    }

    /**
     * Commits with the given message into the repository and pushes it to the remote.
     *
     * @param repo Local Repository Object.
     * @param message Commit Message
     * @throws GitAPIException
     */
    public void commitAndPush(Repository repo, String message) throws GitAPIException {
        Git git = new Git(repo);
        git.commit().setMessage(message).setAllowEmpty(true).setCommitter(GIT_NAME, GIT_EMAIL).call();
        git.push().setCredentialsProvider(new UsernamePasswordCredentialsProvider(GIT_USER, GIT_PASSWORD)).call();
        git.close();
    }

    /**
     * Stage all files in the repo including new files.
     *
     * @param repo
     * @throws GitAPIException
     */
    public void stageAllChanges(Repository repo) throws GitAPIException {
        Git git = new Git(repo);
        // stage deleted files:  http://stackoverflow.com/a/35601677/4013020
        git.add().setUpdate(true).addFilepattern(".").call();
        // stage new files
        git.add().addFilepattern(".").call();
        git.close();
    }

    /**
     * Pulls from remote repository.
     *
     * @param repo Local Repository Object.
     * @return The PullResult which contains FetchResult and MergeResult.
     * @throws GitAPIException
     */
    public PullResult pull(Repository repo) {
        try {
            Git git = new Git(repo);
            // flush cache of files
            repo.setFiles(null);
            return git.pull().setCredentialsProvider(new UsernamePasswordCredentialsProvider(GIT_USER, GIT_PASSWORD)).call();
        }
        catch (GitAPIException ex) {
            log.error("Cannot pull the repo " + repo.getLocalPath() + " due to the following exception: " + ex);
            //TODO: we should send this error to the client and let the user handle it there, e.g. by choosing to reset the repository
        }
        return null;
    }

    /**
     * List all files in the repository
     *
     * @param repo Local Repository Object.
     * @return Collection of File objects
     */
    public Collection<File> listFiles(Repository repo) {
        // Check if list of files is already cached
        if(repo.getFiles() == null) {
            Iterator<java.io.File> itr = FileUtils.iterateFiles(repo.getLocalPath().toFile(), HiddenFileFilter.VISIBLE, HiddenFileFilter.VISIBLE);
            Collection<File> files = new LinkedList<>();

            while(itr.hasNext()) {
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
     * @param repo Local Repository Object.
     * @param filename String of zje filename (including path)
     * @return The File object
     */
    public Optional<File> getFileByName(Repository repo, String filename) {

        // Makes sure the requested file is part of the scanned list of files.
        // Ensures that it is not possible to do bad things like filename="../../passwd"

        for (File file : listFiles(repo)) {
            if (file.toString().equals(filename)) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }


    /**
     * Checks if no differences exist between the working-tree, the index, and the current HEAD.
     *
     * @param repo  Local Repository Object.
     * @return True if the status is clean
     * @throws GitAPIException
     */
    public Boolean isClean(Repository repo) throws GitAPIException {
        Git git = new Git(repo);
        Status status = git.status().call();
        return status.isClean();
    }


    /**
     * Deletes a local repository folder.
     *
     * @param repo Local Repository Object.
     * @throws IOException
     */
    public void deleteLocalRepository(Repository repo) throws IOException {
        Path repoPath = repo.getLocalPath();
        cachedRepositories.remove(repoPath);
        repo.close();
        FileUtils.deleteDirectory(repoPath.toFile());
        repo.setFiles(null);
        log.debug("Deleted Repository at " + repoPath);
    }


    /**
     * Deletes a local repository folder for a Participation.
     *
     * @param participation Participation Object.
     * @throws IOException
     */
    public void deleteLocalRepository(Participation participation) throws IOException {
        Path repoPath = new java.io.File(REPO_CLONE_PATH + folderNameForRepositoryUrl(participation.getRepositoryUrlAsUrl())).toPath();
        cachedRepositories.remove(repoPath);
        if (Files.exists(repoPath)) {
            FileUtils.deleteDirectory(repoPath.toFile());
            log.info("Deleted Repository at " + repoPath);
        }
    }

    public void deleteLocalRepository(URL repoUrl) {
        Path repoPath = new java.io.File(REPO_CLONE_PATH + folderNameForRepositoryUrl(repoUrl)).toPath();
        cachedRepositories.remove(repoPath);
        if (Files.exists(repoPath)) {
            try {
                FileUtils.deleteDirectory(repoPath.toFile());
                log.info("Deleted Repository at " + repoPath);
            } catch (IOException e) {
                log.error("Could not delete repository at " + repoPath, e);
            }
        }
    }

    public Path zipRepository(Repository repo) throws IOException {
        String zipRepoName = repo.getParticipation().getExercise().getCourse().getTitle() + "-" + repo.getParticipation().getExercise().getTitle() + "-" + repo.getParticipation().getStudent().getLogin() + ".zip";
        Path repoPath = repo.getLocalPath();
        Path zipFilePath = Paths.get(REPO_CLONE_PATH, "zippedRepos", zipRepoName);
        Files.createDirectories(Paths.get(REPO_CLONE_PATH, "zippedRepos"));
        try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
            Files.walk(repoPath)
                .filter(path -> !Files.isDirectory(path))
                .forEach(path -> {
                    ZipEntry zipEntry = new ZipEntry(repoPath.relativize(path).toString());
                    try {
                        zs.putNextEntry(zipEntry);
                        Files.copy(path, zs);
                        zs.closeEntry();
                    } catch (Exception e) {
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
     * @return
     */
    public String folderNameForRepositoryUrl(URL repoUrl) {
        String path = repoUrl.getPath();
        path = path.replaceAll(".git$", "");
        path = path.replaceAll("/$", "");
        path = path.replaceAll("^/", "");
        path = path.replaceAll("^scm/", "");
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
        if (Files.exists(localPath)) {
            return true;
        }
        return false;
    }
}
