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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Optional;

import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.web.rest.FileMove;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTO;

@Service
public class RepositoryService {

    private Optional<GitService> gitService;

    private AuthorizationCheckService authCheckService;

    private ParticipationService participationService;

    private UserService userService;

    public RepositoryService(Optional<GitService> gitService, AuthorizationCheckService authCheckService, ParticipationService participationService, UserService userService) {
        this.gitService = gitService;
        this.authCheckService = authCheckService;
        this.participationService = participationService;
        this.userService = userService;
    }

    public HashMap<String, FileType> getFiles(Repository repository) {
        Iterator itr = gitService.get().listFilesAndFolders(repository).entrySet().iterator();

        HashMap<String, FileType> fileList = new HashMap<>();

        while (itr.hasNext()) {
            HashMap.Entry<File, FileType> pair = (HashMap.Entry) itr.next();
            fileList.put(pair.getKey().toString(), pair.getValue());
        }

        return fileList;
    }

    public byte[] getFile(Repository repository, String filename) throws IOException {
        Optional<File> file = gitService.get().getFileByName(repository, filename);
        if (!file.isPresent()) {
            throw new FileNotFoundException();
        }
        InputStream inputStream = new FileInputStream(file.get());

        return org.apache.commons.io.IOUtils.toByteArray(inputStream);
    }

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

    public void pullChanges(Repository repository) {
        gitService.get().pull(repository);
    }

    public void commitChanges(Repository repository) throws GitAPIException {
        gitService.get().stageAllChanges(repository);
        gitService.get().commitAndPush(repository, "Changes by Online Editor");
    }

    public RepositoryStatusDTO getStatus(Repository repository) throws GitAPIException {
        RepositoryStatusDTO status = new RepositoryStatusDTO();
        status.isClean = gitService.get().isClean(repository);

        if (status.isClean) {
            gitService.get().pull(repository);
        }
        return status;
    }

    public Repository checkoutRepositoryByName(Exercise exercise, URL repoUrl) throws IOException, IllegalAccessException, InterruptedException {
        User user = userService.getUserWithGroupsAndAuthorities();
        Course course = exercise.getCourse();
        boolean hasPermissions = authCheckService.isTeachingAssistantInCourse(course, user) || authCheckService.isInstructorInCourse(course, user) || authCheckService.isAdmin();
        if (!hasPermissions) {
            throw new IllegalAccessException();
        }
        return gitService.get().getOrCheckoutRepository(repoUrl);

    }

    public Repository checkoutRepositoryByParticipation(Participation participation) throws IOException, IllegalAccessException, InterruptedException {
        boolean hasAccess = canAccessParticipation(participation);
        if (!hasAccess) {
            throw new IllegalAccessException();
        }

        return gitService.get().getOrCheckoutRepository(participation);
    }

    @Nullable
    public boolean canAccessParticipation(Participation participation) {
        if (!userHasPermissions(participation))
            return false;

        if (!Optional.ofNullable(participation).isPresent()) {
            return false;
        }
        return true;
    }

    private boolean userHasPermissions(Participation participation) {
        if (!authCheckService.isOwnerOfParticipation(participation)) {
            // if the user is not the owner of the participation, the user can only see it in case he is
            // a teaching assistant or an instructor of the course, or in case he is admin
            User user = userService.getUserWithGroupsAndAuthorities();
            Course course = participation.getExercise().getCourse();
            return authCheckService.isTeachingAssistantInCourse(course, user) || authCheckService.isInstructorInCourse(course, user) || authCheckService.isAdmin();
        }
        return true;
    }
}
