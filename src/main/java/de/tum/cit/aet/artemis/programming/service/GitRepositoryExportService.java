package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.BinaryFileExtensionConfiguration.isBinaryFile;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import jakarta.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.ZipFileService;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.programming.domain.File;
import de.tum.cit.aet.artemis.programming.domain.FileType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;

@Profile(PROFILE_CORE)
@Service
public class GitRepositoryExportService {

    private static final Logger log = LoggerFactory.getLogger(GitRepositoryExportService.class);

    private final GitService gitService;

    private final ZipFileService zipFileService;

    public GitRepositoryExportService(GitService gitService, ZipFileService zipFileService) {
        this.gitService = gitService;
        this.zipFileService = zipFileService;
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
        var courseShortName = exercise.getCourseViaExerciseGroupOrCourseMember().getShortName();
        var participation = (ProgrammingExerciseStudentParticipation) repo.getParticipation();

        String repoName = FileUtil.sanitizeFilename(courseShortName + "-" + exercise.getTitle() + "-" + participation.getId());
        if (hideStudentName) {
            repoName += "-student-submission.git";
        }
        else {
            // The zip filename is either the student login, team short name or some default string.
            var studentTeamOrDefault = java.util.Objects.requireNonNullElse(participation.getParticipantIdentifier(), "student-submission" + repo.getParticipation().getId());

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
     */
    public Path zipFiles(Path contentRootPath, String zipFilename, String zipDir, @Nullable Predicate<Path> contentFilter) throws IOException, UncheckedIOException {
        // Strip slashes from name
        var zipFilenameWithoutSlash = zipFilename.replaceAll("\\s", "");

        if (!zipFilenameWithoutSlash.endsWith(".zip")) {
            zipFilenameWithoutSlash += ".zip";
        }

        Path zipFilePath = Path.of(zipDir, zipFilenameWithoutSlash);
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
     */
    public InputStreamResource zipDirectoryToMemory(Path contentRootPath, String zipFilename, @Nullable Predicate<Path> contentFilter) throws IOException, UncheckedIOException {
        var zipFilenameWithoutSlash = zipFilename.replaceAll("\\s", "");

        if (!zipFilenameWithoutSlash.endsWith(".zip")) {
            zipFilenameWithoutSlash += ".zip";
        }

        var byteArrayResource = zipFileService.createZipFileWithFolderContentInMemory(contentRootPath, zipFilenameWithoutSlash, contentFilter);

        return new InputStreamResource(new ByteArrayInputStream(byteArrayResource.getByteArray())) {

            @Override
            public String getFilename() {
                return byteArrayResource.getFilename();
            }

            @Override
            public long contentLength() {
                return byteArrayResource.getByteArray().length;
            }
        };
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
        // Get the bare repository
        Repository repository = gitService.getBareRepository(repositoryUri, false);

        try (Git git = new Git(repository)) {
            // Create a ByteArrayOutputStream to hold the archive data
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            // Use ArchiveCommand to create a zip archive directly to memory
            git.archive().setFormat("zip").setTree(git.getRepository().resolve("HEAD")).setOutputStream(outputStream).call();

            // Create an InputStreamResource from the output
            byte[] zipData = outputStream.toByteArray();
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
    }

    /**
     * Determines the default branch name for a repository using multiple fallback strategies.
     * This method is particularly useful for bare repositories or repositories without proper remote configuration.
     *
     * @param repository the repository to analyze
     * @return the branch name, or null if no branches are found
     */
    private String determineDefaultBranch(Repository repository) {
        // Try to find the default branch - first try origin/HEAD, then just HEAD, then any branch
        String branch = null;

        try {
            // Try to get origin head first
            branch = gitService.getOriginHead(repository);
        }
        catch (Exception e) {
            log.debug("Could not get origin head, trying alternative methods: {}", e.getMessage());
        }

        // If origin head failed, try to resolve HEAD directly
        if (branch == null) {
            try {
                ObjectId headId = repository.resolve("HEAD");
                if (headId != null) {
                    Ref headRef = repository.exactRef("HEAD");
                    if (headRef != null && headRef.isSymbolic()) {
                        String targetRef = headRef.getTarget().getName();
                        if (targetRef.startsWith("refs/heads/")) {
                            branch = targetRef.substring("refs/heads/".length());
                        }
                    }
                }
            }
            catch (Exception e) {
                log.debug("Could not resolve HEAD: {}", e.getMessage());
            }
        }

        // If still no branch, try to find any branch
        if (branch == null) {
            try {
                Map<String, Ref> branches = repository.getAllRefs();
                for (Map.Entry<String, Ref> entry : branches.entrySet()) {
                    if (entry.getKey().startsWith("refs/heads/")) {
                        branch = entry.getKey().substring("refs/heads/".length());
                        break;
                    }
                }
            }
            catch (Exception e) {
                log.debug("Could not find any branches: {}", e.getMessage());
            }
        }

        return branch;
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
        log.debug("Exporting repository with full history to memory using JGit archive: {}", repositoryUri);

        Repository repository = gitService.getBareRepository(repositoryUri, false);
        String branch = determineDefaultBranch(repository);

        if (branch == null) {
            // Empty repository case - just return the .git directory
            log.debug("No branches found, returning .git directory only");
            return zipDirectoryToMemory(repository.getDirectory().toPath(), filename, null);
        }

        String treeish = "refs/heads/" + branch;
        log.debug("Using branch '{}' for export", branch);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {

            // Step 1: Add the .git directory (full history)
            Path bareRepoPath = repository.getDirectory().toPath();
            addDirectoryToZip(zipOutputStream, bareRepoPath, bareRepoPath, ".git");

            // Step 2: Add the working tree snapshot using ArchiveCommand
            try {
                ObjectId treeId = repository.resolve(treeish);
                if (treeId != null) {
                    try (ByteArrayOutputStream archiveStream = new ByteArrayOutputStream()) {
                        try (Git git = new Git(repository)) {
                            git.archive().setTree(treeId).setFormat("zip").setOutputStream(archiveStream).call();
                        }

                        // Extract the archive contents and add them to our main zip
                        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(archiveStream.toByteArray()))) {
                            ZipEntry entry;
                            while ((entry = zipInputStream.getNextEntry()) != null) {
                                zipOutputStream.putNextEntry(new ZipEntry(entry.getName()));
                                zipInputStream.transferTo(zipOutputStream);
                                zipOutputStream.closeEntry();
                            }
                        }
                    }
                }
                else {
                    log.debug("Could not resolve tree for branch '{}', repository might be empty", branch);
                }
            }
            catch (Exception e) {
                log.debug("Could not create archive for branch '{}': {}", branch, e.getMessage());
                // Continue without the working tree content, just include .git directory
            }

            zipOutputStream.finish();
            byte[] zipData = outputStream.toByteArray();

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
    }

    /**
     * Helper method to add a directory and its contents to a ZIP output stream.
     *
     * @param zipOutputStream the ZIP output stream to write to
     * @param rootPath        the root path for calculating relative paths
     * @param pathToAdd       the path to add to the ZIP
     * @param prefix          the prefix to use in the ZIP entry names
     * @throws IOException if an I/O error occurs
     */
    private void addDirectoryToZip(ZipOutputStream zipOutputStream, Path rootPath, Path pathToAdd, String prefix) throws IOException {
        Files.walk(pathToAdd).forEach(path -> {
            try {
                String relativePath = rootPath.relativize(path).toString().replace("\\", "/");
                String zipEntryName = prefix + "/" + relativePath;

                if (Files.isDirectory(path)) {
                    // Add directory entry (with trailing slash)
                    if (!zipEntryName.endsWith("/")) {
                        zipEntryName += "/";
                    }
                    zipOutputStream.putNextEntry(new ZipEntry(zipEntryName));
                }
                else if (Files.isRegularFile(path)) {
                    // Add file entry
                    zipOutputStream.putNextEntry(new ZipEntry(zipEntryName));
                    FileUtils.copyFile(path.toFile(), zipOutputStream);
                }
                zipOutputStream.closeEntry();
            }
            catch (IOException e) {
                throw new UncheckedIOException("Failed to add path to zip: " + path, e);
            }
        });
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
        java.util.Iterator<java.io.File> itr = FileUtils.iterateFilesAndDirs(repo.getLocalPath().toFile(), filter, filter);
        Map<File, FileType> files = new java.util.HashMap<>();

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
    public java.util.Collection<File> getFiles(Repository repo) {
        FileAndDirectoryFilter filter = new FileAndDirectoryFilter();
        java.util.Iterator<java.io.File> itr = FileUtils.iterateFiles(repo.getLocalPath().toFile(), filter, filter);
        java.util.Collection<File> files = new java.util.ArrayList<>();

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
}
