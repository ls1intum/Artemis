package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.jenkins.service.JenkinsXmlFileUtils.getDocumentBuilderFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.dto.RepositoryExportOptionsDTO;
import de.tum.cit.aet.artemis.core.service.ArchivalReportEntry;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.ZipFileService;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseWithSubmissionsExportService;
import de.tum.cit.aet.artemis.localvc.service.GitRepositoryExportService;
import de.tum.cit.aet.artemis.localvc.service.GitRepositoryExportService.RepositoryExportContent;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.exception.GitException;
import de.tum.cit.aet.artemis.programming.exception.VersionControlException;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.BuildPlanRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Service for exporting programming exercises.
 */

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingExerciseExportService extends ExerciseWithSubmissionsExportService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseExportService.class);

    // The downloaded repos should be cloned into another path in order to not interfere with the repo used by the student
    @Value("${artemis.repo-download-clone-path}")
    private Path repoDownloadClonePath;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final FileService fileService;

    private final GitService gitService;

    private final GitRepositoryExportService gitRepositoryExportService;

    private final RepositoryExportGitService repositoryExportGitService;

    private final ZipFileService zipFileService;

    private final BuildPlanRepository buildPlanRepository;

    public static final String EXPORTED_EXERCISE_DETAILS_FILE_PREFIX = "Exercise-Details";

    public static final String EXPORTED_EXERCISE_PROBLEM_STATEMENT_FILE_PREFIX = "Problem-Statement";

    public static final String BUILD_PLAN_FILE_NAME = "buildPlan.txt";

    /**
     * How long the shared checkout directory survives if the export never reaches its own cleanup. Generous on purpose:
     * deleting it while clones are still running would recreate the very failure this class had before.
     */
    private static final long CHECKOUT_DIRECTORY_BACKSTOP_DELETION_DELAY_IN_MINUTES = 60;

    /**
     * The widest a repository export runs, reached only when there are at least that many repositories to export.
     * <p>
     * A course archive has hundreds and wants the width. A data export has one repository per exercise, and sizing the
     * pool to the work keeps it from starting a thread to hand a single git operation to.
     */
    private static final int MAX_CONCURRENT_REPOSITORY_EXPORTS = 10;

    public ProgrammingExerciseExportService(ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseTaskService programmingExerciseTaskService,
            StudentParticipationRepository studentParticipationRepository, FileService fileService, GitService gitService, GitRepositoryExportService gitRepositoryExportService,
            RepositoryExportGitService repositoryExportGitService, ZipFileService zipFileService, ObjectMapper objectMapper,
            AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, BuildPlanRepository buildPlanRepository) {
        // Programming exercises do not have a submission export service
        super(fileService, objectMapper, null);
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.fileService = fileService;
        this.gitService = gitService;
        this.gitRepositoryExportService = gitRepositoryExportService;
        this.repositoryExportGitService = repositoryExportGitService;
        this.zipFileService = zipFileService;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.buildPlanRepository = buildPlanRepository;
    }

    /**
     * Export programming exercise material for instructors including instructor repositories, problem statement (.md) and exercise detail (.json).
     * <p>
     * Optionally, student repositories can be included as well.
     *
     * @param exercise              the programming exercise
     * @param exportErrors          List of failures that occurred during the export
     * @param includeStudentRepos   flag that indicates whether the student repos should also be exported
     * @param shouldZipDirs         flag that indicates whether the directories should be zipped (this is necessary for the import to work)
     * @param exportDir             the directory used to store the zip file
     * @param archivalReportEntries List of all exercises and their statistics
     * @return the path to the zip file
     */
    private Path exportProgrammingExerciseMaterialWithStudentReposOptional(ProgrammingExercise exercise, List<String> exportErrors, boolean includeStudentRepos,
            boolean shouldZipDirs, Optional<Path> exportDir, List<ArchivalReportEntry> archivalReportEntries, List<Path> pathsToBeZipped) throws IOException {
        if (exportDir.isEmpty()) {
            // Create export directory for programming exercises
            exportDir = Optional.of(fileService.createTemporaryDirectory(repoDownloadClonePath, "exercise-export-", 5));
        }

        // Add the exported zip folder containing template, solution, and tests repositories. Also export the build plan if one exists.
        // Wrap this in a try catch block to prevent the problem statement and exercise details not being exported if the repositories fail to export
        try {
            var repoExportsPaths = exportProgrammingExerciseRepositories(exercise, includeStudentRepos, shouldZipDirs, exportDir.orElseThrow(), exportErrors,
                    archivalReportEntries);
            repoExportsPaths.forEach(path -> {
                if (path != null) {
                    pathsToBeZipped.add(path);
                }
            });

            // Export the build plan of a programming exercise, if one exists. Only relevant for Jenkins setups.
            var buildPlan = buildPlanRepository.findByProgrammingExercises_Id(exercise.getId());
            if (buildPlan.isPresent()) {
                Path buildPlanPath = exportDir.orElseThrow().resolve(BUILD_PLAN_FILE_NAME);
                FileUtils.writeStringToFile(buildPlanPath.toFile(), buildPlan.orElseThrow().getBuildPlan(), StandardCharsets.UTF_8);
                pathsToBeZipped.add(buildPlanPath);
            }

        }
        catch (Exception e) {
            exportErrors.add("Failed to export programming exercise repositories: " + e.getMessage());
        }

        // Add problem statement as .md file
        exportProblemStatementAndEmbeddedFilesAndExerciseDetails(exercise, exportErrors, exportDir.orElseThrow(), pathsToBeZipped);

        return exportDir.orElseThrow();
    }

    @Override
    protected void exportProblemStatementAndEmbeddedFilesAndExerciseDetails(Exercise exercise, List<String> exportErrors, Path exportDir, List<Path> pathsToBeZipped)
            throws IOException {
        if (exercise instanceof ProgrammingExercise programmingExercise) {
            // Used for a save typecast, this should always be true since this class only works with programming exercises.
            programmingExerciseTaskService.replaceTestIdsWithNames(programmingExercise);
            programmingExercise.setAuxiliaryRepositories(auxiliaryRepositoryRepository.findByExerciseId(exercise.getId()));
        }
        super.exportProblemStatementAndEmbeddedFilesAndExerciseDetails(exercise, exportErrors, exportDir, pathsToBeZipped);
    }

    /**
     * Exports a programming exercise for archival purposes. This includes the instructor repositories, the student repositories, the problem statement, and the exercise details.
     *
     * @param exercise              the programming exercise
     * @param exportErrors          List of failures that occurred during the export
     * @param exportDir             the directory used to store the exported exercise
     * @param archivalReportEntries List of all exercises and their statistics
     * @return the path to the exported exercise
     */
    public Optional<Path> exportProgrammingExerciseForArchival(ProgrammingExercise exercise, List<String> exportErrors, Optional<Path> exportDir,
            List<ArchivalReportEntry> archivalReportEntries) {
        try {
            return Optional.of(exportProgrammingExerciseMaterialWithStudentReposOptional(exercise, exportErrors, true, false, exportDir, archivalReportEntries, new ArrayList<>()));
        }
        catch (IOException e) {
            // this should actually never happen because all operations that throw an IOException are not executed when calling the method with an exportDir
            log.error("Failed to export programming exercise for archival: {}", e.getMessage());
            exportErrors.add("Failed to export programming exercise for archival: " + e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Exports a programming exercise for download purposes. This includes the instructor repositories, the problem statement, and the exercise details.
     *
     * @param exercise     the programming exercise to export
     * @param exportErrors List of failures that occurred during the export
     * @return the path to the exported exercise
     * @throws IOException if an error occurs while accessing the file system
     */
    public Path exportProgrammingExerciseForDownload(@NonNull ProgrammingExercise exercise, List<String> exportErrors) throws IOException {
        // Reset grading criterion ids to null, such that Hibernate can persist them.
        if (exercise.getGradingCriteria() != null) {
            for (GradingCriterion gradingCriterion : exercise.getGradingCriteria()) {
                gradingCriterion.setId(null);
                for (GradingInstruction gradingInstruction : gradingCriterion.getStructuredGradingInstructions()) {
                    gradingInstruction.setId(null);
                }
            }
        }

        List<Path> pathsToBeZipped = new ArrayList<>();
        Path exportDir = exportProgrammingExerciseMaterialWithStudentReposOptional(exercise, exportErrors, false, true, Optional.empty(), new ArrayList<>(), pathsToBeZipped);
        // Setup path to store the zip file for the exported programming exercise
        var timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-Hmss"));
        String exportedExerciseZipFileName = "Material-" + exercise.getCourseViaExerciseGroupOrCourseMember().getShortName() + "-" + exercise.getTitle() + "-" + exercise.getId()
                + "-" + timestamp + ".zip";
        String cleanFilename = FileUtil.sanitizeFilename(exportedExerciseZipFileName);
        Path pathToZippedExercise = exportDir.resolve(cleanFilename);
        // Create the zip folder of the exported programming exercise and return the path to the created folder
        zipFileService.createTemporaryZipFile(pathToZippedExercise, pathsToBeZipped, 5);
        return pathToZippedExercise;
    }

    /**
     * Export instructor repositories and optionally students' repositories in a zip file or
     * directory (without zipping).
     * <p>
     * The outputDir is used to store the zip file and temporary files used for zipping so make
     * sure to delete it if it's no longer used.
     *
     * @param exercise              the programming exercise
     * @param includingStudentRepos flag for including the students repos as well
     * @param shouldZipDirs         flag for zipping the directories
     * @param outputDir             the path to a directory that will be used to store the zipped programming exercise.
     * @param exportErrors          List of failures that occurred during the export
     * @param reportData            List of all exercises and their statistics
     * @return a list of paths to one zip file or more directories
     */
    public List<Path> exportProgrammingExerciseRepositories(ProgrammingExercise exercise, boolean includingStudentRepos, boolean shouldZipDirs, Path outputDir,
            List<String> exportErrors, List<ArchivalReportEntry> reportData) {
        log.info("Exporting programming exercise {} with title {}", exercise.getId(), exercise.getTitle());
        // List to add paths of files that should be contained in the zip folder of exported programming exercise repositories:
        // i.e., student repositories (if `includingStudentRepos` is true), instructor repositories template, solution and tests
        var pathsToBeZipped = new ArrayList<Path>();

        if (includingStudentRepos) {
            // Lazy load student participation, sort by id, and set the export options
            var studentParticipations = studentParticipationRepository.findByExerciseId(exercise.getId()).stream()
                    .map(studentParticipation -> (ProgrammingExerciseStudentParticipation) studentParticipation).sorted(Comparator.comparing(DomainObject::getId)).toList();
            // Filter late submissions must be false here, because we do not load submissions or commit hashes here
            var exportOptions = new RepositoryExportOptionsDTO(true, false, false, null, false, false, false, false, false);

            // Export student repositories and add them to list
            // Archives keep a snapshot of what the student submitted; the history is deliberately left out (see the
            // course archive documentation), which is also what lets these repositories be streamed without a checkout.
            var exportedStudentRepositoryFiles = exportStudentRepositories(exercise, studentParticipations, Map.of(), outputDir, exportErrors, exportOptions,
                    RepositoryExportContent.WORKING_TREE_ONLY).stream().filter(Objects::nonNull).toList();
            pathsToBeZipped.addAll(exportedStudentRepositoryFiles);
        }

        // Export the template, solution, and tests repositories and add them to list
        pathsToBeZipped.add(exportInstructorRepositoryForExercise(exercise.getId(), RepositoryType.TEMPLATE, outputDir, exportErrors).map(File::toPath).orElse(null));
        pathsToBeZipped.add(exportInstructorRepositoryForExercise(exercise.getId(), RepositoryType.SOLUTION, outputDir, exportErrors).map(File::toPath).orElse(null));
        pathsToBeZipped.add(exportInstructorRepositoryForExercise(exercise.getId(), RepositoryType.TESTS, outputDir, exportErrors).map(File::toPath).orElse(null));

        List<AuxiliaryRepository> auxiliaryRepositories = auxiliaryRepositoryRepository.findByExerciseId(exercise.getId());

        // Export the auxiliary repositories and add them to list
        auxiliaryRepositories.forEach(auxiliaryRepository -> pathsToBeZipped
                .add(exportInstructorAuxiliaryRepositoryForExercise(exercise.getId(), auxiliaryRepository, outputDir, exportErrors).map(File::toPath).orElse(null)));

        // Setup path to store the zip file for the exported repositories
        var timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-Hmss"));
        String filename = exercise.getCourseViaExerciseGroupOrCourseMember().getShortName() + "-" + exercise.getTitle() + "-" + exercise.getId() + "-" + timestamp + ".zip";
        String cleanFilename = FileUtil.sanitizeFilename(filename);
        Path pathToZippedExercise = Path.of(outputDir.toString(), cleanFilename);

        // Remove null elements and get the file path of each file to be included, i.e. each entry in the pathsToBeZipped list
        List<Path> includedFilePathsNotNull = pathsToBeZipped.stream().filter(Objects::nonNull).toList();

        String cleanProjectName = FileUtil.sanitizeFilename(exercise.getProjectName());
        // Add report entry, programming repositories cannot be skipped
        reportData.add(new ArchivalReportEntry(exercise, cleanProjectName, pathsToBeZipped.size(), includedFilePathsNotNull.size(), 0));

        try {
            // Only create zip file if there's files to zip
            if (includedFilePathsNotNull.isEmpty()) {
                String info = "Will not export programming exercise " + exercise.getId() + " with title " + exercise.getTitle() + " because it's empty";
                log.info(info);
                exportErrors.add(info);
                return List.of();
            }

            // Create the zip folder of the exported programming exercise and return the path to the created folder
            if (shouldZipDirs) {
                zipFileService.createZipFile(pathToZippedExercise, includedFilePathsNotNull);
                return List.of(pathToZippedExercise);
            }
            else {
                return includedFilePathsNotNull;
            }

        }
        catch (Exception e) {
            var error = "Failed to export programming exercise because the zip file " + pathToZippedExercise + " could not be created: " + e.getMessage();
            log.info(error);
            exportErrors.add(error);
            return List.of();
        }
    }

    /**
     * Exports a repository available for an instructor/tutor for a given programming exercise. This can be a template,
     * solution, or tests repository
     *
     * @param exerciseId     The id of the programming exercise that has the repository
     * @param repositoryType The type of repository to export
     * @param outputDir      The directory used for store the zip file
     * @param exportErrors   List of failures that occurred during the export
     * @return a zipped file
     */
    public Optional<File> exportInstructorRepositoryForExercise(long exerciseId, RepositoryType repositoryType, Path outputDir, List<String> exportErrors) {
        var exerciseOrEmpty = loadExerciseForRepoExport(exerciseId, exportErrors);
        if (exerciseOrEmpty.isEmpty()) {
            return Optional.empty();
        }
        var exercise = exerciseOrEmpty.get();
        String zippedRepoName = gitRepositoryExportService.getZippedRepoName(exercise, repositoryType.getName());
        var repositoryUri = exercise.getRepositoryURI(repositoryType);
        return exportRepository(repositoryUri, repositoryType.getName(), zippedRepoName, exercise, outputDir, exportErrors);
    }

    /**
     * Exports an auxiliary repository for a given programming exercise.
     *
     * @param exerciseId          The id of the programming exercise that has the repository
     * @param auxiliaryRepository the auxiliary repository to export
     * @param outputDir           The directory used for storing the zip file
     * @param exportErrors        List of failures that occurred during the export
     * @return the zipped file containing the auxiliary repository
     */
    public Optional<File> exportInstructorAuxiliaryRepositoryForExercise(long exerciseId, AuxiliaryRepository auxiliaryRepository, Path outputDir, List<String> exportErrors) {
        var exerciseOrEmpty = loadExerciseForRepoExport(exerciseId, exportErrors);
        if (exerciseOrEmpty.isEmpty()) {
            return Optional.empty();
        }
        var exercise = exerciseOrEmpty.get();
        String zippedRepoName = gitRepositoryExportService.getZippedRepoName(exercise, auxiliaryRepository.getRepositoryName());
        var repositoryUri = auxiliaryRepository.getVcsRepositoryUri();
        return exportRepository(repositoryUri, auxiliaryRepository.getName(), zippedRepoName, exercise, outputDir, exportErrors);
    }

    private Optional<ProgrammingExercise> loadExerciseForRepoExport(long exerciseId, List<String> exportErrors) {
        var exerciseOrEmpty = programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(exerciseId);
        if (exerciseOrEmpty.isEmpty()) {
            var error = "Failed to export instructor repository because the exercise " + exerciseId + " does not exist.";
            log.info(error);
            exportErrors.add(error);
            return Optional.empty();
        }

        var exercise = exerciseOrEmpty.get();
        log.info("Request to export instructor repository of programming exercise {} with title '{}'", exercise, exercise.getTitle());

        return Optional.of(exercise);
    }

    /**
     * Exports a given instructor repository, with its history, into a zip file in the output directory.
     *
     * <p>
     * Instructor repositories are never rewritten on export, so the zip is streamed straight from the bare repository
     * instead of cloning it first.
     *
     * @param repositoryUri  the url of the repository
     * @param zippedRepoName the name of the zip file
     * @param outputDir      the directory used for store the zip file
     * @return an optional containing the path to the zip file if the export was successful
     */
    private Optional<File> exportRepository(LocalVCRepositoryUri repositoryUri, String repositoryName, String zippedRepoName, ProgrammingExercise exercise, Path outputDir,
            List<String> exportErrors) {
        // It's not guaranteed that the repository uri is defined (old courses).
        if (repositoryUri == null) {
            var error = "Failed to export instructor repository " + repositoryName + " because the repository uri is not defined.";
            log.error(error);
            exportErrors.add(error);
            return Optional.empty();
        }

        try {
            Path zippedRepo = gitRepositoryExportService.exportRepositoryToZipFile(repositoryUri, outputDir, zippedRepoName, RepositoryExportContent.WITH_HISTORY);
            return Optional.of(zippedRepo.toFile());
        }
        catch (IOException | GitException | VersionControlException ex) {
            var error = "Failed to export instructor repository " + repositoryName + " for programming exercise '" + exercise.getTitle() + "' (id: " + exercise.getId() + ")";
            log.error("{}: {}", error, ex.getMessage());
            exportErrors.add(error);
        }
        return Optional.empty();
    }

    /**
     * Get participations of programming exercises of a requested list of students packed together in one zip file.
     * <p>
     * The repository download directory is used as the output directory and is destroyed after 5 minutes.
     *
     * @param programmingExercise       the programming exercise for which student repositories should be exported
     * @param participations            participations that should be exported
     * @param repositoryExportOptions   the options that should be used for the export
     * @param participationCommitHashes a map containing the relevant commit hashes to be used for each participation (typically only relevant when filtering for specific
     *                                      submissions)
     * @return a zip file containing all requested participations
     */
    public File exportStudentRepositoriesToZipFile(ProgrammingExercise programmingExercise, @NonNull Collection<ProgrammingExerciseStudentParticipation> participations,
            RepositoryExportOptionsDTO repositoryExportOptions, Map<Long, String> participationCommitHashes) {

        final Path outputDir;
        try {
            outputDir = fileService.createTemporaryDirectory(repoDownloadClonePath, "repo-export-", 10);
        }
        catch (IOException e) {
            log.error("Aborting export: could not create the output directory for exercise {} (id: {})", programmingExercise.getTitle(), programmingExercise.getId(), e);
            return null;
        }

        List<Path> zippedRepos;
        try {
            zippedRepos = exportStudentRepositories(programmingExercise, participations, participationCommitHashes, outputDir, new ArrayList<>(), repositoryExportOptions,
                    RepositoryExportContent.WITH_HISTORY);
        }
        catch (GitException e) {
            log.error("Aborting export: anonymization failed for at least one repository in exercise {} (id: {})", programmingExercise.getTitle(), programmingExercise.getId());
            return null;
        }

        try {
            // Create a zip folder containing the directories with the repositories.
            return createZipWithAllRepositories(programmingExercise, zippedRepos, outputDir);
        }
        catch (IOException ex) {
            log.error("Creating zip file for programming exercise {} did not work correctly: {} ", programmingExercise.getTitle(), ex.getMessage());
            return null;
        }
    }

    /**
     * Exports the repositories of the given participations into the output directory.
     *
     * <p>
     * A repository is streamed from its bare repository whenever the export options leave it unchanged; only the options
     * that rewrite history or the working tree need a checkout, and those share a single working directory created here.
     *
     * @param programmingExercise       the programming exercise
     * @param participations            participations that should be exported
     * @param participationCommitHashes a map containing the commit hashes to be used for each participation (typically only relevant when filtering for specific submissions)
     * @param outputDir                 The directory the exported repositories are placed in
     * @param exportErrors              A list of errors that occurred during export (populated by this function)
     * @param repositoryExportOptions   the options that should be used for the export (e.g. anonymization)
     * @param content                   whether the caller needs the history of the repositories. Both answers are served
     *                                      from the bare repository: a caller that wants a snapshot gets one ZIP per
     *                                      repository, a caller that wants the history gets a directory holding the
     *                                      repository, which is the layout those callers expect.
     * @return List of paths to the exported repositories, either zip files or directories, depending on {@code content}
     *         and the export options
     */
    public List<Path> exportStudentRepositories(ProgrammingExercise programmingExercise, @NonNull Collection<ProgrammingExerciseStudentParticipation> participations,
            Map<Long, String> participationCommitHashes, Path outputDir, List<String> exportErrors, RepositoryExportOptionsDTO repositoryExportOptions,
            RepositoryExportContent content) {
        var programmingExerciseId = programmingExercise.getId();
        if (repositoryExportOptions.exportAllParticipants()) {
            log.info("Request to export all {} student or team repositories of programming exercise {} with title '{}'", participations.size(), programmingExerciseId,
                    programmingExercise.getTitle());
        }
        else {
            log.info("Request to export the repositories of programming exercise {} with title '{}' of {} students or teams", programmingExerciseId, programmingExercise.getTitle(),
                    participations.size());
            log.debug("Export repositories for students or teams: {}",
                    participations.stream().map(StudentParticipation::getParticipantIdentifier).collect(Collectors.joining(", ")));
        }

        // One working directory for the whole export, shared by every participation that needs a checkout. Requesting one
        // per repository is what produced the colliding paths and racing cleanup tasks of issue #13575.
        // It is deleted right after the loop below; the scheduled deletion is only a backstop for an export that dies
        // halfway, and its delay has to outlast the whole checkout phase, which for an anonymising export of a large
        // exercise runs for many minutes.
        final Path checkoutDir;
        if (requiresCheckout(repositoryExportOptions)) {
            try {
                checkoutDir = fileService.createTemporaryDirectory(repoDownloadClonePath, "repo-checkout-", CHECKOUT_DIRECTORY_BACKSTOP_DELETION_DELAY_IN_MINUTES);
            }
            catch (IOException e) {
                var error = "Failed to export the repositories of programming exercise " + programmingExerciseId + " because the working directory could not be created: "
                        + e.getMessage();
                log.error(error);
                exportErrors.add(error);
                return List.of();
            }
        }
        else {
            checkoutDir = null;
        }

        List<Path> exportedStudentRepositoriesPaths = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean anonymizationFailed = new AtomicBoolean(false);
        // The tasks below collect their errors here rather than into the caller's list, because that list is not
        // required to be thread safe and exportStudentRepositoriesToZipFile passes a plain ArrayList. They are handed
        // over once the tasks have finished, on the thread that called this.
        List<String> collectedExportErrors = Collections.synchronizedList(new ArrayList<>());

        List<Runnable> exports = participations.stream().map(participation -> (Runnable) () -> {
            try {
                var relevantCommitHash = participationCommitHashes.get(participation.getId());
                log.debug("invoke exportStudentRepository for participation {}", participation.getId());
                Path repoOutputPath = exportStudentRepository(programmingExercise, participation, repositoryExportOptions, relevantCommitHash, checkoutDir, outputDir, content);
                if (repoOutputPath != null) {
                    exportedStudentRepositoriesPaths.add(repoOutputPath);
                }
            }
            catch (Exception exception) {
                var error = "Failed to export the student repository with participation: " + participation.getId() + " for programming exercise '" + programmingExercise.getTitle()
                        + "' (id: " + programmingExercise.getId() + ") because the repository couldn't be downloaded. ";
                collectedExportErrors.add(error);
                if (repositoryExportOptions.anonymizeRepository() && exception instanceof GitException) {
                    anonymizationFailed.set(true);
                }
            }
        }).toList();

        if (exports.size() <= 1) {
            // A single repository, which is what a data export asks for, runs here rather than on a thread of its own.
            log.info("export the student repositories of programming exercise {} on the calling thread", programmingExerciseId);
            exports.forEach(Runnable::run);
        }
        else {
            int concurrency = Math.min(exports.size(), MAX_CONCURRENT_REPOSITORY_EXPORTS);
            log.info("export {} student repositories for programming exercise {} on {} threads", exports.size(), programmingExerciseId, concurrency);
            try (var threadPool = Executors.newFixedThreadPool(concurrency)) {
                // wait until all operations finish
                CompletableFuture.allOf(exports.stream().map(export -> CompletableFuture.runAsync(export, threadPool)).toArray(CompletableFuture[]::new)).join();
            }
        }
        // Before the anonymization check below, so that a caller still learns which repositories failed even when the
        // export ends in that exception.
        exportErrors.addAll(collectedExportErrors);
        deleteCheckoutDirectory(checkoutDir);
        if (anonymizationFailed.get()) {
            throw new GitException("Anonymization failed for one or more repositories");
        }
        return exportedStudentRepositoriesPaths;
    }

    /**
     * Removes the shared checkout directory as soon as the export no longer needs it. Only the copies in the output
     * directory are returned to the caller, so nothing here is still referenced.
     */
    private void deleteCheckoutDirectory(@Nullable Path checkoutDir) {
        if (checkoutDir == null) {
            return;
        }
        if (!FileUtils.deleteQuietly(checkoutDir.toFile()) && Files.exists(checkoutDir)) {
            log.warn("Could not delete the checkout directory {}; the scheduled cleanup will retry", checkoutDir);
        }
    }

    /**
     * Creates one single zip archive containing all zipped repositories found under the given paths
     *
     * @param programmingExercise The programming exercise to which all repos belong to
     * @param pathsToZippedRepos  The paths to all zipped repositories
     * @param outputDir           The directory used for downloading and zipping the repository
     * @return the zip file
     */
    private File createZipWithAllRepositories(ProgrammingExercise programmingExercise, List<Path> pathsToZippedRepos, Path outputDir) throws IOException {
        if (pathsToZippedRepos.isEmpty()) {
            log.warn("The zip file could not be created. Ignoring the request to export repositories for exercise {}", programmingExercise.getTitle());
            return null;
        }

        log.debug("Create zip file for {} repositorie(s) of programming exercise: {}", pathsToZippedRepos.size(), programmingExercise.getTitle());
        String filename = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getShortName() + "-" + programmingExercise.getShortName() + "-" + System.currentTimeMillis()
                + ".zip";

        Path zipFilePath = Path.of(outputDir.toString(), filename);
        zipFileService.createZipFile(zipFilePath, pathsToZippedRepos);
        return zipFilePath.toFile();
    }

    /**
     * Whether the given export options change the repository, and therefore need a checkout to work on.
     *
     * <p>
     * Anonymizing and combining commits rewrite the history, adding the participant name rewrites project files, and
     * normalizing the code style rewrites every file; filtering late submissions moves the branch to an earlier commit.
     * Without any of them the export is a faithful copy of the repository and can be streamed from the bare repository
     * instead, which is the case for course and exam archiving and for the data export.
     *
     * <p>
     * Asking for the history does not require one: a repository can be materialized with its full history straight from
     * the bare repository, into a ZIP or into a directory. Only the options that rewrite what is exported need a working
     * copy to rewrite.
     *
     * @param repositoryExportOptions the options the export runs with
     * @return true if the repository has to be checked out before it can be exported
     */
    private static boolean requiresCheckout(RepositoryExportOptionsDTO repositoryExportOptions) {
        return repositoryExportOptions.filterLateSubmissions() || repositoryExportOptions.addParticipantName() || repositoryExportOptions.combineStudentCommits()
                || repositoryExportOptions.anonymizeRepository() || repositoryExportOptions.normalizeCodeStyle();
    }

    /**
     * Exports the repository of the given participation into the output directory and returns the path it was written to.
     *
     * <p>
     * Unless the export options require a checkout, the repository is streamed straight from its bare repository into
     * the output directory - as a ZIP for a snapshot, as a directory when the history is wanted: no clone, no working
     * copy and no temporary directory.
     *
     * @param programmingExercise     The programming exercise for the participation
     * @param participation           The participation, for which the repository should get zipped
     * @param repositoryExportOptions The options, that should get applied to the zipped repo
     * @param relevantCommitHash      The commit hash relevant for the submission (e.g. based on the submission date)
     * @param checkoutDir             The directory used to clone the repository, only needed for the rewriting options
     * @param outputDir               The directory where the exported repository is stored
     * @param content                 how much of the repository the caller asked for
     * @return The exported repository as a zip file, or as a directory when the history is wanted or an option rewrites
     *         the repository, or null if the participation was skipped
     * @throws IOException if zip file creation failed
     */
    @Nullable
    private Path exportStudentRepository(final ProgrammingExercise programmingExercise, final ProgrammingExerciseStudentParticipation participation,
            final RepositoryExportOptionsDTO repositoryExportOptions, @Nullable String relevantCommitHash, @Nullable Path checkoutDir, Path outputDir,
            RepositoryExportContent content) throws IOException, UncheckedIOException {
        if (participation.getVcsRepositoryUri() == null) {
            log.warn("Ignore participation {} for export, because its repository URI is null", participation.getId());
            return null;
        }

        if (repositoryExportOptions.excludePracticeSubmissions() && participation.isPracticeMode()) {
            log.debug("Ignoring practice participation {}", participation);
            return null;
        }

        if (!requiresCheckout(repositoryExportOptions)) {
            String repositoryName = gitRepositoryExportService.getStudentRepositoryName(programmingExercise, participation, false);
            // Callers asking for the history get a directory, which is the layout they have always produced; callers
            // asking for a snapshot get one ZIP per repository. Neither needs a working copy on the way.
            if (content == RepositoryExportContent.WITH_HISTORY) {
                return gitRepositoryExportService.exportRepositoryToDirectory(participation.getVcsRepositoryUri(), outputDir, repositoryName);
            }
            return gitRepositoryExportService.exportRepositoryToZipFile(participation.getVcsRepositoryUri(), outputDir, repositoryName, content);
        }

        try {
            var tempRepositoryPath = Objects.requireNonNull(checkoutDir, "A checkout directory is required for the selected export options")
                    .resolve(String.valueOf(participation.getId()));
            // Checkout the repository
            Repository repository = gitService.getOrCheckoutRepository(participation, tempRepositoryPath, false);
            if (repository == null) {
                log.warn("Cannot checkout repository for participation id: {}", participation.getId());
                return null;
            }

            String latestSetupCommitHash = null;
            if (repositoryExportOptions.combineStudentCommits() || repositoryExportOptions.anonymizeRepository()) {
                // only retrieve the setup commit once, even if it is needed for both operations
                latestSetupCommitHash = gitService.getFirstCommitWithMessage(repository, de.tum.cit.aet.artemis.core.config.Constants.SET_UP_TEMPLATE_FOR_EXERCISE);
            }

            if (repositoryExportOptions.filterLateSubmissions()) {
                filterLateSubmissions(repositoryExportOptions, relevantCommitHash, participation, repository);
            }

            if (repositoryExportOptions.addParticipantName()) {
                log.debug("Adding student or team name to participation {}", participation);
                addParticipantIdentifierToProjectName(repository, programmingExercise, participation);
            }

            if (repositoryExportOptions.combineStudentCommits()) {
                log.debug("Combining commits for participation {}", participation);
                repositoryExportGitService.combineAllStudentCommits(repository, repositoryExportOptions.anonymizeRepository(), latestSetupCommitHash);
            }

            if (repositoryExportOptions.anonymizeRepository()) {
                log.debug("Anonymizing commits for participation {}", participation);
                repositoryExportGitService.anonymizeStudentCommits(repository, latestSetupCommitHash);
                // Verify anonymization succeeded before proceeding
                repositoryExportGitService.verifyAnonymizationOrThrow(repository, repositoryExportOptions.combineStudentCommits(), latestSetupCommitHash);
            }
            else {
                gitService.removeRemotesFromRepository(repository);
            }

            if (repositoryExportOptions.normalizeCodeStyle()) {
                try {
                    log.debug("Normalizing code style for participation {}", participation);
                    FileUtil.normalizeLineEndingsDirectory(repository.getLocalPath());
                    FileUtil.convertFilesInDirectoryToUtf8(repository.getLocalPath());
                }
                catch (IOException ex) {
                    log.warn("Cannot normalize code style in the repository {} due to the following exception: {}", repository.getLocalPath(), ex.getMessage());
                }
            }

            log.debug("Create temporary directory for repository {}", repository.getLocalPath().toString());
            return gitRepositoryExportService.getRepositoryWithParticipation(repository, outputDir.toString(), repositoryExportOptions.anonymizeRepository());
        }
        catch (GitAPIException | GitException ex) {
            log.error("Failed to create zip for participation id {} with exercise id {} because of the following exception ", participation.getId(),
                    participation.getProgrammingExercise().getId(), ex);
            return null;
        }
    }

    /**
     * delete all files in the directory based on the given programming exercise and target path
     *
     * @param programmingExercise the programming exercise for which repos have been downloaded
     * @param targetPath          the path in which the repositories have been downloaded
     */
    public void deleteReposDownloadProjectRootDirectory(ProgrammingExercise programmingExercise, Path targetPath) {
        final String projectDirName = programmingExercise.getProjectKey();
        Path projectPath = targetPath.resolve(projectDirName);
        try {
            log.info("Delete project root directory {}", projectPath.toFile());
            FileUtils.deleteDirectory(projectPath.toFile());
        }
        catch (IOException ex) {
            log.warn("The project root directory '{}' could not be deleted.", projectPath, ex);
        }
    }

    /**
     * Filters out all late commits of submissions from the checked out repository of a participation
     *
     * @param repositoryExportOptions The options that should get applied when exporting the submissions
     * @param relevantCommitHash      The commit hash relevant for the submission (if any)
     * @param participation           The participation related to the repository
     * @param repo                    The repository for which to filter all late submissions
     */
    private void filterLateSubmissions(RepositoryExportOptionsDTO repositoryExportOptions, @Nullable String relevantCommitHash,
            ProgrammingExerciseStudentParticipation participation, Repository repo) {
        log.debug("Filter late submissions for participation {}", participation.toString());
        final Optional<ZonedDateTime> latestAllowedDate;
        if (repositoryExportOptions.filterLateSubmissionsIndividualDueDate()) {
            latestAllowedDate = ExerciseDateService.getDueDate(participation);
        }
        else {
            latestAllowedDate = Optional.of(repositoryExportOptions.filterLateSubmissionsDate());
        }

        if (latestAllowedDate.isPresent()) {
            repositoryExportGitService.filterLateSubmissions(repo, relevantCommitHash, latestAllowedDate.get());
        }
    }

    /**
     * Adds the participant identifier (student login or team short name) of the given student participation to the project name in all .project (Eclipse)
     * and pom.xml (Maven) files found in the given repository.
     *
     * @param repository          The repository for which the student id should get added
     * @param programmingExercise The checked out exercise in the repository
     * @param participation       The student participation for the student/team identifier, which should be added.
     */
    public void addParticipantIdentifierToProjectName(Repository repository, ProgrammingExercise programmingExercise, StudentParticipation participation) {
        String participantIdentifier = participation.addPracticePrefixIfTestRun(participation.getParticipantIdentifier());

        // Get all files in repository except .git files
        List<String> allRepoFiles = listAllFilesInPath(repository.getLocalPath());

        // is Java or Kotlin programming language
        if (programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA || programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.KOTLIN) {
            // Filter all Eclipse .project files
            List<String> eclipseProjectFiles = allRepoFiles.stream().filter(file -> file.endsWith(".project")).toList();

            for (String eclipseProjectFilePath : eclipseProjectFiles) {
                addParticipantIdentifierToEclipseProjectName(repository, participantIdentifier, eclipseProjectFilePath);
            }

            // Filter all pom.xml files
            List<String> pomFiles = allRepoFiles.stream().filter(file -> file.endsWith("pom.xml")).toList();
            for (String pomFilePath : pomFiles) {
                addParticipantIdentifierToMavenProjectName(repository, participantIdentifier, pomFilePath);
            }
        }

        try {
            boolean hasChanges = gitService.stageAllChanges(repository);
            if (hasChanges) {
                gitService.commit(repository, "Add participant identifier (student login or team short name) to project name");
            }
        }
        catch (GitAPIException ex) {
            log.error("Cannot stage or commit to the repository {}", repository.getLocalPath(), ex);
        }
        finally {
            // if repo is not closed, it causes weird IO issues when trying to delete the repo again
            // java.io.IOException: Unable to delete file: ...\.git\objects\pack\...
            repository.close();
        }
    }

    private void addParticipantIdentifierToMavenProjectName(Repository repo, String participantIdentifier, String pomFilePath) {
        try {
            File pomFile = Path.of(pomFilePath).toFile();
            // check if file exists and full file name is pom.xml and not just the file ending.
            if (!pomFile.exists() || !pomFile.getName().equals("pom.xml")) {
                return;
            }

            // 1- Build the doc from the XML file
            Document doc = getDocumentBuilderFactory().newDocumentBuilder().parse(new InputSource(pomFile.getPath()));
            doc.setXmlStandalone(true);

            // 2- Find the relevant nodes with xpath
            XPath xPath = XPathFactory.newInstance().newXPath();
            Node nameNode = (Node) xPath.compile("/project/name").evaluate(doc, XPathConstants.NODE);
            Node artifactIdNode = (Node) xPath.compile("/project/artifactId").evaluate(doc, XPathConstants.NODE);

            // 3- Append Participant Identifier (student login or team short name) to Project Names
            if (nameNode != null) {
                nameNode.setTextContent(nameNode.getTextContent() + " " + participantIdentifier);
            }
            if (artifactIdNode != null) {
                String artifactId = (artifactIdNode.getTextContent() + "-" + participantIdentifier).replaceAll(" ", "-").toLowerCase();
                artifactIdNode.setTextContent(artifactId);
            }

            // 4- Save the result to a new XML doc
            Transformer xformer = TransformerFactory.newInstance().newTransformer();
            xformer.transform(new DOMSource(doc), new StreamResult(pomFile));

        }
        catch (SAXException | IOException | ParserConfigurationException | TransformerException | XPathException ex) {
            log.error("Cannot rename pom.xml file in {}", repo.getLocalPath(), ex);
        }
    }

    private void addParticipantIdentifierToEclipseProjectName(Repository repo, String participantIdentifier, String eclipseProjectFilePath) {
        try {
            File eclipseProjectFile = Path.of(eclipseProjectFilePath).toFile();
            // Check if file exists and full file name is .project and not just the file ending.
            if (!eclipseProjectFile.exists() || !eclipseProjectFile.getName().equals(".project")) {
                return;
            }

            // 1- Build the doc from the XML file
            Document doc = getDocumentBuilderFactory().newDocumentBuilder().parse(new InputSource(eclipseProjectFile.getPath()));
            doc.setXmlStandalone(true);

            // 2- Find the node with xpath
            XPath xPath = XPathFactory.newInstance().newXPath();
            Node nameNode = (Node) xPath.compile("/projectDescription/name").evaluate(doc, XPathConstants.NODE);

            // 3- Append Participant Identifier (student login or team short name) to Project Name
            if (nameNode != null) {
                nameNode.setTextContent(nameNode.getTextContent() + " " + participantIdentifier);
            }

            // 4- Save the result to a new XML doc
            Transformer xformer = TransformerFactory.newInstance().newTransformer();
            xformer.transform(new DOMSource(doc), new StreamResult(eclipseProjectFile));

        }
        catch (SAXException | IOException | ParserConfigurationException | TransformerException | XPathException ex) {
            log.error("Cannot rename .project file in {}", repo.getLocalPath(), ex);
        }
    }

    /**
     * Get all files in path except .git files
     *
     * @param path The path for which all file names should be listed
     * @return an unmodifiable list of all file names under the given path
     */
    private List<String> listAllFilesInPath(Path path) {
        List<String> allRepoFiles = List.of();
        try (Stream<Path> walk = Files.walk(path)) {
            allRepoFiles = walk.filter(Files::isRegularFile).map(Path::toString).filter(fileName -> !fileName.contains(File.separator + ".git" + File.separator)).toList();
        }
        catch (IOException | SecurityException e) {
            log.error("Cannot list all files in path {}: {}", path, e.getMessage());
        }
        return allRepoFiles;
    }

}
