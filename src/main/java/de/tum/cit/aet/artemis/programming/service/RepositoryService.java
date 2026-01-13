package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.BinaryFileExtensionConfiguration.isBinaryFile;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import de.tum.cit.aet.artemis.core.config.BinaryFileExtensionConfiguration;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.GitException;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.programming.domain.File;
import de.tum.cit.aet.artemis.programming.domain.FileType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;
import de.tum.cit.aet.artemis.programming.dto.FileMove;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.service.localvc.VcsAccessLogService;

/**
 * Service that provides utilities for managing files in a git repository.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class RepositoryService {

    private final GitService gitService;

    private final Optional<VcsAccessLogService> vcsAccessLogService;

    private static final Logger log = LoggerFactory.getLogger(RepositoryService.class);

    public RepositoryService(GitService gitService, Optional<VcsAccessLogService> vcsAccessLogService) {
        this.gitService = gitService;
        this.vcsAccessLogService = vcsAccessLogService;
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
     * @throws IOException If an I/O error occurs during the file content retrieval process. This could be due to issues with file access, network problems, etc.
     */
    public Map<String, String> getFilesContentAtCommit(ProgrammingExercise programmingExercise, String commitId, RepositoryType repositoryType,
            ProgrammingExerciseParticipation participation) throws IOException {
        log.debug("Getting files at commit {} for participation {}", commitId, participation.getId());
        var repoUri = repositoryType == RepositoryType.TESTS ? programmingExercise.getVcsTestRepositoryUri() : participation.getVcsRepositoryUri();
        try (Repository repository = gitService.getBareRepository(repoUri, false)) {
            return getFilesContentFromBareRepository(repository, commitId);
        }
    }

    /**
     * Retrieves a mapping of file paths to their contents within a specified repository.
     * This method filters out all non-file type entries and reads the content of each file.
     * Note: If an I/O error occurs reading any file, this exception is caught internally and logged.
     *
     * @param repository   The repository from which files are to be fetched.
     * @param omitBinaries omit binary files for brevity and reducing size
     * @return A {@link Map} where each key is a file path (as a {@link String}) and each value is the content of the file (also as a {@link String}).
     *         The map includes only those files that could successfully have their contents read; files that cause an IOException are logged but not included.
     */
    public Map<String, String> getFilesContentFromWorkingCopy(Repository repository, boolean omitBinaries) {
        var files = gitService.listFilesAndFolders(repository, omitBinaries).entrySet().stream().filter(entry -> entry.getValue() == FileType.FILE).map(Map.Entry::getKey).toList();
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
     * @param commitHash The commit identifier from which to extract file contents.
     * @return A {@link Map} where each key is a file path and each value is the content of the file as a {@link String}.
     *         The content is encoded in UTF-8 and may not represent binary data accurately.
     * @throws IOException If an I/O error occurs during the file content retrieval process, including issues with
     *                         opening and reading the file stream.
     */
    public Map<String, String> getFilesContentFromBareRepository(Repository repository, @NonNull String commitHash) throws IOException {
        ObjectId commitId = repository.resolve(commitHash);
        if (commitId == null) {
            log.warn("Cannot resolve {} in the repository {}", commitHash, repository.getRemoteRepositoryUri());
            return Map.of();
        }
        return getFileContentFromBareRepositoryForCommitId(repository, commitId);
    }

    /**
     * Retrieves the contents of text-based files from the latest commit in a bare repository.
     * Binary files, as defined by {@link BinaryFileExtensionConfiguration}, are excluded.
     *
     * @param repository the JGit {@link Repository} instance representing the bare repository.
     * @return a {@link Map} where keys are file paths and values are file contents as UTF-8 strings.
     * @throws IOException if an error occurs while accessing the repository.
     */
    public Map<String, String> getFilesContentFromBareRepositoryForLastCommit(Repository repository) throws IOException {
        ObjectId headCommitId = repository.resolve("HEAD"); // Resolve HEAD if no commit ID is provided
        if (headCommitId == null) {
            log.warn("Cannot resolve HEAD. The repository might be empty.");
            return Map.of();
        }

        return getFileContentFromBareRepositoryForCommitId(repository, headCommitId);
    }

    /**
     * Retrieves the contents of text-based files from the latest commit in a bare repository identified by its URI.
     * If the bare repository is unavailable, falls back to retrieving files from the checked-out repository.
     * Binary files, as defined by {@link BinaryFileExtensionConfiguration}, are excluded.
     *
     * @param repositoryUri the {@link LocalVCRepositoryUri} identifying the repository location.
     * @return a {@link Map} where keys are file paths and values are file contents as UTF-8 strings.
     * @throws IOException if an error occurs while accessing the repository.
     */
    public Map<String, String> getFilesContentFromBareRepositoryForLastCommit(LocalVCRepositoryUri repositoryUri) throws IOException {

        try {
            var bareRepository = gitService.getBareRepository(repositoryUri, false);
            return getFilesContentFromBareRepositoryForLastCommit(bareRepository);
        }
        catch (GitException exception) {
            log.debug("Bare repository for {} is unavailable, falling back to checked out repository", repositoryUri, exception);
            return getFilesContentFromCheckedOutRepository(repositoryUri, null);
        }
    }

    /**
     * Retrieves the contents of text-based files from the most recent commit on or before the specified deadline.
     * Traverses the commit history from HEAD in descending commit time order and selects the first commit whose
     * committer time is less than or equal to the provided deadline.
     * Binary files and symlinks are excluded.
     *
     * @param repository the JGit {@link Repository} instance representing the bare repository.
     * @param deadline   the cutoff time; selects the last commit at or before this instant. If null, falls back to HEAD.
     * @return a map from file paths to UTF-8 content at the selected commit. Empty if no such commit exists.
     * @throws IOException if an error occurs while accessing the repository.
     */
    public Map<String, String> getFilesContentFromBareRepositoryForLastCommitBeforeOrAt(Repository repository, ZonedDateTime deadline) throws IOException {
        if (deadline == null) {
            return getFilesContentFromBareRepositoryForLastCommit(repository);
        }

        ObjectId headCommitId = repository.resolve("HEAD");
        if (headCommitId == null) {
            log.warn("Cannot resolve HEAD. The repository might be empty.");
            return Map.of();
        }

        long epochSeconds = deadline.toInstant().getEpochSecond();
        try (RevWalk walk = new RevWalk(repository)) {
            walk.markStart(walk.parseCommit(headCommitId));
            walk.sort(RevSort.COMMIT_TIME_DESC, true);

            for (RevCommit commit : walk) {
                if (((long) commit.getCommitTime()) <= epochSeconds) {
                    return getFileContentFromBareRepositoryForCommitId(repository, commit.getId());
                }
            }
        }

        // No commit older than or equal to deadline
        return Map.of();
    }

    /**
     * Variant that opens the bare repository from a {@link LocalVCRepositoryUri} and applies
     * {@link #getFilesContentFromBareRepositoryForLastCommitBeforeOrAt(Repository, ZonedDateTime)}.
     *
     * @param repositoryUri the repository URI to open
     * @param deadline      the deadline for the last commit
     * @return a mapping of file paths to their content
     * @throws IOException if an I/O error occurs
     */
    public Map<String, String> getFilesContentFromBareRepositoryForLastCommitBeforeOrAt(LocalVCRepositoryUri repositoryUri, ZonedDateTime deadline) throws IOException {
        try (Repository bareRepository = gitService.getBareRepository(repositoryUri, false)) {
            return getFilesContentFromBareRepositoryForLastCommitBeforeOrAt(bareRepository, deadline);
        }
        catch (GitException exception) {
            log.debug("Bare repository for {} before deadline {} is unavailable, falling back to checked out repository", repositoryUri, deadline, exception);
            return getFilesContentFromCheckedOutRepository(repositoryUri, deadline);
        }
    }

    /**
     * Retrieves a mapping of file paths to their content for a specific commit in a bare Git repository for non binary files
     * This method extracts file content by traversing the repository's tree from the specified commit.
     * It is primarily designed to read text files, converting the binary content to a UTF-8 string.
     * Usage of this method with binary files may lead to data corruption or misrepresentation as
     * binary data does not convert cleanly into UTF-8 strings.
     *
     * @param repository The repository from which file contents are to be retrieved. Must be a bare repository.
     * @param commitId   The commit id from which to extract file contents.
     * @return A {@link Map} where each key is a file path and each value is the content of the file as a {@link String}.
     *         The content is encoded in UTF-8 and may not represent binary data accurately.
     * @throws IOException If an I/O error occurs during the file content retrieval process, including issues with
     *                         opening and reading the file stream.
     */
    private Map<String, String> getFileContentFromBareRepositoryForCommitId(Repository repository, @NonNull ObjectId commitId) throws IOException {
        RevWalk revWalk = new RevWalk(repository);
        RevCommit commit = revWalk.parseCommit(commitId);

        // Initialize your map to store file paths and their contents
        Map<String, String> filesWithContent = new HashMap<>();

        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);

            while (treeWalk.next()) {
                String path = treeWalk.getPathString();

                // Skip binary files
                if (isBinaryFile(path)) {
                    continue;
                }

                // Skip symbolic links (CHECK FILE MODE)
                if (treeWalk.getFileMode(0) == FileMode.SYMLINK) {
                    continue;
                }

                ObjectId objectId = treeWalk.getObjectId(0);

                // Open the object stream to read the file content
                try (InputStream inputStream = repository.open(objectId).openStream()) {
                    byte[] bytes = inputStream.readAllBytes(); // Read all bytes at once
                    String content = new String(bytes, StandardCharsets.UTF_8); // Convert bytes to string with UTF-8 encoding

                    // Put the path and corresponding file content into the map
                    filesWithContent.put(path, content);
                }
            }
        }
        revWalk.close();
        return filesWithContent;
    }

    private Map<String, String> getFilesContentFromCheckedOutRepository(LocalVCRepositoryUri repositoryUri, ZonedDateTime deadline) throws IOException {
        try (Repository checkedOutRepository = gitService.getOrCheckoutRepository(repositoryUri, true, false)) {
            if (deadline == null) {
                return getFilesContentFromBareRepositoryForLastCommit(checkedOutRepository);
            }
            return getFilesContentFromBareRepositoryForLastCommitBeforeOrAt(checkedOutRepository, deadline);
        }
        catch (GitAPIException | GitException e) {
            throw new IOException("Failed to retrieve repository content for " + repositoryUri, e);
        }
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
     * Retrieves the contents of a single file from the given repository.
     * <p>
     * This method resolves the given repository-relative filename via the underlying
     * {@code gitService} and reads the file contents into memory.
     *
     * @param repository the repository in which the requested file is located
     * @param filename   repository-relative path of the file to retrieve
     * @return the complete contents of the file as a byte array
     *
     * @throws FileNotFoundException if no file with the given name exists in the repository
     * @throws IOException           if the file cannot be read due to I/O errors (e.g. permission issues, filesystem errors, or concurrent modification)
     */
    public byte[] getFile(Repository repository, String filename) throws IOException {

        Optional<File> fileOpt = gitService.getFileByName(repository, filename);
        if (fileOpt.isEmpty()) {
            throw new FileNotFoundException("File not found in repository: " + filename);
        }

        File file = fileOpt.get();
        try (InputStream inputStream = Files.newInputStream(file.toPath())) {
            return inputStream.readAllBytes();
        }
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
        Path safePath = checkIfPathIsValidAndExistenceAndReturnSafePath(repository, filePath, false);
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
        Path safePath = checkIfPathIsValidAndExistenceAndReturnSafePath(repository, folderPath, false);
        checkIfPathAndFileAreValidAndReturnSafeFile(repository, safePath);
        Files.createDirectory(repository.getLocalPath().resolve(safePath));
        // We need to add an empty keep file so that the folder can be added to the git repository
        File keep = new File(repository.getLocalPath().resolve(safePath).resolve(".keep"), repository);
        FileUtils.copyToFile(inputStream, keep);
    }

    /**
     * Validates a repository-relative path and ensures that it cannot escape the
     * repository's local root directory.
     * <p>
     * This method performs the following steps:
     * <ul>
     * <li>Unescapes the given path to handle encoded input.</li>
     * <li>Normalizes the path to eliminate redundant elements such as {@code "."}.</li>
     * <li>Resolves the path against the repository's local root directory.</li>
     * <li>Verifies that the resulting absolute path is still located within the repository
     * root (prevents path traversal attacks).</li>
     * </ul>
     *
     * @param repository the repository against which the path is validated
     * @param path       a repository-relative path (potentially escaped)
     * @return a normalized, repository-safe relative {@link Path}
     *
     * @throws IllegalArgumentException if the resolved path escapes the repository root or is otherwise unsafe
     */
    private Path checkIfPathIsValidAndReturnSafePath(Repository repository, String path) {

        String unescapedPath = StringEscapeUtils.unescapeJava(path);
        Path normalizedInputPath = Path.of(unescapedPath).normalize();

        Path repositoryRoot = repository.getLocalPath().normalize().toAbsolutePath();
        Path resolvedAbsolutePath = repositoryRoot.resolve(normalizedInputPath).normalize();

        if (!resolvedAbsolutePath.startsWith(repositoryRoot)) {
            throw new IllegalArgumentException("Illegal path traversal attempt detected. Resolved path escapes repository root. Input: " + path);
        }

        return normalizedInputPath;
    }

    /**
     * Validates a repository-relative path and verifies its existence according
     * to the given expectation.
     * <p>
     * This method performs the following checks:
     * <ul>
     * <li>Ensures that the given path is syntactically valid and safe within the repository
     * (e.g. no path traversal).</li>
     * <li>Resolves the path against the repository's local root directory.</li>
     * <li>Verifies whether the resolved path exists or does not exist, depending on
     * {@code shouldExist}.</li>
     * </ul>
     *
     * @param repository  the repository against which the path is validated
     * @param path        a repository-relative path
     * @param shouldExist {@code true} if the file or directory is expected to exist;
     *                        {@code false} if it must not exist
     * @return the validated, repository-safe relative path
     *
     * @throws IllegalArgumentException if the path is invalid, unsafe, or if the existence check does not match the expectation (e.g. a missing file when {@code shouldExist} is
     *                                      {@code true},
     *                                      or an already existing file when {@code shouldExist} is {@code false})
     */
    private Path checkIfPathIsValidAndExistenceAndReturnSafePath(Repository repository, String path, boolean shouldExist) {
        Path safePath = checkIfPathIsValidAndReturnSafePath(repository, path);
        Path fullPath = repository.getLocalPath().resolve(safePath);

        boolean exists = fullPath.toFile().exists();

        if (shouldExist && !exists) {
            throw new IllegalArgumentException("Expected file to exist, but it does not: " + fullPath);
        }

        if (!shouldExist && exists) {
            throw new IllegalArgumentException("Expected file to not exist, but it already exists: " + fullPath);
        }

        return safePath;
    }

    /**
     * Validates a repository-relative path and ensures that it refers to a valid,
     * non-conflicting file within the given repository.
     * <p>
     * This method performs the following checks:
     * <ul>
     * <li>Ensures that no file with the same path already exists in the repository
     * (e.g. to avoid name collisions).</li>
     * <li>Resolves the path against the repository's local root directory.</li>
     * <li>Verifies that the resolved file is considered valid according to the
     * repository's constraints (e.g. location, permissions, or repository rules).</li>
     * </ul>
     *
     * @param repository the repository against which the path is validated
     * @param path       a repository-relative path
     * @return a validated {@link File} instance representing the resolved path in the repository
     *
     * @throws IllegalArgumentException if a file with the given path already exists in the repository or if the resolved file violates repository validity constraints
     */
    private File checkIfPathAndFileAreValidAndReturnSafeFile(Repository repository, Path path) {
        if (gitService.getFileByName(repository, path.toString()).isPresent()) {
            throw new IllegalArgumentException("A file with the given path already exists in the repository: " + path);
        }

        File file = new File(repository.getLocalPath().resolve(path).toFile(), repository);

        if (!repository.isValidFile(file)) {
            throw new IllegalArgumentException("The resolved file is not valid within the repository constraints: " + file.getPath());
        }

        return file;
    }

    /**
     * Renames an existing file in the given repository to a new filename within the same directory.
     * <p>
     * This method performs the following checks:
     * <ul>
     * <li>Validates that {@code fileMove.currentFilePath()} is a safe repository-relative path and that the file exists.</li>
     * <li>Resolves the current file via {@code gitService} and verifies that it is valid according to repository constraints.</li>
     * <li>Sanitizes {@code fileMove.newFilename()} and constructs the target path in the same parent directory.</li>
     * <li>Validates that the target file path is allowed and does not already exist.</li>
     * </ul>
     *
     * @param repository the repository in which the file is located
     * @param fileMove   DTO describing the current repository-relative path and the new filename
     *
     * @throws FileNotFoundException      if the file referenced by {@code fileMove.currentFilePath()} does not exist in the repository
     * @throws FileAlreadyExistsException if a file with the resulting target path already exists
     * @throws IllegalArgumentException   if the current path or the resulting target path is unsafe/invalid for the repository, or if the rename operation fails on the filesystem
     */
    public void renameFile(Repository repository, FileMove fileMove) throws FileNotFoundException, FileAlreadyExistsException, IllegalArgumentException {

        Path currentSafePath = checkIfPathIsValidAndExistenceAndReturnSafePath(repository, fileMove.currentFilePath(), true);
        String newFilename = FileUtil.sanitizeFilename(fileMove.newFilename());

        Optional<File> existingFileOpt = gitService.getFileByName(repository, currentSafePath.toString());
        if (existingFileOpt.isEmpty()) {
            throw new FileNotFoundException("File to rename not found in repository: " + currentSafePath);
        }

        File existingFile = existingFileOpt.get();
        if (!repository.isValidFile(existingFile)) {
            throw new IllegalArgumentException("Resolved existing file is not valid within repository constraints: " + existingFile.getPath());
        }

        File newFile = new File(existingFile.toPath().getParent().resolve(newFilename), repository);
        if (!repository.isValidFile(newFile)) {
            throw new IllegalArgumentException(
                    "Target file path is not valid within repository constraints. " + "Existing: " + existingFile.getPath() + ", target: " + newFile.getPath());
        }

        // Important: use repository-relative path for lookup (avoid relying on File#toString format).
        String newRepositoryRelativePath = repository.getLocalPath().relativize(newFile.toPath()).toString();
        if (gitService.getFileByName(repository, newRepositoryRelativePath).isPresent()) {
            throw new FileAlreadyExistsException("Cannot rename because target already exists in repository: " + newRepositoryRelativePath);
        }

        boolean renamed = existingFile.renameTo(newFile);
        if (!renamed) {
            throw new IllegalArgumentException("Rename failed on filesystem. Existing: " + existingFile.getPath() + ", target: " + newFile.getPath());
        }
    }

    /**
     * Deletes an existing file or directory from the given repository.
     * <p>
     * This method performs the following checks:
     * <ul>
     * <li>Validates that {@code filename} is a safe repository-relative path and that it exists.</li>
     * <li>Resolves the file via {@code gitService} and verifies that it is valid according to repository constraints.</li>
     * </ul>
     * If the resolved path points to a regular file it is deleted via {@link Files#delete(java.nio.file.Path)}.
     * If it points to a directory it is deleted recursively.
     *
     * @param repository the repository in which the file/directory to delete is located
     * @param filename   repository-relative path of the file/directory to delete
     *
     * @throws FileNotFoundException    if the file/directory does not exist in the repository
     * @throws IllegalArgumentException if {@code filename} is unsafe/invalid or if the resolved file violates repository constraints
     * @throws IOException              if deletion fails at the filesystem level
     */
    public void deleteFile(Repository repository, String filename) throws IllegalArgumentException, IOException {

        Path safePath = checkIfPathIsValidAndExistenceAndReturnSafePath(repository, filename, true);
        Optional<File> fileOpt = gitService.getFileByName(repository, safePath.toString());

        if (fileOpt.isEmpty()) {
            throw new FileNotFoundException("File to delete not found in repository: " + safePath);
        }

        File file = fileOpt.get();
        if (!repository.isValidFile(file)) {
            throw new IllegalArgumentException("Resolved file is not valid within repository constraints: " + file.getPath());
        }

        if (file.isFile()) {
            Files.delete(file.toPath());
        }
        else {
            // Apache Commons IO: deletes directory recursively; throws IOException on failure.
            FileUtils.deleteDirectory(file);
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
     * Saves a preliminary access log for a push from the code editor, and returns it
     *
     * @param repository for which to execute the commit.
     * @param user       the user who has committed the changes in the online editor
     * @param domainId   the id that serves as an abstract identifier for retrieving the repository.
     * @return an Optional of a preliminary VcsAccessLog
     * @throws GitAPIException if accessing the repository fails
     */
    public Optional<VcsAccessLog> savePreliminaryCodeEditorAccessLog(Repository repository, User user, Long domainId) throws GitAPIException {
        return vcsAccessLogService.isPresent() ? vcsAccessLogService.get().createPreliminaryCodeEditorAccessLog(repository, user, domainId) : Optional.empty();
    }

    /**
     * Retrieve the status of the repository. Also pulls the repository.
     *
     * @param repositoryUri of the repository to check the status for.
     * @param defaultBranch the already used default branch in the remote repository
     * @return a dto to determine the status of the repository.
     * @throws GitAPIException if the repository status can't be retrieved.
     */
    public boolean isWorkingCopyClean(LocalVCRepositoryUri repositoryUri, String defaultBranch) throws GitAPIException {
        Repository repository = gitService.getOrCheckoutRepository(repositoryUri, true, defaultBranch, false);
        return gitService.isWorkingCopyClean(repository);
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
        // Prevent the file from being interpreted as HTML by the browser when opened directly:
        responseHeaders.setContentDisposition(ContentDisposition.builder("attachment").filename(filename).build());
        return new ResponseEntity<>(out, responseHeaders, HttpStatus.OK);
    }
}
