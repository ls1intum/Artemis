package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.programming.domain.File;
import de.tum.cit.aet.artemis.programming.domain.FileType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.service.FileService;
import de.tum.cit.aet.artemis.service.ProfileService;
import de.tum.cit.aet.artemis.programming.dto.FileMove;

/**
 * Service that provides utilities for managing files in a git repository.
 */
@Profile(PROFILE_CORE)
@Service
public class RepositoryService {

    private final GitService gitService;

    private final ProfileService profileService;

    private static final Logger log = LoggerFactory.getLogger(RepositoryService.class);

    public RepositoryService(GitService gitService, ProfileService profileService) {
        this.gitService = gitService;
        this.profileService = profileService;
    }

    /**
     * Gets a participation as a {@link ProgrammingExerciseParticipation} if it belongs to the specified programming exercise.
     *
     * @param programmingExerciseId The ID of the programming exercise to which the participation belongs.
     * @param participation         The participation which to check and retrieve.
     * @param entityName            The name of the entity to include in the exception message if an error occurs.
     * @return The participation as a {@link ProgrammingExerciseParticipation} if it belongs to the specified programming exercise.
     *         An exception is thrown if the participation does not belong to the specified exercise or is not a programming exercise participation.
     */
    public ProgrammingExerciseParticipation getAsProgrammingExerciseParticipationOfExerciseElseThrow(long programmingExerciseId, Participation participation, String entityName) {
        if (!participation.getExercise().getId().equals(programmingExerciseId)) {
            throw new ConflictException("The specified participation does not belong to the specified exercise.", entityName, "exerciseIdsMismatch");
        }
        if (!(participation instanceof ProgrammingExerciseParticipation programmingExerciseParticipation)) {
            throw new ConflictException("The specified participation does not belong to a programming exercise.", entityName, "notProgrammingExerciseParticipation");
        }
        return programmingExerciseParticipation;
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
            Map.Entry<File, FileType> pair = iterator.next();
            fileList.put(pair.getKey().toString(), pair.getValue());
        }

        return fileList;
    }

    /**
     * Retrieves the content of files at a specific commit in a given repository.
     *
     * @param programmingExercise The programming exercise which contains the VCS repository. This is used to determine the repository URI for TESTS repositories.
     * @param commitId            The commit identifier from which to extract file contents.
     * @param repositoryType      The type of the repository (e.g., TESTS, TEMPLATE, etc.). Relevant for participations of type TESTS.
     * @param participation       The participation related to the repository.
     * @return A map where each key is a file path and each value is the content of the file as a String. This represents the state of the repository at the given commit.
     * @throws IOException     If an I/O error occurs during the file content retrieval process. This could be due to issues with file access, network problems, etc.
     * @throws GitAPIException If an error occurs while interacting with the Git repository. This could be due to issues with repository access, invalid commit ids, etc.
     */
    public Map<String, String> getFilesContentAtCommit(ProgrammingExercise programmingExercise, String commitId, RepositoryType repositoryType,
            ProgrammingExerciseParticipation participation) throws IOException, GitAPIException {
        // Check if local VCS is active
        if (profileService.isLocalVcsActive()) {
            log.debug("Using local VCS for getting files at commit {} for participation {}", commitId, participation.getId());
            // If local VCS is active, operate directly on the bare repository
            var repoUri = repositoryType == RepositoryType.TESTS ? programmingExercise.getVcsTestRepositoryUri() : participation.getVcsRepositoryUri();
            Repository repository = gitService.getBareRepository(repoUri);
            return getFilesContentFromBareRepository(repository, commitId);
        }
        else {
            log.debug("Checking out repo to get files at commit {} for participation {}", commitId, participation.getId());
            Repository repository;
            if (repositoryType == RepositoryType.TESTS) {
                repository = gitService.checkoutRepositoryAtCommit(programmingExercise.getVcsTestRepositoryUri(), commitId, true);
            }
            else {
                // For other repository types, check out the repository at the commit
                repository = gitService.checkoutRepositoryAtCommit(participation.getVcsRepositoryUri(), commitId, true);
            }
            // Get the files content from the working copy of the repository
            Map<String, String> filesWithContent = getFilesContentFromWorkingCopy(repository);
            // Switch back to the default branch head
            gitService.switchBackToDefaultBranchHead(repository);
            return filesWithContent;
        }
    }

    /**
     * Retrieves a mapping of file paths to their contents within a specified repository.
     * This method filters out all non-file type entries and reads the content of each file.
     * Note: If an I/O error occurs reading any file, this exception is caught internally and logged.
     *
     * @param repository The repository from which files are to be fetched.
     * @return A {@link Map} where each key is a file path (as a {@link String}) and each value is the content of the file (also as a {@link String}).
     *         The map includes only those files that could successfully have their contents read; files that cause an IOException are logged but not included.
     */
    public Map<String, String> getFilesContentFromWorkingCopy(Repository repository) {
        var files = gitService.listFilesAndFolders(repository).entrySet().stream().filter(entry -> entry.getValue() == FileType.FILE).map(Map.Entry::getKey).toList();
        Map<String, String> fileListWithContent = new HashMap<>();

        files.forEach(file -> {
            try {
                fileListWithContent.put(file.toString(), FileUtils.readFileToString(file, StandardCharsets.UTF_8));
            }
            catch (IOException e) {
                log.error("Content of file: {} could not be loaded and throws the following error: {}", file, e.getMessage());
            }
        });
        return fileListWithContent;
    }

    /**
     * Retrieves a mapping of file paths to their content for a specific commit in a bare Git repository.
     * This method extracts file content by traversing the repository's tree from the specified commit.
     * It is primarily designed to read text files, converting the binary content to a UTF-8 string.
     * Usage of this method with binary files may lead to data corruption or misrepresentation as
     * binary data does not convert cleanly into UTF-8 strings.
     *
     * @param repository The repository from which file contents are to be retrieved. Must be a bare repository.
     * @param commitId   The commit identifier from which to extract file contents.
     * @return A {@link Map} where each key is a file path and each value is the content of the file as a {@link String}.
     *         The content is encoded in UTF-8 and may not represent binary data accurately.
     * @throws IOException If an I/O error occurs during the file content retrieval process, including issues with
     *                         opening and reading the file stream.
     */
    public Map<String, String> getFilesContentFromBareRepository(Repository repository, String commitId) throws IOException {
        RevWalk revWalk = new RevWalk(repository);
        RevCommit commit = revWalk.parseCommit(repository.resolve(commitId));
        RevTree tree = commit.getTree();
        // Initialize your map to store file paths and their contents
        Map<String, String> filesWithContent = new HashMap<>();

        TreeWalk treeWalk = new TreeWalk(repository);
        treeWalk.addTree(tree);
        treeWalk.setRecursive(true);
        while (treeWalk.next()) {
            String path = treeWalk.getPathString();
            ObjectId objectId = treeWalk.getObjectId(0);

            // TODO: In the future, it may make sense to exclude binary files here.
            // Open the object stream to read the file content
            try (InputStream inputStream = repository.open(objectId).openStream()) {
                byte[] bytes = inputStream.readAllBytes(); // Read all bytes at once
                String content = new String(bytes, StandardCharsets.UTF_8); // Convert bytes to string with UTF-8 encoding

                // Put the path and corresponding file content into the map
                filesWithContent.put(path, content);
            }
        }
        revWalk.close();
        return filesWithContent;
    }

    /**
     * Deletes all content in the repository except the .git folder
     *
     * @param repository the repository the content should be deleted from
     **/
    public void deleteAllContentInRepository(Repository repository) throws IOException {
        try (var content = Files.list(repository.getLocalPath())) {
            content.filter(path -> !".git".equals(path.getFileName().toString())).forEach(path -> {
                try {
                    FileSystemUtils.deleteRecursively(path);
                }
                catch (IOException e) {
                    log.error("Error while deleting file {}", path, e);
                }
            });
        }
    }

    /**
     * Get a single file/folder from repository.
     *
     * @param repository in which the requested file is located.
     * @param filename   of the file to be retrieved.
     * @return The file if found or throw an exception.
     * @throws IOException if the file can't be found, is corrupt, etc.
     */
    public byte[] getFile(Repository repository, String filename) throws IOException {
        Optional<File> file = gitService.getFileByName(repository, filename);
        if (file.isEmpty()) {
            throw new FileNotFoundException();
        }
        InputStream inputStream = new FileInputStream(file.get());
        byte[] fileInBytes = org.apache.commons.io.IOUtils.toByteArray(inputStream);
        inputStream.close();
        return fileInBytes;
    }

    /**
     * Get the mimetype of a single file from the repository
     *
     * @param repository in which the requested file is located.
     * @param filename   of the file to be probed.
     * @return The mimetype of the file if found or throw an exception.
     * @throws IOException if the file can't be found, is corrupt, etc.
     */
    public String getFileType(Repository repository, String filename) throws IOException {
        Optional<File> file = gitService.getFileByName(repository, filename);
        if (file.isEmpty()) {
            throw new FileNotFoundException();
        }
        String type = Files.probeContentType(file.get().toPath());
        // fallback to text/plain in case content type can not be determined
        if (type == null) {
            return "text/plain";
        }
        return type;
    }

    /**
     * Gets the files of the repository and checks whether they were changed during a student participation.
     * Compares the files from the students' repository with the files of the template repository.
     *
     * @param repository         the students' repository with possibly new files and changed files
     * @param templateRepository the template repository with default files on which the student started working on
     * @return a map of files with the information if they were changed/are new.
     */
    public Map<String, Boolean> getFilesWithInformationAboutChange(Repository repository, Repository templateRepository) {
        Map<String, Boolean> filesWithInformationAboutChange = new HashMap<>();

        var repoFiles = gitService.listFilesAndFolders(repository).entrySet().stream().filter(entry -> entry.getValue() == FileType.FILE).map(Map.Entry::getKey).toList();

        Map<String, File> templateRepoFiles = gitService.listFilesAndFolders(templateRepository).entrySet().stream().filter(entry -> entry.getValue() == FileType.FILE)
                .collect(Collectors.toMap(entry -> entry.getKey().toString(), Map.Entry::getKey));

        repoFiles.forEach(file -> {
            String fileName = file.toString();

            if (templateRepoFiles.get(fileName) == null) {
                filesWithInformationAboutChange.put(fileName, true);
            }
            else {
                File templateFile = templateRepoFiles.get(fileName);
                try {
                    if (FileUtils.contentEquals(file, templateFile)) {
                        filesWithInformationAboutChange.put(fileName, false);
                    }
                    else {
                        filesWithInformationAboutChange.put(fileName, true);
                    }
                }
                catch (IOException e) {
                    log.error("Comparing files '{}' with '{}' failed: {}", fileName, templateFile, e.getMessage());
                }
            }
        });
        return filesWithInformationAboutChange;
    }

    /**
     * Create a file in a repository.
     *
     * @param repository  in which the file should be created.
     * @param filePath    of the file to be created.
     * @param inputStream byte representation of the file to be created.
     * @throws IOException if the inputStream is corrupt, the file can't be stored, the repository is unavailable, etc.
     */
    public void createFile(Repository repository, String filePath, InputStream inputStream) throws IOException {
        Path safePath = checkIfPathIsValidAndExistanceAndReturnSafePath(repository, filePath, false);
        File file = checkIfPathAndFileAreValidAndReturnSafeFile(repository, safePath);
        FileUtils.copyToFile(inputStream, file);
    }

    /**
     * Create a folder in a repository.
     *
     * @param repository  in which the folder should be created.
     * @param folderPath  of the folder to be created.
     * @param inputStream byte representation of the folder to be created.
     * @throws IOException if the inputStream is corrupt, the folder can't be stored, the repository is unavailable, etc.
     */
    public void createFolder(Repository repository, String folderPath, InputStream inputStream) throws IOException {
        Path safePath = checkIfPathIsValidAndExistanceAndReturnSafePath(repository, folderPath, false);
        checkIfPathAndFileAreValidAndReturnSafeFile(repository, safePath);
        Files.createDirectory(repository.getLocalPath().resolve(safePath));
        // We need to add an empty keep file so that the folder can be added to the git repository
        File keep = new File(repository.getLocalPath().resolve(safePath).resolve(".keep"), repository);
        FileUtils.copyToFile(inputStream, keep);
    }

    /**
     * Checks if the path is valid within the repository context and returns the absolute path.
     *
     * @param repository the repository
     * @param path       the relative path
     * @return the full safe path
     * @throws IllegalArgumentException if the path reaches outside of the repository
     */
    private Path checkIfPathIsValidAndReturnSafePath(Repository repository, String path) {
        String unescapedPath = StringEscapeUtils.unescapeJava(path);
        Path unknownInputPath = Paths.get(unescapedPath).normalize();
        Path absoluteRepositoryPath = repository.getLocalPath().normalize().toAbsolutePath();
        Path absoluteInputPath = absoluteRepositoryPath.resolve(unknownInputPath).normalize();
        if (!absoluteInputPath.startsWith(absoluteRepositoryPath)) {
            throw new IllegalArgumentException("Path is not valid");
        }
        return unknownInputPath;
    }

    /**
     * Checks if the path is valid and if the file exists or not.
     *
     * @param repository  the repository
     * @param path        the relative path
     * @param shouldExist if the file should exist or not
     * @return the full safe path
     * @throws IllegalArgumentException if the existence check fails
     */
    private Path checkIfPathIsValidAndExistanceAndReturnSafePath(Repository repository, String path, boolean shouldExist) {
        Path safePath = checkIfPathIsValidAndReturnSafePath(repository, path);
        Path fullPath = repository.getLocalPath().resolve(safePath);
        if ((shouldExist && !fullPath.toFile().exists()) || (!shouldExist && fullPath.toFile().exists())) {
            throw new IllegalArgumentException("Path is not valid");
        }
        return safePath;
    }

    /**
     * Checks if the path and the file are valid.
     *
     * @param repository the repository
     * @param path       the relative path
     * @return the file for the path in the repository
     */
    private File checkIfPathAndFileAreValidAndReturnSafeFile(Repository repository, Path path) {
        if (gitService.getFileByName(repository, path.toString()).isPresent()) {
            throw new IllegalArgumentException("Path is not valid");
        }

        File file = new File(Path.of(repository.getLocalPath().toString()).resolve(path).toFile(), repository);
        if (!repository.isValidFile(file)) {
            throw new IllegalArgumentException("Path is not valid");
        }
        return file;
    }

    /**
     * Rename a file in a repository.
     *
     * @param repository in which the file is located.
     * @param fileMove   dto for describing the old and the new filename.
     * @throws FileNotFoundException      if the file to rename is not available.
     * @throws FileAlreadyExistsException if the new filename is already taken.
     * @throws IllegalArgumentException   if the new filename is not allowed (e.g. contains '..' or '/../' or '.git')
     */
    public void renameFile(Repository repository, FileMove fileMove) throws FileNotFoundException, FileAlreadyExistsException, IllegalArgumentException {
        Path currentSafePath = checkIfPathIsValidAndExistanceAndReturnSafePath(repository, fileMove.currentFilePath(), true);
        String newFilename = FileService.sanitizeFilename(fileMove.newFilename());

        Optional<File> existingFile = gitService.getFileByName(repository, currentSafePath.toString());
        if (existingFile.isEmpty()) {
            throw new FileNotFoundException();
        }
        if (!repository.isValidFile(existingFile.get())) {
            throw new IllegalArgumentException("Existing path is not valid");
        }
        File newFile = new File(existingFile.get().toPath().getParent().resolve(newFilename), repository);
        if (!repository.isValidFile(newFile)) {
            throw new IllegalArgumentException("Existing path is not valid");
        }
        if (gitService.getFileByName(repository, newFile.getPath()).isPresent()) {
            throw new FileAlreadyExistsException("New path is not valid");
        }
        boolean isRenamed = existingFile.get().renameTo(newFile);
        if (!isRenamed) {
            throw new IllegalArgumentException("Existing path is not valid");
        }
    }

    /**
     * Delete a file in a repository.
     *
     * @param repository in which the file to delete is located.
     * @param filename   to delete.
     * @throws IOException              if the file can't be deleted.
     * @throws FileNotFoundException    if the file can't be found.
     * @throws IllegalArgumentException if the filename contains forbidden sequences (e.g. .. or /../).
     */
    public void deleteFile(Repository repository, String filename) throws IllegalArgumentException, IOException {
        Path safePath = checkIfPathIsValidAndExistanceAndReturnSafePath(repository, filename, true);
        Optional<File> file = gitService.getFileByName(repository, safePath.toString());

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
     * Commit all staged and un-staged changes in the given repository.
     *
     * @param repository for which to execute the commit.
     * @param user       the user who has committed the changes in the online editor
     * @throws GitAPIException if the staging/committing process fails.
     */
    public void commitChanges(Repository repository, User user) throws GitAPIException {
        gitService.stageAllChanges(repository);
        gitService.commitAndPush(repository, "Changes by Online Editor", true, user);
    }

    /**
     * Retrieve the status of the repository. Also pulls the repository.
     *
     * @param repositoryUri of the repository to check the status for.
     * @return a dto to determine the status of the repository.
     * @throws GitAPIException if the repository status can't be retrieved.
     */
    public boolean isClean(VcsRepositoryUri repositoryUri) throws GitAPIException {
        Repository repository = gitService.getOrCheckoutRepository(repositoryUri, true);
        return gitService.isClean(repository);
    }

    /**
     * Retrieve the status of the repository. Also pulls the repository.
     *
     * @param repositoryUri of the repository to check the status for.
     * @param defaultBranch the already used default branch in the remote repository
     * @return a dto to determine the status of the repository.
     * @throws GitAPIException if the repository status can't be retrieved.
     */
    public boolean isClean(VcsRepositoryUri repositoryUri, String defaultBranch) throws GitAPIException {
        Repository repository = gitService.getOrCheckoutRepository(repositoryUri, true, defaultBranch);
        return gitService.isClean(repository);
    }

    /**
     * Get the content of a file from the given repository.
     *
     * @param filename   of the file to retrieve.
     * @param repository the repository to retrieve the file from.
     * @return the file if available.
     * @throws IOException if the file can't be retrieved.
     */
    public ResponseEntity<byte[]> getFileFromRepository(String filename, Repository repository) throws IOException {
        byte[] out = getFile(repository, filename);
        HttpHeaders responseHeaders = new HttpHeaders();
        var contentType = getFileType(repository, filename);
        responseHeaders.add("Content-Type", contentType);
        // Prevent the file from being interpreted as HTML by the browser when opened directly:
        responseHeaders.setContentDisposition(ContentDisposition.builder("attachment").filename(filename).build());
        return new ResponseEntity<>(out, responseHeaders, HttpStatus.OK);
    }
}
