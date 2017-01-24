package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.domain.Repository;
import de.tum.in.www1.exerciseapp.domain.File;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.HiddenFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


@Service
public class GitService {

    private final Logger log = LoggerFactory.getLogger(GitService.class);

    @Value("${exerciseapp.bitbucket.user}")
    private String GIT_USER;

    @Value("${exerciseapp.bitbucket.password}")
    private String GIT_PASSWORD;

    @Value("${exerciseapp.repo-clone-path}")
    private String REPO_CLONE_PATH;

    @Value("${exerciseapp.git.name}")
    private String GIT_NAME;

    @Value("${exerciseapp.git.email}")
    private String GIT_EMAIL;


    private HashMap<Path, Repository> cachedRepositories = new HashMap<>();


    /**
     * Get the local repository for a given remote repository URL.
     * If the local repo does not exist yet, it will be checked out.
     *
     * @param participation Participation the remote repository belongs to.
     * @return
     * @throws IOException
     * @throws GitAPIException
     */
    public Repository getOrCheckoutRepository(Participation participation) throws IOException, GitAPIException {
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
     * @throws GitAPIException
     */
    public Repository getOrCheckoutRepository(URL repoUrl) throws IOException, GitAPIException {
        Path localPath = new java.io.File(REPO_CLONE_PATH + folderNameForRepositoryUrl(repoUrl)).toPath();

        // check if Repository object already created
        if (cachedRepositories.containsKey(localPath)) {
            return cachedRepositories.get(localPath);
        }

        if (!Files.exists(localPath)) {
            log.debug("Cloning from " + repoUrl + " to " + localPath);
            Git.cloneRepository()
                .setURI(repoUrl.toString())
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(GIT_USER, GIT_PASSWORD))
                .setDirectory(localPath.toFile())
                .call();
        } else {
            log.debug("Repository at " + localPath + " already exists");
        }


        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        builder.setGitDir(new java.io.File(localPath + "/.git"))
            .readEnvironment() // scan environment GIT_* variables
            .findGitDir()
            .setup();

        Repository repository = new Repository(builder);
        repository.setLocalPath(localPath);

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
    }


    /**
     * Stage all files in the repo including new files.
     *
     * @param repo
     * @throws GitAPIException
     */
    public void stageAllChanges(Repository repo) throws GitAPIException {
        Git git = new Git(repo);
        git.add().addFilepattern(".").call();
    }


    /**
     * Pulls from remote repository.
     *
     * @param repo Local Repository Object.
     * @return The PullResult which contains FetchResult and MergeResult.
     * @throws GitAPIException
     */
    public PullResult pull(Repository repo) throws GitAPIException {
        Git git = new Git(repo);
        // flush cache
        repo.setFiles(null);
        return git.pull().setCredentialsProvider(new UsernamePasswordCredentialsProvider(GIT_USER, GIT_PASSWORD)).call();
    }


    /**
     * List all files in the repository
     *
     * @param repo Local Repository Object.
     * @return Collection of File objects
     */
    public Collection<File> listFiles(Repository repo) {
        if(repo.getFiles() == null) {
            Iterator<java.io.File> itr = FileUtils.iterateFiles(repo.getLocalPath().toFile(), HiddenFileFilter.VISIBLE, HiddenFileFilter.VISIBLE);
            Collection<File> files = new LinkedList<>();

            while(itr.hasNext()) {
                files.add(new File(itr.next(), repo));
            }

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
        Iterator<File> itr = listFiles(repo).iterator();

        while (itr.hasNext()) {
            File file = itr.next();
            if(file.toString().equals(filename)) {
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
            log.debug("Deleted Repository at " + repoPath);
        }
    }



    /**
     * Generates the unique local folder name for a given remote repository URL.
     *
     * @param repoUrl URL of the remote repository.
     * @return
     * @throws MalformedURLException
     */
    public String folderNameForRepositoryUrl(URL repoUrl) throws MalformedURLException {
        String path = repoUrl.getPath();
        path = path.replaceAll(".git$", "");
        path = path.replaceAll("/$", "");
        path = path.replaceAll("^/", "");
        path = path.replaceAll("^scm/", "");
        return path;
    }


}
