package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import jakarta.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.ArchiveCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.archive.ZipFormat;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.ZipFileService;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.service.git.InMemoryRepositoryBuilder;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

/**
 * Service for exporting Git repositories to ZIPs fully in memory.
 *
 * <p>
 * Supports two main export modes:
 * <ul>
 * <li>Snapshot export (no .git directory) using JGit's Archive command.</li>
 * <li>Full-history export (including a synthetic .git directory) using an in-memory
 * repository builder.</li>
 * </ul>
 *
 * The service returns {@link org.springframework.core.io.InputStreamResource} instances so
 * controllers can stream responses without writing temporary files.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class GitRepositoryExportService {

    private static final Logger log = LoggerFactory.getLogger(GitRepositoryExportService.class);

    private final GitService gitService;

    private final ZipFileService zipFileService;

    public GitRepositoryExportService(GitService gitService, ZipFileService zipFileService) {
        this.gitService = gitService;
        this.zipFileService = zipFileService;

        try {
            ArchiveCommand.registerFormat("zip", new ZipFormat());
        }
        catch (Exception e) {
            log.error("Could not register zip format", e);
        }
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
    public Path getRepositoryWithParticipation(Repository repo, String repositoryDir, boolean hideStudentName, boolean zipOutput) throws IOException {
        var exercise = repo.getParticipation().getProgrammingExercise();
        String courseShortName = exercise.getCourseViaExerciseGroupOrCourseMember().getShortName();
        ProgrammingExerciseStudentParticipation participation = (ProgrammingExerciseStudentParticipation) repo.getParticipation();

        String sanitizedRepoName = FileUtil.sanitizeFilename(courseShortName + "-" + exercise.getTitle() + "-" + participation.getId());
        if (hideStudentName) {
            sanitizedRepoName += "-student-submission.git";
        }
        else {
            var studentTeamOrDefault = Objects.requireNonNullElse(participation.getParticipantIdentifier(), "student-submission" + repo.getParticipation().getId());

            sanitizedRepoName += "-" + studentTeamOrDefault;
        }
        sanitizedRepoName = participation.addPracticePrefixIfTestRun(sanitizedRepoName);

        if (zipOutput) {
            Predicate<Path> contentFilter = null;
            if (hideStudentName) {
                contentFilter = path -> {
                    String s = path.toString().replace('\\', '/');
                    if (s.contains("/.git/logs")) {
                        return false;
                    }
                    if (s.endsWith("/FETCH_HEAD")) {
                        return false;
                    }
                    return true;
                };
            }
            return zipFiles(repo.getLocalPath(), sanitizedRepoName, repositoryDir, contentFilter);
        }
        else {
            Path targetDir = Path.of(repositoryDir, sanitizedRepoName);

            FileUtils.copyDirectory(repo.getLocalPath().toFile(), targetDir.toFile());
            if (hideStudentName) {
                try {
                    Path logsPath = targetDir.resolve(".git").resolve("logs");
                    FileUtils.deleteDirectory(logsPath.toFile());
                }
                catch (IOException ex) {
                    log.warn("Could not delete reflogs from exported repository at {}: {}", targetDir, ex.getMessage());
                }
                try {
                    Files.deleteIfExists(targetDir.resolve(".git").resolve("FETCH_HEAD"));
                }
                catch (IOException ex) {
                    log.warn("Could not delete FETCH_HEAD from exported repository at {}: {}", targetDir, ex.getMessage());
                }
            }
            return targetDir;
        }
    }

    private String sanitizeZipFilename(String filename) {
        String sanitized = FileUtil.sanitizeFilename(filename).replaceAll("\\s+", "");
        if (!sanitized.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
            sanitized += ".zip";
        }
        return sanitized;
    }

    /**
     * Zips the contents of a folder, files are filtered according to the contentFilter.
     * Content filtering is added with the intention of optionally excluding ".git" directory from the result.
     * <p>
     * Example
     * // Exclude .git directory
     * Predicate<Path> excludeGit = path -> !path.toString().contains(".git");
     * <p>
     * // Include everything
     * Predicate<Path> includeAll = null;
     *
     * @param contentRootPath the root path of the content to zip
     * @param zipFilename     the name of the zipped file
     * @param zipDir          path of folder where the zip should be located on disk
     * @param contentFilter   path filter to exclude some files, can be null to include everything
     *
     * @return path to the zip file
     * @throws IOException if the zipping process failed.
     */
    public Path zipFiles(Path contentRootPath, String zipFilename, String zipDir, @Nullable Predicate<Path> contentFilter) throws IOException {
        String sanitized = sanitizeZipFilename(zipFilename);
        Path zipFilePath = Path.of(zipDir, sanitized);
        Files.createDirectories(Path.of(zipDir));
        return zipFileService.createZipFileWithFolderContent(zipFilePath, contentRootPath, contentFilter);
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
        Repository repository = gitService.getBareRepository(new LocalVCRepositoryUri(repositoryUri.toString()), false);
        byte[] zipData = createInMemoryZipArchive(repository);
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
     * @throws IOException if IO operations fail
     */
    public InputStreamResource exportRepositoryWithFullHistoryToMemory(VcsRepositoryUri repositoryUri, String filename) throws IOException {
        Repository repository = gitService.getBareRepository(new LocalVCRepositoryUri(repositoryUri.toString()), false);
        byte[] zipData = InMemoryRepositoryBuilder.buildZip(repository);
        return createZipInputStreamResource(zipData, filename);
    }

    /**
     * Exports an instructor repository (template, solution, or tests) directly to memory as an InputStreamResource.
     *
     * @param programmingExercise the programming exercise that has the repository
     * @param repositoryType      the type of repository to export (template, solution, or tests)
     * @return an InputStreamResource containing the zipped repository, or null if export failed
     */
    public InputStreamResource exportInstructorRepositoryForExerciseInMemory(ProgrammingExercise programmingExercise, RepositoryType repositoryType) throws IOException {
        String zippedRepoName = getZippedRepoName(programmingExercise, repositoryType.getName());
        return exportRepositoryWithFullHistoryToMemory(programmingExercise.getRepositoryURI(repositoryType), zippedRepoName);
    }

    /**
     * Exports an auxiliary repository directly to memory as an InputStreamResource.
     *
     * @param programmingExercise the programming exercise that has the repository
     * @param auxiliaryRepository the auxiliary repository to export
     * @return an InputStreamResource containing the zipped repository, or null if export failed
     */
    public InputStreamResource exportInstructorAuxiliaryRepositoryForExerciseInMemory(ProgrammingExercise programmingExercise, AuxiliaryRepository auxiliaryRepository)
            throws IOException {
        String zippedRepoName = getZippedRepoName(programmingExercise, auxiliaryRepository.getRepositoryName());
        return exportRepositoryWithFullHistoryToMemory(auxiliaryRepository.getVcsRepositoryUri(), zippedRepoName);
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
            log.error(error, ex);
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

    /**
     * Creates a zip archive of the repository's HEAD state in memory.
     * This method uses JGit's ArchiveCommand to create a zip archive without writing to disk.
     *
     * @param repository the repository to archive
     * @return byte array containing the zip archive data
     * @throws GitAPIException if the git operation fails
     * @throws IOException     if IO operations fail
     */
    public byte[] createInMemoryZipArchive(Repository repository) throws GitAPIException, IOException {
        ObjectId treeId = repository.resolve("HEAD");
        if (treeId == null) {
            log.debug("Could not resolve tree for HEAD");
            return new byte[0];
        }

        ByteArrayOutputStream archiveData = new ByteArrayOutputStream();
        try (Git git = new Git(repository)) {
            git.archive().setFormat("zip").setTree(treeId).setOutputStream(archiveData).call();
        }
        return archiveData.toByteArray();
    }
}
