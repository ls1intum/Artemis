package de.tum.in.www1.artemis.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.File;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.web.rest.dto.FileMove;

/**
 * Service that provides utilities for managing files in a git repository.
 */
@Service
public class RepositoryService {

    private final GitService gitService;

    private final Logger log = LoggerFactory.getLogger(RepositoryService.class);

    public RepositoryService(GitService gitService) {
        this.gitService = gitService;
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
     * Get all files/folders with content from repository.
     *
     * @param repository in which the requested files are located
     * @return Files with code or an exception is thrown
     */
    public Map<String, String> getFilesWithContent(Repository repository) {
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
        // invalidate cache
        repository.setContent(null);
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
        repository.setContent(null); // invalidate cache
        inputStream.close();
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
        repository.setContent(null); // invalidate cache
        inputStream.close();
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

        repository.setContent(null); // invalidate cache
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
     * @param repositoryUrl of the repository to check the status for.
     * @return a dto to determine the status of the repository.
     * @throws GitAPIException if the repository status can't be retrieved.
     */
    public boolean isClean(VcsRepositoryUrl repositoryUrl) throws GitAPIException {
        Repository repository = gitService.getOrCheckoutRepository(repositoryUrl, true);
        return gitService.isClean(repository);
    }

    /**
     * Retrieve the status of the repository. Also pulls the repository.
     *
     * @param repositoryUrl of the repository to check the status for.
     * @param defaultBranch the already used default branch in the remote repository
     * @return a dto to determine the status of the repository.
     * @throws GitAPIException if the repository status can't be retrieved.
     */
    public boolean isClean(VcsRepositoryUrl repositoryUrl, String defaultBranch) throws GitAPIException {
        Repository repository = gitService.getOrCheckoutRepository(repositoryUrl, true, defaultBranch);
        return gitService.isClean(repository);
    }
}
