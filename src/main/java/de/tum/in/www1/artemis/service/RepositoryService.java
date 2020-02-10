package de.tum.in.www1.artemis.service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.web.rest.dto.FileMove;

/**
 * Service that provides utilites for managing files in a git repository.
 */
@Service
public class RepositoryService {

    private GitService gitService;

    private AuthorizationCheckService authCheckService;

    private UserService userService;

    private ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    public RepositoryService(GitService gitService, AuthorizationCheckService authCheckService, UserService userService, ParticipationService participationService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService) {
        this.gitService = gitService;
        this.authCheckService = authCheckService;
        this.userService = userService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
    }

    /**
     * Get the repository content (files and folders).
     * 
     * @param repository VCS repository to get files for.
     * @return a map of files with the information if they are a file or a folder.
     */
    public Map<String, FileType> getFiles(Repository repository) {
        var iterator = gitService.listFilesAndFolders(repository).entrySet().iterator();

        Map<String, FileType> fileList = new HashMap<>();

        while (iterator.hasNext()) {
            Map.Entry<File, FileType> pair = (Map.Entry<File, FileType>) iterator.next();
            fileList.put(pair.getKey().toString(), pair.getValue());
        }

        return fileList;
    }

    /**
     * Get a single file/folder from repository.
     * 
     * @param repository in which the requested file is located.
     * @param filename of the file to be retrieved.
     * @return The file if found or throw an exception.
     * @throws IOException if the file can't be found, is corrupt, etc.
     */
    public byte[] getFile(Repository repository, String filename) throws IOException {
        Optional<File> file = gitService.getFileByName(repository, filename);
        if (file.isEmpty()) {
            throw new FileNotFoundException();
        }
        InputStream inputStream = new FileInputStream(file.get());

        return org.apache.commons.io.IOUtils.toByteArray(inputStream);
    }

    /**
     * Create a file in a repository.
     * 
     * @param repository in which the file should be created.
     * @param filename of the file to be created.
     * @param inputStream byte representation of the file to be created.
     * @throws IOException if the inputStream is corrupt, the file can't be stored, the repository is unavailable, etc.
     */
    public void createFile(Repository repository, String filename, InputStream inputStream) throws IOException {
        if (gitService.getFileByName(repository, filename).isPresent()) {
            throw new FileAlreadyExistsException("file already exists");
        }

        File file = new File(new java.io.File(repository.getLocalPath() + File.separator + filename), repository);
        if (!repository.isValidFile(file)) {
            throw new IllegalArgumentException();
        }

        Files.copy(inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        repository.setContent(null); // invalidate cache
    }

    /**
     * Create a folder in a repository.
     *
     * @param repository in which the folder should be created.
     * @param folderName of the folder to be created.
     * @param inputStream byte representation of the folder to be created.
     * @throws IOException if the inputStream is corrupt, the folder can't be stored, the repository is unavailable, etc.
     */
    public void createFolder(Repository repository, String folderName, InputStream inputStream) throws IOException {
        if (gitService.getFileByName(repository, folderName).isPresent()) {
            throw new FileAlreadyExistsException("file already exists");
        }
        File file = new File(new java.io.File(repository.getLocalPath() + File.separator + folderName), repository);
        if (!repository.isValidFile(file)) {
            throw new IllegalArgumentException();
        }
        Files.createDirectory(Paths.get(repository.getLocalPath() + File.separator + folderName));
        // We need to add an empty keep file so that the folder can be added to the git repository
        File keep = new File(new java.io.File(repository.getLocalPath() + File.separator + folderName + File.separator + ".keep"), repository);
        Files.copy(inputStream, keep.toPath(), StandardCopyOption.REPLACE_EXISTING);
        repository.setContent(null); // invalidate cache
    }

    /**
     * Rename a file in a repository.
     * 
     * @param repository in which the file is located.
     * @param fileMove dto for describing the old and the new filename.
     * @throws FileNotFoundException if the file to rename is not available.
     * @throws FileAlreadyExistsException if the new filename is already taken.
     * @throws IllegalArgumentException if the new filename is not allowed (e.g. contains .. or /../)
     */
    public void renameFile(Repository repository, FileMove fileMove) throws FileNotFoundException, FileAlreadyExistsException, IllegalArgumentException {
        Optional<File> file = gitService.getFileByName(repository, fileMove.getCurrentFilePath());
        if (file.isEmpty()) {
            throw new FileNotFoundException();
        }
        if (!repository.isValidFile(file.get())) {
            throw new IllegalArgumentException();
        }
        File newFile = new File(new java.io.File(file.get().toPath().getParent().toString() + File.separator + fileMove.getNewFilename()), repository);
        if (gitService.getFileByName(repository, newFile.getName()).isPresent()) {
            throw new FileAlreadyExistsException("file already exists");
        }
        boolean isRenamed = file.get().renameTo(newFile);
        if (!isRenamed) {
            throw new FileNotFoundException();
        }

        repository.setContent(null); // invalidate cache
    }

    /**
     * Delete a file in a repository.
     * 
     * @param repository in which the file to delete is located.
     * @param filename to delete.
     * @throws IOException if the file can't be deleted.
     * @throws FileNotFoundException if the file can't be found.
     * @throws IllegalArgumentException if the filename contains forbidden sequences (e.g. .. or /../).
     */
    public void deleteFile(Repository repository, String filename) throws IllegalArgumentException, IOException {

        Optional<File> file = gitService.getFileByName(repository, filename);

        if (file.isEmpty()) {
            throw new FileNotFoundException();
        }
        if (!repository.isValidFile(file.get())) {
            throw new IllegalArgumentException();
        }
        if (file.get().isFile()) {
            Files.delete(file.get().toPath());
        }
        else {
            FileUtils.deleteDirectory(file.get());
        }
        repository.setContent(null); // invalidate cache
    }

    /**
     * Pull from a git repository.
     * 
     * @param repository for which to pull the current state of the remote.
     */
    public void pullChanges(Repository repository) {
        gitService.pullIgnoreConflicts(repository);
    }

    /**
     * Commit all staged and unstaged changes in the given repository.
     * 
     * @param repository for which to execute the commit.
     * @param user the user who has committed the changes in the online editor
     * @throws GitAPIException if the staging/committing process fails.
     */
    public void commitChanges(Repository repository, User user) throws GitAPIException {
        gitService.stageAllChanges(repository);
        gitService.commitAndPush(repository, "Changes by Online Editor", user);
    }

    /**
     * Retrieve the status of the repository. Also pulls the repository.
     * 
     * @param repositoryUrl of the repository to check the status for.
     * @return a dto to determine the status of the repository.
     * @throws InterruptedException if the repository can't be checked out on the server.
     * @throws IOException if the repository status can't be retrieved.
     * @throws GitAPIException if the repository status can't be retrieved.
     */
    public boolean isClean(URL repositoryUrl) throws IOException, GitAPIException, InterruptedException {
        Repository repository = gitService.getOrCheckoutRepository(repositoryUrl, true);
        return gitService.isClean(repository);
    }

    /**
     * Retrieve a repository by its name.
     * 
     * @param exercise to which the repository belongs.
     * @param repoUrl of the repository on the server.
     * @param pullOnCheckout if true pulls after checking out the git repository.
     * @return the repository if available.
     * @throws IOException if the repository can't be checked out.
     * @throws GitAPIException if the repository can't be checked out.
     * @throws IllegalAccessException if the user does not have access to the repository.
     * @throws InterruptedException if the repository can't be checked out.
     */
    public Repository checkoutRepositoryByName(Exercise exercise, URL repoUrl, boolean pullOnCheckout)
            throws IOException, IllegalAccessException, InterruptedException, GitAPIException {
        User user = userService.getUserWithGroupsAndAuthorities();
        Course course = exercise.getCourse();
        boolean hasPermissions = authCheckService.isAtLeastTeachingAssistantInCourse(course, user);
        if (!hasPermissions) {
            throw new IllegalAccessException();
        }
        return gitService.getOrCheckoutRepository(repoUrl, pullOnCheckout);
    }

    /**
     * Retrieve a repository by its name.
     *
     * @param principal entity used for permission checking.
     * @param exercise to which the repository belongs.
     * @param repoUrl of the repository on the server.
     * @return the repository if available.
     * @throws GitAPIException if the repository can't be checked out.
     * @throws IllegalAccessException if the user does not have access to the repository.
     * @throws InterruptedException if the repository can't be checked out.
     */
    public Repository checkoutRepositoryByName(Principal principal, Exercise exercise, URL repoUrl) throws IllegalAccessException, InterruptedException, GitAPIException {
        User user = userService.getUserWithGroupsAndAuthorities(principal);
        Course course = exercise.getCourse();
        boolean hasPermissions = authCheckService.isAtLeastTeachingAssistantInCourse(course, user);
        if (!hasPermissions) {
            throw new IllegalAccessException();
        }
        return gitService.getOrCheckoutRepository(repoUrl, true);
    }

    /**
     * Retrieve a repository by the participation connected to it.
     *
     * @param participation to which the repository belongs.
     * @return the repository if available.
     * @throws IOException if the repository can't be checked out.
     * @throws GitAPIException if the repository can't be checked out.
     * @throws IllegalAccessException if the user does not have access to the repository.
     * @throws InterruptedException if the repository can't be checked out.
     */
    public Repository checkoutRepositoryByParticipation(ProgrammingExerciseParticipation participation)
            throws IOException, IllegalAccessException, GitAPIException, InterruptedException {
        boolean hasAccess = programmingExerciseParticipationService.canAccessParticipation(participation);
        if (!hasAccess) {
            throw new IllegalAccessException();
        }

        return gitService.getOrCheckoutRepository(participation);
    }
}
