package de.tum.in.www1.artemis.service.programming;

import static de.tum.in.www1.artemis.service.util.XmlFileUtils.getDocumentBuilderFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.exception.GitException;
import de.tum.in.www1.artemis.repository.AuxiliaryRepositoryRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.service.ExerciseDateService;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.ZipFileService;
import de.tum.in.www1.artemis.service.archival.ArchivalReportEntry;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryExportOptionsDTO;

@Service
public class ProgrammingExerciseExportService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseExportService.class);

    // The downloaded repos should be cloned into another path in order to not interfere with the repo used by the student
    @Value("${artemis.repo-download-clone-path}")
    private String repoDownloadClonePath;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final ExerciseDateService exerciseDateService;

    private final ObjectMapper objectMapper;

    private final FileService fileService;

    private final GitService gitService;

    private final ZipFileService zipFileService;

    public static final String EXPORTED_EXERCISE_DETAILS_FILE_PREFIX = "Exercise-Details";

    public static final String EXPORTED_EXERCISE_PROBLEM_STATEMENT_FILE_PREFIX = "Problem-Statement";

    public ProgrammingExerciseExportService(ProgrammingExerciseRepository programmingExerciseRepository, StudentParticipationRepository studentParticipationRepository,
            ExerciseDateService exerciseDateService, FileService fileService, GitService gitService, ZipFileService zipFileService,
            MappingJackson2HttpMessageConverter springMvcJacksonConverter, AuxiliaryRepositoryRepository auxiliaryRepositoryRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.objectMapper = springMvcJacksonConverter.getObjectMapper();
        this.exerciseDateService = exerciseDateService;
        this.fileService = fileService;
        this.gitService = gitService;
        this.zipFileService = zipFileService;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
    }

    /**
     * Export programming exercise material for instructors including instructor repositories, problem statement (.md) and exercise detail (.json).
     *
     * @param exercise     the programming exercise
     * @param exportErrors List of failures that occurred during the export
     * @return the path to the zip file
     */
    public Path exportProgrammingExerciseInstructorMaterial(ProgrammingExercise exercise, List<String> exportErrors) {
        // Create export directory for programming exercises
        var exportDir = Path.of(repoDownloadClonePath, "programming-exercise-material");
        fileService.createDirectory(exportDir);

        // List to add paths of files that should be contained in the zip folder of exported programming exercise:
        // i.e., problem statement, exercise details, instructor repositories
        var pathsToBeZipped = new ArrayList<Path>();

        // Add the exported zip folder containing template, solution, and tests repositories
        // Ignore report data
        pathsToBeZipped.add(exportProgrammingExerciseRepositories(exercise, false, exportDir, exportErrors, new ArrayList<>()));

        // Add problem statement as .md file
        var problemStatementFileExtension = ".md";
        String problemStatementFileName = EXPORTED_EXERCISE_PROBLEM_STATEMENT_FILE_PREFIX + "-" + exercise.getTitle() + problemStatementFileExtension;
        String cleanProblemStatementFileName = fileService.removeIllegalCharacters(problemStatementFileName);
        var problemStatementExportPath = Path.of(exportDir.toString(), cleanProblemStatementFileName);
        pathsToBeZipped.add(fileService.writeStringToFile(exercise.getProblemStatement(), problemStatementExportPath));

        // Add programming exercise details (object) as .json file
        var exerciseDetailsFileExtension = ".json";
        String exerciseDetailsFileName = EXPORTED_EXERCISE_DETAILS_FILE_PREFIX + "-" + exercise.getTitle() + exerciseDetailsFileExtension;
        String cleanExerciseDetailsFileName = fileService.removeIllegalCharacters(exerciseDetailsFileName);
        var exerciseDetailsExportPath = Path.of(exportDir.toString(), cleanExerciseDetailsFileName);
        pathsToBeZipped.add(fileService.writeObjectToJsonFile(exercise, this.objectMapper, exerciseDetailsExportPath));

        // Setup path to store the zip file for the exported programming exercise
        var timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-Hmss"));
        String exportedExerciseZipFileName = "Material-" + exercise.getCourseViaExerciseGroupOrCourseMember().getShortName() + "-" + exercise.getTitle() + "-" + exercise.getId()
                + "-" + timestamp + ".zip";
        String cleanFilename = fileService.removeIllegalCharacters(exportedExerciseZipFileName);
        Path pathToZippedExercise = Path.of(exportDir.toString(), cleanFilename);

        // Create the zip folder of the exported programming exercise and return the path to the created folder
        try {
            zipFileService.createZipFile(pathToZippedExercise, pathsToBeZipped, false);
            return pathToZippedExercise;
        }
        catch (IOException e) {
            var error = "Failed to export programming exercise because the zip file " + pathToZippedExercise + " could not be created: " + e.getMessage();
            log.info(error);
            exportErrors.add(error);
            return null;
        }
        finally {
            // Delete the export directory
            fileService.scheduleForDirectoryDeletion(exportDir, 5);
        }
    }

    /**
     * Export instructor repositories and optionally students' repositories in a zip file.
     *
     * The outputDir is used to store the zip file and temporary files used for zipping so make
     * sure to delete it if it's no longer used.
     *
     * @param exercise              the programming exercise
     * @param includingStudentRepos flag for including the students repos as well
     * @param outputDir             the path to a directory that will be used to store the zipped programming exercise.
     * @param exportErrors          List of failures that occurred during the export
     * @param reportData            List of all exercises and their statistics
     * @return the path to the zip file
     */
    public Path exportProgrammingExerciseRepositories(ProgrammingExercise exercise, Boolean includingStudentRepos, Path outputDir, List<String> exportErrors,
            List<ArchivalReportEntry> reportData) {
        log.info("Exporting programming exercise {} with title {}", exercise.getId(), exercise.getTitle());
        // List to add paths of files that should be contained in the zip folder of exported programming exercise repositories:
        // i.e., student repositories (if `includingStudentRepos` is true), instructor repositories template, solution and tests
        var pathsToBeZipped = new ArrayList<Path>();

        if (includingStudentRepos) {
            // Lazy load student participation, sort by id, and set the export options
            var studentParticipations = studentParticipationRepository.findByExerciseId(exercise.getId()).stream()
                    .map(studentParticipation -> (ProgrammingExerciseStudentParticipation) studentParticipation).sorted(Comparator.comparing(DomainObject::getId)).toList();
            var exportOptions = new RepositoryExportOptionsDTO();
            exportOptions.setHideStudentNameInZippedFolder(false);

            // Export student repositories and add them to list
            var exportedStudentRepositoryFiles = exportStudentRepositories(exercise, studentParticipations, exportOptions, outputDir, exportErrors).stream()
                    .filter(Objects::nonNull).toList();
            pathsToBeZipped.addAll(exportedStudentRepositoryFiles);
        }

        // Export the template, solution, and tests repositories and add them to list
        pathsToBeZipped.add(exportInstructorRepositoryForExercise(exercise.getId(), RepositoryType.TEMPLATE, outputDir, exportErrors).map(File::toPath).orElse(null));
        pathsToBeZipped.add(exportInstructorRepositoryForExercise(exercise.getId(), RepositoryType.SOLUTION, outputDir, exportErrors).map(File::toPath).orElse(null));
        pathsToBeZipped.add(exportInstructorRepositoryForExercise(exercise.getId(), RepositoryType.TESTS, outputDir, exportErrors).map(File::toPath).orElse(null));

        List<AuxiliaryRepository> auxiliaryRepositories = auxiliaryRepositoryRepository.findByExerciseId(exercise.getId());

        // Export the auxiliary repositories and add them to list
        auxiliaryRepositories.forEach(auxiliaryRepository -> {
            pathsToBeZipped.add(exportInstructorAuxiliaryRepositoryForExercise(exercise.getId(), auxiliaryRepository, outputDir, exportErrors).map(File::toPath).orElse(null));
        });

        // Setup path to store the zip file for the exported repositories
        var timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-Hmss"));
        String filename = exercise.getCourseViaExerciseGroupOrCourseMember().getShortName() + "-" + exercise.getTitle() + "-" + exercise.getId() + "-" + timestamp + ".zip";
        String cleanFilename = fileService.removeIllegalCharacters(filename);
        Path pathToZippedExercise = Path.of(outputDir.toString(), cleanFilename);

        // Remove null elements and get the file path of each file to be included, i.e. each entry in the pathsToBeZipped list
        List<Path> includedFilePathsNotNull = pathsToBeZipped.stream().filter(Objects::nonNull).toList();

        String cleanProjectName = fileService.removeIllegalCharacters(exercise.getProjectName());
        // Add report entry, programming repositories cannot be skipped
        reportData.add(new ArchivalReportEntry(exercise, cleanProjectName, pathsToBeZipped.size(), includedFilePathsNotNull.size(), 0));

        try {
            // Only create zip file if there's files to zip
            if (includedFilePathsNotNull.isEmpty()) {
                String info = "Will not export programming exercise " + exercise.getId() + " with title " + exercise.getTitle() + " because it's empty";
                log.info(info);
                exportErrors.add(info);
                return null;
            }

            // Create the zip folder of the exported programming exercise and return the path to the created folder
            zipFileService.createZipFile(pathToZippedExercise, includedFilePathsNotNull, false);
            return pathToZippedExercise;
        }
        catch (Exception e) {
            var error = "Failed to export programming exercise because the zip file " + pathToZippedExercise + " could not be created: " + e.getMessage();
            log.info(error);
            exportErrors.add(error);
            return null;
        }
    }

    /**
     * Exports a repository available for an instructor/tutor for a given programming exercise. This can be a template,
     * solution, or tests repository.
     *
     * The repository download directory is used as the output directory and is destroyed after 5 minutes.
     * @param exerciseId The id of the programming exercise that has the repository
     * @param repositoryType the type of repository to export
     * @param exportErrors List of failures that occurred during the export
     * @return a zipped file
     */
    public Optional<File> exportInstructorRepositoryForExercise(long exerciseId, RepositoryType repositoryType, List<String> exportErrors) {
        Path outputDir = fileService.getUniquePath(repoDownloadClonePath);
        return exportInstructorRepositoryForExercise(exerciseId, repositoryType, outputDir, exportErrors);
    }

    /**
     * Exports a solution repository available for an instructor/tutor/student for a given programming exercise.
     *
     * The repository download directory is used as the output directory and is destroyed after 5 minutes.
     * @param exerciseId The id of the programming exercise that has the repository
     * @param exportErrors List of failures that occurred during the export
     * @return a zipped file
     */
    public Optional<File> exportSolutionRepositoryForExercise(long exerciseId, List<String> exportErrors) {
        Path outputDir = fileService.getUniquePath(repoDownloadClonePath);
        return exportSolutionRepositoryForExercise(exerciseId, outputDir, exportErrors);
    }

    /**
     * Exports an auxiliary repository available for an instructor/editor/tutor for a given programming exercise.
     *
     * The repository download directory is used as the output directory and is destroyed after 5 minutes.
     * @param exerciseId The id of the programming exercise that has the repository
     * @param auxiliaryRepository the auxiliary repository to export
     * @param exportErrors List of failures that occurred during the export
     * @return a zipped file
     */
    public Optional<File> exportInstructorAuxiliaryRepositoryForExercise(long exerciseId, AuxiliaryRepository auxiliaryRepository, List<String> exportErrors) {
        Path outputDir = fileService.getUniquePath(repoDownloadClonePath);
        return exportInstructorAuxiliaryRepositoryForExercise(exerciseId, auxiliaryRepository, outputDir, exportErrors);
    }

    /**
     * Exports a repository available for an instructor/tutor for a given programming exercise. This can be a template,
     * solution, or tests repository
     *
     * @param exerciseId     The id of the programming exercise that has the repository
     * @param repositoryType the type of repository to export
     * @param outputDir The directory used for store the zip file
     * @param exportErrors   List of failures that occurred during the export
     * @return a zipped file
     */
    public Optional<File> exportInstructorRepositoryForExercise(long exerciseId, RepositoryType repositoryType, Path outputDir, List<String> exportErrors) {
        var exerciseOrEmpty = loadExerciseForRepoExport(exerciseId, repositoryType.getName(), exportErrors);
        if (exerciseOrEmpty.isEmpty()) {
            return Optional.empty();
        }
        var exercise = exerciseOrEmpty.get();
        String zippedRepoName = getZippedRepoName(exercise, repositoryType.getName());
        var repositoryUrl = exercise.getRepositoryURL(repositoryType);
        return exportRepository(repositoryUrl, repositoryType.getName(), zippedRepoName, exercise, outputDir, null, exportErrors);
    }

    /**
     * Exports an auxiliary repository for a given programming exercise.
     *
     * @param exerciseId     The id of the programming exercise that has the repository
     * @param auxiliaryRepository the auxiliary repository to export
     * @param outputDir The directory used for storing the zip file
     * @param exportErrors   List of failures that occurred during the export
     * @return the zipped file containing the auxiliary repository
     */
    public Optional<File> exportInstructorAuxiliaryRepositoryForExercise(long exerciseId, AuxiliaryRepository auxiliaryRepository, Path outputDir, List<String> exportErrors) {
        var exerciseOrEmpty = loadExerciseForRepoExport(exerciseId, auxiliaryRepository.getName(), exportErrors);
        if (exerciseOrEmpty.isEmpty()) {
            return Optional.empty();
        }
        var exercise = exerciseOrEmpty.get();
        String zippedRepoName = getZippedRepoName(exercise, auxiliaryRepository.getRepositoryName());
        var repositoryUrl = auxiliaryRepository.getVcsRepositoryUrl();
        return exportRepository(repositoryUrl, auxiliaryRepository.getName(), zippedRepoName, exercise, outputDir, null, exportErrors);
    }

    /**
     * Exports the solution repository available for an instructor/tutor/student for a given programming exercise.
     * Removes the ".git" directory from the resulting zip file to prevent leaking unintended information to students.
     *
     * @param exerciseId     The id of the programming exercise that has the repository
     * @param outputDir The directory used for store the zip file
     * @param exportErrors   List of failures that occurred during the export
     * @return a zipped file
     */
    public Optional<File> exportSolutionRepositoryForExercise(long exerciseId, Path outputDir, List<String> exportErrors) {
        RepositoryType repositoryType = RepositoryType.SOLUTION;
        var exerciseOrEmpty = loadExerciseForRepoExport(exerciseId, repositoryType.getName(), exportErrors);
        if (exerciseOrEmpty.isEmpty()) {
            return Optional.empty();
        }
        var exercise = exerciseOrEmpty.get();
        String zippedRepoName = getZippedRepoName(exercise, repositoryType.getName());
        var repositoryUrl = exercise.getRepositoryURL(repositoryType);

        Predicate<Path> gitDirFilter = path -> StreamSupport.stream(path.spliterator(), false).noneMatch(pathPart -> ".git".equalsIgnoreCase(pathPart.toString()));

        return exportRepository(repositoryUrl, repositoryType.getName(), zippedRepoName, exercise, outputDir, gitDirFilter, exportErrors);
    }

    private Optional<ProgrammingExercise> loadExerciseForRepoExport(long exerciseId, String repositoryName, List<String> exportErrors) {
        var exerciseOrEmpty = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(exerciseId);
        if (exerciseOrEmpty.isEmpty()) {
            var error = "Failed to export instructor repository " + repositoryName + " because the exercise " + exerciseId + " does not exist.";
            log.info(error);
            exportErrors.add(error);
            return Optional.empty();
        }

        var exercise = exerciseOrEmpty.get();
        log.info("Request to export instructor repository of type {} of programming exercise {} with title '{}'", repositoryName, exercise, exercise.getTitle());

        return Optional.of(exercise);
    }

    private String getZippedRepoName(ProgrammingExercise exercise, String repositoryName) {
        String courseShortName = exercise.getCourseViaExerciseGroupOrCourseMember().getShortName();
        return fileService.removeIllegalCharacters(courseShortName + "-" + exercise.getTitle() + "-" + repositoryName);
    }

    private Optional<File> exportRepository(VcsRepositoryUrl repositoryUrl, String repositoryName, String zippedRepoName, ProgrammingExercise exercise, Path outputDir,
            @Nullable Predicate<Path> contentFilter, List<String> exportErrors) {
        try {
            // It's not guaranteed that the repository url is defined (old courses).
            if (repositoryUrl == null) {
                var error = "Failed to export instructor repository " + repositoryName + " because the repository url is not defined.";
                log.info(error);
                exportErrors.add(error);
                return Optional.empty();
            }

            Path zippedRepo = createZipForRepository(repositoryUrl, zippedRepoName, outputDir, contentFilter);
            if (zippedRepo != null) {
                return Optional.of(new File(zippedRepo.toString()));
            }
        }
        catch (Exception ex) {
            var error = "Failed to export instructor repository " + repositoryName + " for programming exercise '" + exercise.getTitle() + "' (id: " + exercise.getId() + ")";
            log.info("{}: {}", error, ex.getMessage());
            exportErrors.add(error);
        }
        return Optional.empty();
    }

    /**
     * Get participations of programming exercises of a requested list of students packed together in one zip file.
     *
     * The repository download directory is used as the output directory and is destroyed after 5 minutes.
     *
     * @param programmingExerciseId   the id of the exercise entity
     * @param participations          participations that should be exported
     * @param repositoryExportOptions the options that should be used for the export
     * @return a zip file containing all requested participations
     */
    public File exportStudentRepositoriesToZipFile(long programmingExerciseId, @NotNull List<ProgrammingExerciseStudentParticipation> participations,
            RepositoryExportOptionsDTO repositoryExportOptions) {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId)
                .get();

        Path outputDir = fileService.getUniquePath(repoDownloadClonePath);
        var zippedRepos = exportStudentRepositories(programmingExercise, participations, repositoryExportOptions, outputDir, new ArrayList<>());

        try {
            // Create a zip folder containing the zipped repositories.
            return createZipWithAllRepositories(programmingExercise, zippedRepos, outputDir);
        }
        catch (IOException ex) {
            log.error("Creating zip file for programming exercise {} did not work correctly: {} ", programmingExercise.getTitle(), ex.getMessage());
            return null;
        }
        finally {
            fileService.scheduleForDirectoryDeletion(outputDir, 5);
        }
    }

    /**
     * Zip the participations of programming exercises of a requested list of students separately.
     *
     * @param programmingExercise     the programming exercise
     * @param participations          participations that should be exported
     * @param repositoryExportOptions the options that should be used for the export
     * @param outputDir The directory used for store the zip file
     * @param exportErrors            A list of errors that occurred during export (populated by this function)
     * @return List of zip file paths
     */
    public List<Path> exportStudentRepositories(ProgrammingExercise programmingExercise, @NotNull List<ProgrammingExerciseStudentParticipation> participations,
            RepositoryExportOptionsDTO repositoryExportOptions, Path outputDir, List<String> exportErrors) {
        var programmingExerciseId = programmingExercise.getId();
        if (repositoryExportOptions.isExportAllParticipants()) {
            log.info("Request to export all student or team repositories of programming exercise {} with title '{}'", programmingExerciseId, programmingExercise.getTitle());
        }
        else {
            log.info("Request to export the repositories of programming exercise {} with title '{}' of the following students or teams: {}", programmingExerciseId,
                    programmingExercise.getTitle(), participations.stream().map(StudentParticipation::getParticipantIdentifier).collect(Collectors.joining(", ")));
        }

        List<Path> exportedStudentRepositories = new ArrayList<>();
        participations.forEach(participation -> {
            try {
                Path zipFile = createZipForRepositoryWithParticipation(programmingExercise, participation, repositoryExportOptions, outputDir);
                if (zipFile != null) {
                    exportedStudentRepositories.add(zipFile);
                }
            }
            catch (Exception e) {
                var error = "Failed to export the student repository with participation: " + participation.getId() + " for programming exercise '" + programmingExercise.getTitle()
                        + "' (id: " + programmingExercise.getId() + ") because the repository couldn't be downloaded. ";
                exportErrors.add(error);
            }
        });
        return exportedStudentRepositories;
    }

    /**
     * Creates a zip file with the contents of the git repository. Note that the zip file is deleted in 5 minutes.
     *
     * @param repositoryUrl The url of the repository to zip
     * @param zipFilename   The name of the zip file
     * @param outputDir The directory used for downloading and zipping the repository
     * @param contentFilter The path filter to exclude some files, can be null to include everything
     * @return The path to the zip file.
     * @throws IOException if the zip file couldn't be created
     * @throws GitAPIException if the repo couldn't get checked out
     */
    private Path createZipForRepository(VcsRepositoryUrl repositoryUrl, String zipFilename, Path outputDir, @Nullable Predicate<Path> contentFilter)
            throws IOException, GitAPIException, GitException, UncheckedIOException {
        var repositoryDir = fileService.getUniquePathString(outputDir.toString());
        Repository repository;

        // Checkout the repository
        try (Repository repositoryToClose = gitService.getOrCheckoutRepository(repositoryUrl, repositoryDir, true)) {
            repository = repositoryToClose; // Try-with-resources requires the variable to be declared inside try.
            gitService.resetToOriginHead(repositoryToClose);
        }

        // Zip it and return the path to the file
        return gitService.zipRepository(repository, zipFilename, repositoryDir, contentFilter);
    }

    /**
     * Creates one single zip archive containing all zipped repositories found under the given paths
     *
     * @param programmingExercise The programming exercise to which all repos belong to
     * @param pathsToZippedRepos  The paths to all zipped repositories
     * @param outputDir The directory used for downloading and zipping the repository
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
        zipFileService.createZipFile(zipFilePath, pathsToZippedRepos, false);
        return new File(zipFilePath.toString());
    }

    /**
     * Checks out the repository for the given participation, zips it and adds the path to the given list of already
     * zipped repos.
     *
     * @param programmingExercise     The programming exercise for the participation
     * @param participation           The participation, for which the repository should get zipped
     * @param repositoryExportOptions The options, that should get applied to the zipped repo
     * @param outputDir The directory used for downloading and zipping the repository
     * @return The checked out and zipped repository
     * @throws IOException if zip file creation failed
     */
    private Path createZipForRepositoryWithParticipation(final ProgrammingExercise programmingExercise, final ProgrammingExerciseStudentParticipation participation,
            final RepositoryExportOptionsDTO repositoryExportOptions, Path outputDir) throws IOException, UncheckedIOException {
        if (participation.getVcsRepositoryUrl() == null) {
            log.warn("Ignore participation {} for export, because its repository URL is null", participation.getId());
            return null;
        }

        try {
            // Checkout the repository
            Repository repository = gitService.getOrCheckoutRepository(participation, outputDir.toString());
            if (repository == null) {
                log.warn("Cannot checkout repository for participation id: {}", participation.getId());
                return null;
            }

            gitService.resetToOriginHead(repository);

            if (repositoryExportOptions.isFilterLateSubmissions()) {
                filterLateSubmissions(repositoryExportOptions, participation, repository);
            }

            if (repositoryExportOptions.isAddParticipantName()) {
                log.debug("Adding student or team name to participation {}", participation);
                addParticipantIdentifierToProjectName(repository, programmingExercise, participation);
            }

            if (repositoryExportOptions.isCombineStudentCommits()) {
                log.debug("Combining commits for participation {}", participation);
                gitService.combineAllStudentCommits(repository, programmingExercise, repositoryExportOptions.isAnonymizeStudentCommits());
            }

            if (repositoryExportOptions.isAnonymizeStudentCommits()) {
                log.debug("Anonymizing commits for participation {}", participation);
                gitService.anonymizeStudentCommits(repository, programmingExercise);
            }

            if (repositoryExportOptions.isNormalizeCodeStyle()) {
                try {
                    log.debug("Normalizing code style for participation {}", participation);
                    fileService.normalizeLineEndingsDirectory(repository.getLocalPath().toString());
                    fileService.convertToUTF8Directory(repository.getLocalPath().toString());
                }
                catch (IOException ex) {
                    log.warn("Cannot normalize code style in the repository {} due to the following exception: {}", repository.getLocalPath(), ex.getMessage());
                }
            }

            log.debug("Create temporary zip file for repository {}", repository.getLocalPath().toString());
            return gitService.zipRepositoryWithParticipation(repository, outputDir.toString(), repositoryExportOptions.isHideStudentNameInZippedFolder());
        }
        catch (GitAPIException | GitException ex) {
            log.error("Failed to create zip for participation id {} with exercise id {} because of the following exception ", participation.getId(),
                    participation.getProgrammingExercise().getId(), ex);
            return null;
        }
    }

    /**
     * delete all files in the directory based on the given programming exercise and target path
     * @param programmingExercise the programming exercise for which repos have been downloaded
     * @param targetPath the path in which the repositories have been downloaded
     */
    public void deleteReposDownloadProjectRootDirectory(ProgrammingExercise programmingExercise, String targetPath) {
        final String projectDirName = programmingExercise.getProjectKey();
        Path projectPath = Path.of(targetPath, projectDirName);
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
     * @param participation  The participation related to the repository
     * @param repo           The repository for which to filter all late submissions
     */
    private void filterLateSubmissions(RepositoryExportOptionsDTO repositoryExportOptions, ProgrammingExerciseStudentParticipation participation, Repository repo) {
        log.debug("Filter late submissions for participation {}", participation.toString());
        final Optional<ZonedDateTime> latestAllowedDate;
        if (repositoryExportOptions.isFilterLateSubmissionsIndividualDueDate()) {
            latestAllowedDate = exerciseDateService.getDueDate(participation);
        }
        else {
            latestAllowedDate = Optional.of(repositoryExportOptions.getFilterLateSubmissionsDate());
        }

        if (latestAllowedDate.isPresent()) {
            Optional<Submission> lastValidSubmission = participation.getSubmissions().stream()
                    .filter(s -> s.getSubmissionDate() != null && s.getSubmissionDate().isBefore(latestAllowedDate.get())).max(Comparator.naturalOrder());
            gitService.filterLateSubmissions(repo, lastValidSubmission, latestAllowedDate.get());
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
            gitService.stageAllChanges(repository);
            gitService.commit(repository, "Add participant identifier (student login or team short name) to project name");
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
            File pomFile = new File(pomFilePath);
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
            xformer.transform(new DOMSource(doc), new StreamResult(new File(pomFile.getPath())));

        }
        catch (SAXException | IOException | ParserConfigurationException | TransformerException | XPathException ex) {
            log.error("Cannot rename pom.xml file in {}", repo.getLocalPath(), ex);
        }
    }

    private void addParticipantIdentifierToEclipseProjectName(Repository repo, String participantIdentifier, String eclipseProjectFilePath) {
        try {
            File eclipseProjectFile = new File(eclipseProjectFilePath);
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
            xformer.transform(new DOMSource(doc), new StreamResult(new File(eclipseProjectFile.getPath())));

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
        List<String> allRepoFiles = Collections.emptyList();
        try (Stream<Path> walk = Files.walk(path)) {
            allRepoFiles = walk.filter(Files::isRegularFile).map(Path::toString).filter(s -> !s.contains(".git")).toList();
        }
        catch (IOException | SecurityException e) {
            log.error("Cannot list all files in path {}: {}", path, e.getMessage());
        }
        return allRepoFiles;
    }
}
