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
import java.util.Iterator;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.web.rest.FileMove;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTO;

/**
 * Service that provides utilites for managing files in a git repository.
 */
@Service
public class RepositoryService {

    private Optional<GitService> gitService;

    private AuthorizationCheckService authCheckService;

    private UserService userService;

    private ParticipationService participationService;

    private ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    public RepositoryService(Optional<GitService> gitService, AuthorizationCheckService authCheckService, UserService userService, ParticipationService participationService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService) {
        this.gitService = gitService;
        this.authCheckService = authCheckService;
        this.userService = userService;
        this.participationService = participationService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
    }

    /**
     * Get the repository content (files and folders).
     * 
     * @param repository
     * @return
     */
    public HashMap<String, FileType> getFiles(Repository repository) {
        Iterator itr = gitService.get().listFilesAndFolders(repository).entrySet().iterator();

        HashMap<String, FileType> fileList = new HashMap<>();

        while (itr.hasNext()) {
            HashMap.Entry<File, FileType> pair = (HashMap.Entry) itr.next();
            fileList.put(pair.getKey().toString(), pair.getValue());
        }

        return fileList;
    }

    /**
     * Get a single file/folder from repository.
     * 
     * @param repository
     * @param filename
     * @return
     * @throws IOException
     */
    public byte[] getFile(Repository repository, String filename) throws IOException {
        Optional<File> file = gitService.get().getFileByName(repository, filename);
        if (!file.isPresent()) {
            throw new FileNotFoundException();
        }
        InputStream inputStream = new FileInputStream(file.get());

        return org.apache.commons.io.IOUtils.toByteArray(inputStream);
    }

    /**
     * Create a file in a repository.
     * 
     * @param repository
     * @param filename
     * @param inputStream
     * @throws IOException
     */
    public void createFile(Repository repository, String filename, InputStream inputStream) throws IOException {
        if (gitService.get().getFileByName(repository, filename).isPresent()) {
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
     * @param repository
     * @param folderName
     * @param inputStream
     * @throws IOException
     */
    public void createFolder(Repository repository, String folderName, InputStream inputStream) throws IOException {
        if (gitService.get().getFileByName(repository, folderName).isPresent()) {
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
     * @param repository
     * @param fileMove
     * @throws FileNotFoundException
     * @throws FileAlreadyExistsException
     * @throws IllegalArgumentException
     */
    public void renameFile(Repository repository, FileMove fileMove) throws FileNotFoundException, FileAlreadyExistsException, IllegalArgumentException {
        Optional<File> file = gitService.get().getFileByName(repository, fileMove.getCurrentFilePath());
        if (!file.isPresent()) {
            throw new FileNotFoundException();
        }
        if (!repository.isValidFile(file.get())) {
            throw new IllegalArgumentException();
        }
        File newFile = new File(new java.io.File(file.get().toPath().getParent().toString() + File.separator + fileMove.getNewFilename()), repository);
        if (gitService.get().getFileByName(repository, newFile.getName()).isPresent()) {
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
     * @param repository
     * @param filename
     * @throws IOException
     */
    public void deleteFile(Repository repository, String filename) throws IOException {

        Optional<File> file = gitService.get().getFileByName(repository, filename);

        if (!file.isPresent()) {
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
     * @param repository
     */
    public void pullChanges(Repository repository) {
        gitService.get().pull(repository);
    }

    /**
     * Commit all staged and unstaged changes in the given repository.
     * 
     * @param repository
     * @throws GitAPIException
     */
    public void commitChanges(Repository repository) throws GitAPIException {
        gitService.get().stageAllChanges(repository);
        gitService.get().commitAndPush(repository, "Changes by Online Editor");
    }

    /**
     * Retrieve the status of the repository. Also pulls the repository.
     * 
     * @param repository
     * @return
     * @throws GitAPIException
     */
    public RepositoryStatusDTO getStatus(Repository repository) throws GitAPIException {
        RepositoryStatusDTO status = new RepositoryStatusDTO();
        status.isClean = gitService.get().isClean(repository);

        if (status.isClean) {
            gitService.get().pull(repository);
        }
        return status;
    }

    /**
     * Retrieve a repository by its name.
     * 
     * @param exercise
     * @param repoUrl
     * @return
     * @throws IOException
     * @throws IllegalAccessException
     * @throws InterruptedException
     */
    public Repository checkoutRepositoryByName(Exercise exercise, URL repoUrl) throws IOException, IllegalAccessException, InterruptedException {
        User user = userService.getUserWithGroupsAndAuthorities();
        Course course = exercise.getCourse();
        boolean hasPermissions = authCheckService.isAtLeastTeachingAssistantInCourse(course, user);
        if (!hasPermissions) {
            throw new IllegalAccessException();
        }
        return gitService.get().getOrCheckoutRepository(repoUrl);
    }

    /**
     * Retrieve a repository by its name.
     * 
     * @param exercise
     * @param repoUrl
     * @return
     * @throws IOException
     * @throws IllegalAccessException
     * @throws InterruptedException
     */
    public Repository checkoutRepositoryByName(Principal principal, Exercise exercise, URL repoUrl) throws IOException, IllegalAccessException, InterruptedException {
        User user = userService.getUserWithGroupsAndAuthorities(principal);
        Course course = exercise.getCourse();
        boolean hasPermissions = authCheckService.isAtLeastTeachingAssistantInCourse(course, user);
        if (!hasPermissions) {
            throw new IllegalAccessException();
        }
        return gitService.get().getOrCheckoutRepository(repoUrl);
    }

    /**
     * Retrieve a repository by a participation connected to it.
     * 
     * @param participation
     * @return
     * @throws IOException
     * @throws IllegalAccessException
     * @throws InterruptedException
     */
    public Repository checkoutRepositoryByParticipation(ProgrammingExerciseStudentParticipation participation) throws IOException, IllegalAccessException, InterruptedException {
        boolean hasAccess = participationService.canAccessParticipation(participation);
        if (!hasAccess) {
            throw new IllegalAccessException();
        }

        return gitService.get().getOrCheckoutRepository(participation);
    }

    /**
     * Retrieve a repository by a participation connected to it.
     *
     * @param participation
     * @return
     * @throws IOException
     * @throws IllegalAccessException
     * @throws InterruptedException
     */
    public Repository checkoutRepositoryByParticipation(ProgrammingExerciseParticipation participation) throws IOException, IllegalAccessException, InterruptedException {
        boolean hasAccess = programmingExerciseParticipationService.canAccessParticipation(participation);
        if (!hasAccess) {
            throw new IllegalAccessException();
        }

        return gitService.get().getOrCheckoutRepository(participation);
    }
}
