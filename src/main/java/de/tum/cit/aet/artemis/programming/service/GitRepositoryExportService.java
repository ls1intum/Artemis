package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.BinaryFileExtensionConfiguration.isBinaryFile;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import jakarta.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.ZipFileService;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.File;
import de.tum.cit.aet.artemis.programming.domain.FileType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class GitRepositoryExportService {

    private static final Logger log = LoggerFactory.getLogger(GitRepositoryExportService.class);

    private final GitService gitService;

    private final ZipFileService zipFileService;

    private final GitArchiveHelper gitArchiveHelper;

    public GitRepositoryExportService(GitService gitService, ZipFileService zipFileService, GitArchiveHelper gitArchiveHelper) {
        this.gitService = gitService;
        this.zipFileService = zipFileService;
        this.gitArchiveHelper = gitArchiveHelper;
    }

    /**
     * Get the content of a git repository that contains a participation, as zip or directory.
     *
     * @param repo            Local Repository Object.
     * @param repositoryDir   path where the repo is located on disk
     * @param hideStudentName option to hide the student name for the zip file or directory
     * @param zipOutput       If true the method returns a zip file otherwise a directory.
     * @return path to zip file or directory.
     * @throws IOException if the zipping or copying process failed.
     */
    public Path getRepositoryWithParticipation(Repository repo, String repositoryDir, boolean hideStudentName, boolean zipOutput) throws IOException, UncheckedIOException {
        var exercise = repo.getParticipation().getProgrammingExercise();
        String courseShortName = exercise.getCourseViaExerciseGroupOrCourseMember().getShortName();
        ProgrammingExerciseStudentParticipation participation = (ProgrammingExerciseStudentParticipation) repo.getParticipation();

        String repoName = FileUtil.sanitizeFilename(courseShortName + "-" + exercise.getTitle() + "-" + participation.getId());
        if (hideStudentName) {
            repoName += "-student-submission.git";
        }
        else {
            var studentTeamOrDefault = Objects.requireNonNullElse(participation.getParticipantIdentifier(), "student-submission" + repo.getParticipation().getId());

            repoName += "-" + studentTeamOrDefault;
        }
        repoName = participation.addPracticePrefixIfTestRun(repoName);

        if (zipOutput) {
            return zipFiles(repo.getLocalPath(), repoName, repositoryDir, null);
        }
        else {
            Path targetDir = Path.of(repositoryDir, repoName);

            FileUtils.copyDirectory(repo.getLocalPath().toFile(), targetDir.toFile());
            return targetDir;
        }
    }

    /**
     * Zips the contents of a folder, files are filtered according to the contentFilter.
     * Content filtering is added with the intention of optionally excluding ".git" directory from the result.
     *
     * @param contentRootPath the root path of the content to zip
     * @param zipFilename     the name of the zipped file
     * @param zipDir          path of folder where the zip should be located on disk
     * @param contentFilter   path filter to exclude some files, can be null to include everything
     * @return path to the zip file
     * @throws IOException if the zipping process failed.
     * @example
     *          // Exclude .git directory
     *          Predicate<Path> excludeGit = path -> !path.toString().contains(".git");
     *
     *          // Include everything
     *          Predicate<Path> includeAll = null;
     */
    public Path zipFiles(Path contentRootPath, String zipFilename, String zipDir, @Nullable Predicate<Path> contentFilter) throws IOException {
        String sanitized = FileUtil.sanitizeFilename(zipFilename).replaceAll("\\s+", "");
        if (!sanitized.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
            sanitized += ".zip";
        }
        Path zipFilePath = Path.of(zipDir, sanitized);
        Files.createDirectories(Path.of(zipDir));
        return zipFileService.createZipFileWithFolderContent(zipFilePath, contentRootPath, contentFilter);
    }

    /**
     * Zips the contents of a directory directly to memory without creating temporary files.
     * Content filtering is added with the intention of optionally excluding ".git" directory from the result.
     *
     * @param contentRootPath the root path of the content to zip
     * @param zipFilename     the name of the zipped file (for metadata)
     * @param contentFilter   path filter to exclude some files, can be null to include everything
     * @return InputStreamResource containing the zipped content
     * @throws IOException if the zipping process failed.
     * @example
     *          // Exclude .git directory
     *          Predicate<Path> excludeGit = path -> !path.toString().contains(".git");
     *
     *          // Include everything
     *          Predicate<Path> includeAll = null;
     */
    public InputStreamResource zipDirectoryToMemory(Path contentRootPath, String zipFilename, @Nullable Predicate<Path> contentFilter) throws IOException, UncheckedIOException {
        String zipFilenameWithoutWhitespace = zipFilename.replaceAll("\\s", "");

        if (!zipFilenameWithoutWhitespace.endsWith(".zip")) {
            zipFilenameWithoutWhitespace += ".zip";
        }

        var byteArrayResource = zipFileService.createZipFileWithFolderContentInMemory(contentRootPath, zipFilenameWithoutWhitespace, contentFilter);

        return createZipInputStreamResource(byteArrayResource.getByteArray(), byteArrayResource.getFilename().replace(".zip", ""));
    }

    /**
     * Creates an InputStreamResource from byte array data with proper filename and content length.
     *
     * @param zipData  the byte array containing the zip data
     * @param filename the filename for the resource (without .zip extension)
     * @return InputStreamResource with the zip data
     */
    private InputStreamResource createZipInputStreamResource(byte[] zipData, String filename) {
        return new InputStreamResource(new ByteArrayInputStream(zipData)) {

            @Override
            public String getFilename() {
                return filename + ".zip";
            }

            @Override
            public long contentLength() {
                return zipData.length;
            }
        };
    }

    /**
     * Creates a JGit archive of the repository's working tree for a given treeish.
     *
     * @param repository the repository to archive
     * @param treeish    the treeish to archive (e.g., "HEAD", "refs/heads/main")
     * @return byte array containing the archive data
     * @throws GitAPIException if the git operation fails
     * @throws IOException     if IO operations fail
     */
    private byte[] createJGitArchive(Repository repository, String treeish) throws GitAPIException, IOException {
        return gitArchiveHelper.createJGitArchive(repository, treeish);
    }

    /**
     * Exports a repository snapshot directly to memory without creating temporary files.
     * This method uses JGit's ArchiveCommand to create a zip archive of the repository's HEAD state.
     *
     * @param repositoryUri the URI of the repository to export
     * @param filename      the desired filename for the export (without extension)
     * @return InputStreamResource containing the zipped repository content
     * @throws GitAPIException if the git operation fails
     * @throws IOException     if IO operations fail
     */
    public InputStreamResource exportRepositorySnapshot(VcsRepositoryUri repositoryUri, String filename) throws GitAPIException, IOException {
        Repository repository = gitService.getBareRepository(repositoryUri, false);
        byte[] zipData = createJGitArchive(repository, "HEAD");
        return createZipInputStreamResource(zipData, filename);
    }

    /**
     * Exports a repository with full history including the .git directory directly to memory.
     * This method uses JGit's ArchiveCommand to create a zip of the working tree and combines it
     * with the .git directory for full history, all done in memory without disk checkout.
     *
     * @param repositoryUri the URI of the repository to export
     * @param filename      the desired filename for the export (without extension)
     * @return InputStreamResource containing the zipped repository content with full history
     * @throws GitAPIException if the git operation fails
     * @throws IOException     if IO operations fail
     */
    public InputStreamResource exportRepositoryWithFullHistoryToMemory(VcsRepositoryUri repositoryUri, String filename) throws GitAPIException, IOException {
        Repository repository = gitService.getBareRepository(repositoryUri, false);
        return gitArchiveHelper.exportRepositoryWithFullHistoryToMemory(repository, filename);
    }

    /**
     * Returns all files and directories within the working copy of the given repository in a map, excluding symbolic links.
     * This method performs a file scan and filters out symbolic links.
     * It only supports checked-out repositories (not bare ones)
     *
     * @param repo         The repository to scan for files and directories.
     * @param omitBinaries do not include binaries to reduce payload size
     * @return A {@link Map} where each key is a {@link File} object representing a file or directory, and each value is
     *         the corresponding {@link FileType} (FILE or FOLDER). The map excludes symbolic links.
     */
    public Map<File, FileType> listFilesAndFolders(Repository repo, boolean omitBinaries) {
        FileAndDirectoryFilter filter = new FileAndDirectoryFilter();
        Iterator<java.io.File> itr = FileUtils.iterateFilesAndDirs(repo.getLocalPath().toFile(), filter, filter);
        Map<File, FileType> files = new HashMap<>();

        while (itr.hasNext()) {
            File nextFile = new File(itr.next(), repo);
            Path nextPath = nextFile.toPath();

            if (Files.isSymbolicLink(nextPath)) {
                log.warn("Found a symlink {} in the git repository {}. Do not allow access!", nextPath, repo);
                continue;
            }

            if (omitBinaries && nextFile.isFile() && isBinaryFile(nextFile.getName())) {
                log.debug("Omitting binary file: {}", nextFile);
                continue;
            }

            files.put(nextFile, nextFile.isFile() ? FileType.FILE : FileType.FOLDER);
        }
        return files;
    }

    public Map<File, FileType> listFilesAndFolders(Repository repo) {
        return listFilesAndFolders(repo, false);
    }

    /**
     * List all files in the repository. In an empty git repo, this method returns en empty list.
     *
     * @param repo Local Repository Object.
     * @return Collection of File objects
     */
    public Collection<File> getFiles(Repository repo) {
        FileAndDirectoryFilter filter = new FileAndDirectoryFilter();
        Iterator<java.io.File> itr = FileUtils.iterateFiles(repo.getLocalPath().toFile(), filter, filter);
        Collection<File> files = new ArrayList<>();

        while (itr.hasNext()) {
            files.add(new File(itr.next(), repo));
        }

        return files;
    }

    /**
     * Get a specific file by name. Makes sure the file is actually part of the repository.
     *
     * @param repo     Local Repository Object.
     * @param filename String of the filename (including path)
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

    private static class FileAndDirectoryFilter implements IOFileFilter {

        private static final String GIT_DIRECTORY_NAME = ".git";

        @Override
        public boolean accept(java.io.File file) {
            return !GIT_DIRECTORY_NAME.equals(file.getName());
        }

        @Override
        public boolean accept(java.io.File directory, String fileName) {
            return !GIT_DIRECTORY_NAME.equals(directory.getName());
        }
    }

    /**
     * Exports an instructor repository (template, solution, or tests) directly to memory as an InputStreamResource.
     *
     * @param programmingExercise the programming exercise that has the repository
     * @param repositoryType      the type of repository to export (template, solution, or tests)
     * @param exportErrors        list of failures that occurred during the export
     * @return an InputStreamResource containing the zipped repository, or null if export failed
     */
    public InputStreamResource exportInstructorRepositoryForExerciseInMemory(ProgrammingExercise programmingExercise, RepositoryType repositoryType, List<String> exportErrors) {
        String zippedRepoName = getZippedRepoName(programmingExercise, repositoryType.getName());
        try {
            return exportRepositoryWithFullHistoryToMemory(programmingExercise.getRepositoryURL(repositoryType), zippedRepoName);
        }
        catch (IOException | GitAPIException ex) {
            String error = "Failed to export instructor repository " + repositoryType.getName() + " for programming exercise '" + programmingExercise.getTitle() + "' (id: "
                    + programmingExercise.getId() + ")";
            log.error("{}: {}", error, ex.getMessage());
            exportErrors.add(error);
            return null;
        }
    }

    /**
     * Exports an auxiliary repository directly to memory as an InputStreamResource.
     *
     * @param programmingExercise the programming exercise that has the repository
     * @param auxiliaryRepository the auxiliary repository to export
     * @param exportErrors        list of failures that occurred during the export
     * @return an InputStreamResource containing the zipped repository, or null if export failed
     */
    public InputStreamResource exportInstructorAuxiliaryRepositoryForExerciseInMemory(ProgrammingExercise programmingExercise, AuxiliaryRepository auxiliaryRepository,
            List<String> exportErrors) {
        String zippedRepoName = getZippedRepoName(programmingExercise, auxiliaryRepository.getRepositoryName());
        try {
            return exportRepositoryWithFullHistoryToMemory(auxiliaryRepository.getVcsRepositoryUri(), zippedRepoName);
        }
        catch (IOException | GitAPIException ex) {
            String error = "Failed to export auxiliary repository " + auxiliaryRepository.getName() + " for programming exercise '" + programmingExercise.getTitle() + "' (id: "
                    + programmingExercise.getId() + ")";
            log.error("{}: {}", error, ex.getMessage());
            exportErrors.add(error);
            return null;
        }
    }

    /**
     * Exports a student repository directly to memory as an InputStreamResource.
     *
     * @param programmingExercise the programming exercise
     * @param participation       the student participation for which to export the repository
     * @param exportErrors        list of failures that occurred during the export
     * @return an InputStreamResource containing the zipped repository, or null if export failed
     */
    public InputStreamResource exportStudentRepositoryInMemory(ProgrammingExercise programmingExercise, ProgrammingExerciseStudentParticipation participation,
            List<String> exportErrors) {
        if (participation.getVcsRepositoryUri() == null) {
            log.warn("Cannot export participation {} because its repository URI is null", participation.getId());
            exportErrors.add("Repository URI is null for participation " + participation.getId());
            return null;
        }

        try {
            String courseShortName = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getShortName();
            String repoName = FileUtil.sanitizeFilename(courseShortName + "-" + programmingExercise.getTitle() + "-" + participation.getId());

            // The zip filename is either the student login, team short name or some default string.
            String studentTeamOrDefault = Objects.requireNonNullElse(participation.getParticipantIdentifier(), "student-submission" + participation.getId());
            repoName += "-" + studentTeamOrDefault;
            repoName = participation.addPracticePrefixIfTestRun(repoName);

            // For student repositories, we use snapshot export to exclude .git directory for privacy
            return exportRepositorySnapshot(participation.getVcsRepositoryUri(), repoName);
        }
        catch (IOException | GitAPIException ex) {
            String error = "Failed to export student repository for participation " + participation.getId() + " in programming exercise '" + programmingExercise.getTitle()
                    + "' (id: " + programmingExercise.getId() + ")";
            log.error("{}: {}", error, ex.getMessage());
            exportErrors.add(error);
            return null;
        }
    }

    /**
     * Generates a zipped repository name for a programming exercise and repository.
     *
     * @param exercise       the programming exercise
     * @param repositoryName the name of the repository
     * @return the sanitized filename for the zipped repository
     */
    public String getZippedRepoName(ProgrammingExercise exercise, String repositoryName) {
        String courseShortName = exercise.getCourseViaExerciseGroupOrCourseMember().getShortName();
        return FileUtil.sanitizeFilename(courseShortName + "-" + exercise.getTitle() + "-" + repositoryName);
    }
}
