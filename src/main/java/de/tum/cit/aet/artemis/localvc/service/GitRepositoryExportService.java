package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.eclipse.jgit.api.ArchiveCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.archive.ZipFormat;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.localvc.service.git.InMemoryRepositoryBuilder;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;

/**
 * Service for exporting Git repositories, read straight from the bare repository on disk.
 *
 * <p>
 * Supports two export modes:
 * <ul>
 * <li>Snapshot export (no .git directory) using JGit's Archive command.</li>
 * <li>Full-history export (including a synthetic .git directory) using {@link InMemoryRepositoryBuilder}.</li>
 * </ul>
 *
 * <p>
 * Neither mode clones or checks out anything. Controllers get an
 * {@link org.springframework.core.io.InputStreamResource} so they can stream a response without a temporary file. Bulk
 * exports write straight into their output directory, either as one ZIP per repository
 * ({@link #exportRepositoryToZipFile}) or as a directory per repository ({@link #exportRepositoryToDirectory}) for the
 * callers whose layout is a directory.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class GitRepositoryExportService {

    private static final Logger log = LoggerFactory.getLogger(GitRepositoryExportService.class);

    /** Suffix an archive carries while it is still being written. */
    private static final String PARTIAL_EXPORT_SUFFIX = ".part";

    private final GitService gitService;

    public GitRepositoryExportService(GitService gitService) {
        this.gitService = gitService;

        try {
            ArchiveCommand.registerFormat("zip", new ZipFormat());
        }
        catch (Exception e) {
            log.error("Could not register zip format", e);
        }
    }

    /**
     * Copies a checked out participation repository into the given directory, under a name derived from the participation.
     *
     * <p>
     * Only the export options that rewrite a repository still reach this method. Every faithful export is streamed
     * straight from the bare repository instead - to a ZIP with {@link #exportRepositoryToZipFile} or to a directory
     * with {@link #exportRepositoryToDirectory} - and never produces a working copy to copy from.
     *
     * @param repo            Local Repository Object.
     * @param repositoryDir   path where the copy should be placed
     * @param hideStudentName option to hide the student name in the directory name
     * @return path to the copied directory.
     * @throws IOException if the copying process failed.
     */
    public Path getRepositoryWithParticipation(Repository repo, String repositoryDir, boolean hideStudentName) throws IOException {
        var exercise = repo.getParticipation().getProgrammingExercise();
        ProgrammingExerciseStudentParticipation participation = (ProgrammingExerciseStudentParticipation) repo.getParticipation();

        Path targetDir = Path.of(repositoryDir, getStudentRepositoryName(exercise, participation, hideStudentName));
        FileUtils.copyDirectory(repo.getLocalPath().toFile(), targetDir.toFile());
        return targetDir;
    }

    private String sanitizeZipFilename(String filename) {
        String sanitized = FileUtil.sanitizeFilename(filename).replaceAll("\\s+", "");
        if (!sanitized.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
            sanitized += ".zip";
        }
        return sanitized;
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
     * How much of a repository an export contains.
     */
    public enum RepositoryExportContent {
        /** The working tree of the current branch only, without any git metadata. */
        WORKING_TREE_ONLY,
        /** The working tree plus a synthetic {@code .git} directory, so the extracted archive is a usable repository. */
        WITH_HISTORY
    }

    /**
     * Materializes a repository with its full history into a directory, straight from the bare repository.
     *
     * <p>
     * Callers that have to hand back a directory rather than a ZIP - the personal data export does, because a student
     * should not have to unpack a second archive to reach their own code - used to clone the repository and check it
     * out to get there. Reading the objects directly skips the clone and the temporary working copy it needed.
     *
     * <p>
     * The directory is assembled under a temporary name and moved into place only on success, so a failure cannot leave
     * a half-written repository behind for a later step to pick up.
     *
     * @param repositoryUri   the repository to export
     * @param targetDirectory the directory the repository directory is created in
     * @param directoryName   the name of the repository directory
     * @return the path of the materialized repository directory
     * @throws IOException if the repository cannot be read or the directory cannot be written
     */
    public Path exportRepositoryToDirectory(VcsRepositoryUri repositoryUri, Path targetDirectory, String directoryName) throws IOException {
        Files.createDirectories(targetDirectory);
        Path repositoryPath = targetDirectory.resolve(FileUtil.sanitizeFilename(directoryName));
        Path partialPath = targetDirectory.resolve(repositoryPath.getFileName() + PARTIAL_EXPORT_SUFFIX);

        try {
            // A staging directory an earlier run left behind would be written into rather than replaced, and
            // DirectoryRepositoryContentSink only creates and overwrites the entries of the current repository, so a
            // file that is no longer in it would survive and be published by the move below.
            FileUtils.deleteDirectory(partialPath.toFile());
            try (Repository bareRepository = gitService.getBareRepository(new LocalVCRepositoryUri(repositoryUri.toString()), false)) {
                InMemoryRepositoryBuilder.writeToDirectory(bareRepository, partialPath);
            }
            FileUtil.publishAtomically(partialPath, repositoryPath);
        }
        finally {
            if (Files.exists(partialPath) && !FileUtils.deleteQuietly(partialPath.toFile())) {
                log.error("Could not delete the incomplete export {}", partialPath);
            }
        }
        return repositoryPath;
    }

    /**
     * Writes a zip of the given repository directly into the target directory, reading the objects from the bare
     * repository on disk.
     *
     * <p>
     * There is no clone, no checkout and no temporary directory involved: for every export that does not rewrite the
     * repository (course and exam archiving, the exercise material export, the instructor repositories of any export)
     * this replaces a clone, a full directory copy and a zip pass with a single compressed write.
     *
     * @param repositoryUri   the URI of the repository to export
     * @param targetDirectory the directory the zip is written into; created if it does not exist
     * @param zipFilename     the desired filename for the zip, with or without the {@code .zip} extension
     * @param content         whether the archive should carry the git history
     * @return the path of the written zip file
     * @throws IOException if the repository cannot be read or the zip cannot be written; the archive only appears under
     *                         its final name once it is complete, so a failed export cannot leave a truncated file for a
     *                         later step to pick up
     */
    public Path exportRepositoryToZipFile(VcsRepositoryUri repositoryUri, Path targetDirectory, String zipFilename, RepositoryExportContent content) throws IOException {
        Files.createDirectories(targetDirectory);
        Path zipFilePath = targetDirectory.resolve(sanitizeZipFilename(zipFilename));
        // Written under a temporary name and moved into place only on success. The callers zip whole directories, and a
        // truncated archive inside one of them is far harder to diagnose than a missing repository plus a reported error.
        // A move also covers the failures a catch block cannot, such as running out of memory on a large repository.
        Path partialFilePath = targetDirectory.resolve(zipFilePath.getFileName() + PARTIAL_EXPORT_SUFFIX);

        try {
            try (Repository bareRepository = gitService.getBareRepository(new LocalVCRepositoryUri(repositoryUri.toString()), false);
                    OutputStream outputStream = Files.newOutputStream(partialFilePath)) {
                if (content == RepositoryExportContent.WITH_HISTORY) {
                    InMemoryRepositoryBuilder.writeZip(bareRepository, outputStream);
                }
                else {
                    writeSnapshotArchive(bareRepository, outputStream);
                }
            }
            catch (GitAPIException e) {
                throw new IOException("Could not archive the repository " + repositoryUri, e);
            }
            FileUtil.publishAtomically(partialFilePath, zipFilePath);
        }
        finally {
            if (!FileUtils.deleteQuietly(partialFilePath.toFile()) && Files.exists(partialFilePath)) {
                log.error("Could not delete the incomplete export {}", partialFilePath);
            }
        }
        return zipFilePath;
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
        try (Repository repository = gitService.getBareRepository(new LocalVCRepositoryUri(repositoryUri.toString()), false)) {
            return createZipInputStreamResource(createInMemoryZipArchive(repository), filename);
        }
    }

    /**
     * Exports a repository with full history including the .git directory directly to memory.
     * The archive is assembled by {@link InMemoryRepositoryBuilder} from the bare repository's objects, so nothing is
     * cloned or checked out.
     *
     * @param repositoryUri the URI of the repository to export
     * @param filename      the desired filename for the export (without extension)
     * @return InputStreamResource containing the zipped repository content with full history
     * @throws IOException if IO operations fail
     */
    public InputStreamResource exportRepositoryWithFullHistoryToMemory(VcsRepositoryUri repositoryUri, String filename) throws IOException {
        try (Repository repository = gitService.getBareRepository(new LocalVCRepositoryUri(repositoryUri.toString()), false)) {
            return createZipInputStreamResource(InMemoryRepositoryBuilder.buildZip(repository), filename);
        }
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
            String repoName = getStudentRepositoryName(programmingExercise, participation, false);
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
     * Builds the name a student repository is exported under, either identifying the participant or anonymised.
     *
     * @param exercise        the programming exercise the participation belongs to
     * @param participation   the student participation whose repository is exported
     * @param hideStudentName whether the participant must not be identifiable from the name, e.g. for a double-blind export
     * @return the name for the exported repository, without a file extension
     */
    public String getStudentRepositoryName(ProgrammingExercise exercise, ProgrammingExerciseStudentParticipation participation, boolean hideStudentName) {
        String courseShortName = exercise.getCourseViaExerciseGroupOrCourseMember().getShortName();
        String repositoryName = FileUtil.sanitizeFilename(courseShortName + "-" + exercise.getTitle() + "-" + participation.getId());
        if (hideStudentName) {
            repositoryName += "-student-submission.git";
        }
        else {
            // The name is either the student login, the team short name or some default string.
            repositoryName += "-" + Objects.requireNonNullElse(participation.getParticipantIdentifier(), "student-submission" + participation.getId());
        }
        return participation.addPracticePrefixIfTestRun(repositoryName);
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
        ByteArrayOutputStream archiveData = new ByteArrayOutputStream();
        writeSnapshotArchive(repository, archiveData);
        return archiveData.toByteArray();
    }

    /**
     * Writes the working tree of the repository's current branch to the given stream as a zip, using JGit's
     * {@link org.eclipse.jgit.api.ArchiveCommand} so that nothing is checked out. The stream is left open.
     *
     * <p>
     * A repository whose {@code HEAD} does not resolve has no commits at all, which happens when its setup failed
     * halfway. That is reported rather than silently yielding an empty archive, because an empty zip inside a course
     * archive looks like an empty repository and hides the failure.
     *
     * @param repository   the bare repository to archive
     * @param outputStream the stream the archive is written to; it stays open, so the caller keeps ownership of it
     * @throws GitAPIException if the archive command fails
     * @throws IOException     if the repository has no commits or cannot be read
     */
    private void writeSnapshotArchive(Repository repository, OutputStream outputStream) throws GitAPIException, IOException {
        ObjectId treeId = repository.resolve(Constants.HEAD);
        if (treeId == null) {
            throw new IOException("Cannot archive the repository " + repository.getRemoteRepositoryUri() + " because HEAD does not resolve, so it has no commits");
        }
        try (Git git = new Git(repository)) {
            // Close-shielded because ArchiveCommand closes the stream it is handed, which is not this method's contract.
            git.archive().setFormat("zip").setTree(treeId).setOutputStream(CloseShieldOutputStream.wrap(outputStream)).call();
        }
    }
}
