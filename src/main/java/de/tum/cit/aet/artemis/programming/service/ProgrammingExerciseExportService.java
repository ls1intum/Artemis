package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.programming.service.jenkins.JenkinsXmlFileUtils.getDocumentBuilderFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jakarta.validation.constraints.NotNull;

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
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.dto.RepositoryExportOptionsDTO;
import de.tum.cit.aet.artemis.core.exception.GitException;
import de.tum.cit.aet.artemis.core.service.ArchivalReportEntry;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.ZipFileService;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseWithSubmissionsExportService;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.BuildPlanRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

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

    private final ZipFileService zipFileService;

    private final BuildPlanRepository buildPlanRepository;

    private final ObjectMapper objectMapper;

    public static final String EXPORTED_EXERCISE_DETAILS_FILE_PREFIX = "Exercise-Details";

    public static final String EXPORTED_EXERCISE_PROBLEM_STATEMENT_FILE_PREFIX = "Problem-Statement";

    public static final String BUILD_PLAN_FILE_NAME = "buildPlan.txt";

    public ProgrammingExerciseExportService(ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseTaskService programmingExerciseTaskService,
            StudentParticipationRepository studentParticipationRepository, FileService fileService, GitService gitService, GitRepositoryExportService gitRepositoryExportService,
            ZipFileService zipFileService, MappingJackson2HttpMessageConverter springMvcJacksonConverter, AuxiliaryRepositoryRepository auxiliaryRepositoryRepository,
            BuildPlanRepository buildPlanRepository) {
        // Programming exercises do not have a submission export service
        super(fileService, springMvcJacksonConverter, null);
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.fileService = fileService;
        this.gitService = gitService;
        this.gitRepositoryExportService = gitRepositoryExportService;
        this.zipFileService = zipFileService;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.buildPlanRepository = buildPlanRepository;
        this.objectMapper = springMvcJacksonConverter.getObjectMapper();
    }

    /**
     * Export programming exercise material for instructors including instructor repositories, problem statement (.md) and exercise detail (.json).
     * <p>
     * Optionally, student repositories can be included as well.
     *
     * @param exercise              the programming exercise
     * @param exportErrors          List of failures that occurred during the export
     * @param exportDir             the directory used to store the zip file
     * @param archivalReportEntries List of all exercises and their statistics
     * @return the path to the zip file
     */
    private Path exportProgrammingExerciseMaterialWithStudentRepositories(ProgrammingExercise exercise, List<String> exportErrors, Optional<Path> exportDir,
            List<ArchivalReportEntry> archivalReportEntries, List<Path> pathsToBeZipped) throws IOException {
        if (exportDir.isEmpty()) {
            // Create export directory for programming exercises
            if (!Files.exists(repoDownloadClonePath)) {
                Files.createDirectories(repoDownloadClonePath);
            }
            exportDir = Optional.of(fileService.getTemporaryUniquePathWithoutPathCreation(repoDownloadClonePath, 5));
        }

        // Add the exported zip folder containing template, solution, and tests repositories. Also export the build plan if one exists.
        // Wrap this in a try catch block to prevent the problem statement and exercise details not being exported if the repositories fail to export
        try {
            var repoExportsPaths = exportProgrammingExerciseRepositories(exercise, repoDownloadClonePath, exportDir.orElseThrow(), exportErrors, archivalReportEntries);
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
            return Optional.of(exportProgrammingExerciseMaterialWithStudentRepositories(exercise, exportErrors, exportDir, archivalReportEntries, new ArrayList<>()));
        }
        catch (IOException e) {
            // this should actually never happen because all operations that throw an IOException are not executed when calling the method with an exportDir
            log.error("Failed to export programming exercise for archival: {}", e.getMessage());
            exportErrors.add("Failed to export programming exercise for archival: " + e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Exports a programming exercise for download purposes. This includes the instructor repositories and combines them into a single zip stream.
     *
     * @param exercise the programming exercise to export
     * @return a resource streaming the zipped instructor repositories
     * @throws IOException if an error occurs while creating the zip
     */
    public Resource exportProgrammingExerciseForDownload(@NotNull ProgrammingExercise exercise) throws IOException {
        anonymizeGradingCriteria(exercise);

        // Inner zip containing the instructor repositories (template, solution, tests, auxiliary)
        Resource instructorRepositoriesZip = createInstructorRepositoriesZip(exercise);
        // Outer zip containing the inner zip + exercise details (json) + problem statement (md)
        return createCombinedZipResource(exercise, instructorRepositoriesZip);
    }

    private void anonymizeGradingCriteria(ProgrammingExercise exercise) {
        if (exercise.getGradingCriteria() != null) {
            for (GradingCriterion gradingCriterion : exercise.getGradingCriteria()) {
                gradingCriterion.setId(null);
            }
        }
    }

    private Resource createInstructorRepositoriesZip(ProgrammingExercise exercise) throws IOException {
        Resource templateResource = gitRepositoryExportService.exportInstructorRepositoryForExerciseInMemory(exercise, RepositoryType.TEMPLATE);
        Resource solutionResource = gitRepositoryExportService.exportInstructorRepositoryForExerciseInMemory(exercise, RepositoryType.SOLUTION);
        Resource testsResource = gitRepositoryExportService.exportInstructorRepositoryForExerciseInMemory(exercise, RepositoryType.TESTS);

        List<InputStreamResource> auxiliaryResources = exercise.getAuxiliaryRepositories().stream().map(auxiliaryRepository -> {
            try {
                return gitRepositoryExportService.exportInstructorAuxiliaryRepositoryForExerciseInMemory(exercise, auxiliaryRepository);
            }
            catch (IOException e) {
                log.error("Failed to export auxiliary repository {}: {}", auxiliaryRepository.getName(), e.getMessage());
                return null;
            }
        }).filter(Objects::nonNull).toList();

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(); ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {

            addResourceToZip(zipOutputStream, templateResource, templateResource.getFilename());
            addResourceToZip(zipOutputStream, solutionResource, solutionResource.getFilename());
            addResourceToZip(zipOutputStream, testsResource, testsResource.getFilename());
            auxiliaryResources.forEach(resource -> {
                try {
                    addResourceToZip(zipOutputStream, resource, resource.getFilename());
                }
                catch (IOException e) {
                    log.error("Failed to add auxiliary repository to zip: {}", e.getMessage());
                }
            });

            zipOutputStream.finish();
            return new ByteArrayResource(byteArrayOutputStream.toByteArray());
        }
    }

    private Resource createCombinedZipResource(ProgrammingExercise exercise, Resource instructorRepositoriesZip) throws IOException {
        String exerciseDetailsFileName = FileUtil.sanitizeFilename(EXPORTED_EXERCISE_DETAILS_FILE_PREFIX + "-" + exercise.getTitle() + ".json");
        Resource exerciseDetailsJson = createExerciseDetailsJson(exercise);

        String problemStatementFileName = FileUtil.sanitizeFilename(EXPORTED_EXERCISE_PROBLEM_STATEMENT_FILE_PREFIX + "-" + exercise.getSanitizedExerciseTitle() + ".md");
        Resource problemStatementMarkdown = createProblemStatementMarkdown(exercise);

        String timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-Hmss"));
        String courseShortName = Optional.ofNullable(exercise.getCourseViaExerciseGroupOrCourseMember().getShortName()).orElse("course");
        String exerciseTitle = Optional.ofNullable(exercise.getTitle()).orElse(exercise.getShortName());

        String instructorRepositoriesFilename = FileUtil.sanitizeFilename(courseShortName + "-" + exerciseTitle + "-" + exercise.getId() + "-" + timestamp + ".zip");

        String exportFilename = FileUtil.sanitizeFilename("Material-" + courseShortName + "-" + exerciseTitle + "-" + exercise.getId() + "-" + timestamp + ".zip");

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(); ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
            addResourceToZip(zipOutputStream, instructorRepositoriesZip, instructorRepositoriesFilename);
            addResourceToZip(zipOutputStream, exerciseDetailsJson, exerciseDetailsFileName);
            addResourceToZip(zipOutputStream, problemStatementMarkdown, problemStatementFileName);
            zipOutputStream.finish();

            return new ByteArrayResource(byteArrayOutputStream.toByteArray(), exportFilename);
        }
    }

    private Resource createExerciseDetailsJson(ProgrammingExercise exercise) throws IOException {
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        course.setExercises(null);
        course.setExams(null);

        return new ByteArrayResource(objectMapper.writeValueAsBytes(exercise));
    }

    private Resource createProblemStatementMarkdown(ProgrammingExercise exercise) {
        if (exercise.getProblemStatement() == null) {
            return null;
        }
        return new ByteArrayResource(exercise.getProblemStatement().getBytes(StandardCharsets.UTF_8));
    }

    private void addResourceToZip(ZipOutputStream zipOutputStream, Resource resource, String entryName) throws IOException {
        ZipEntry zipEntry = new ZipEntry(entryName);
        zipOutputStream.putNextEntry(zipEntry);

        try (InputStream inputStream = resource.getInputStream()) {
            inputStream.transferTo(zipOutputStream);
        }

        zipOutputStream.closeEntry();
    }

    /**
     * Export instructor repositories and optionally students' repositories in a zip file or
     * directory (without zipping).
     * <p>
     * The outputDir is used to store the zip file and temporary files used for zipping so make
     * sure to delete it if it's no longer used.
     *
     * @param exercise     the programming exercise
     * @param workingDir   the directory used to clone the repository
     * @param outputDir    the path to a directory that will be used to store the zipped programming exercise.
     * @param exportErrors List of failures that occurred during the export
     * @param reportData   List of all exercises and their statistics
     * @return a list of paths to one zip file or more directories
     */
    public List<Path> exportProgrammingExerciseRepositories(ProgrammingExercise exercise, Path workingDir, Path outputDir, List<String> exportErrors,
            List<ArchivalReportEntry> reportData) {
        log.info("Exporting programming exercise {} with title {}", exercise.getId(), exercise.getTitle());
        // List to add paths of files that should be contained in the zip folder of exported programming exercise repositories:
        // i.e., student repositories, instructor repositories template, solution and tests

        // Lazy load student participations, sorted by id, and set the export options
        var studentParticipations = studentParticipationRepository.findByExerciseId(exercise.getId()).stream()
                .map(studentParticipation -> (ProgrammingExerciseStudentParticipation) studentParticipation).sorted(Comparator.comparing(DomainObject::getId)).toList();
        var exportOptions = new RepositoryExportOptionsDTO(true, false, false, null, false, false, false, false, false);

        // Export student repositories and add them to list
        var exportedStudentRepositoryFiles = exportStudentRepositories(exercise, studentParticipations, exportOptions, workingDir, outputDir, exportErrors).stream()
                .filter(Objects::nonNull).toList();
        var pathsToBeZipped = new ArrayList<>(exportedStudentRepositoryFiles);

        // Export the template, solution, and tests repositories and add them to list
        pathsToBeZipped.add(exportInstructorRepositoryForExercise(exercise.getId(), RepositoryType.TEMPLATE, workingDir, outputDir, exportErrors).map(File::toPath).orElse(null));
        pathsToBeZipped.add(exportInstructorRepositoryForExercise(exercise.getId(), RepositoryType.SOLUTION, workingDir, outputDir, exportErrors).map(File::toPath).orElse(null));
        pathsToBeZipped.add(exportInstructorRepositoryForExercise(exercise.getId(), RepositoryType.TESTS, workingDir, outputDir, exportErrors).map(File::toPath).orElse(null));

        List<AuxiliaryRepository> auxiliaryRepositories = auxiliaryRepositoryRepository.findByExerciseId(exercise.getId());

        // Export the auxiliary repositories and add them to list
        auxiliaryRepositories.forEach(auxiliaryRepository -> pathsToBeZipped
                .add(exportInstructorAuxiliaryRepositoryForExercise(exercise.getId(), auxiliaryRepository, workingDir, outputDir, exportErrors).map(File::toPath).orElse(null)));

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
                return null;
            }

            // Create the zip folder of the exported programming exercise and return the path to the created folder
            return includedFilePathsNotNull;
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
     * solution, or tests repository
     *
     * @param exerciseId     The id of the programming exercise that has the repository
     * @param repositoryType The type of repository to export
     * @param workingDir     The directory used to clone the repository
     * @param outputDir      The directory used for store the zip file
     * @param exportErrors   List of failures that occurred during the export
     * @return a zipped file
     */
    public Optional<File> exportInstructorRepositoryForExercise(long exerciseId, RepositoryType repositoryType, Path workingDir, Path outputDir, List<String> exportErrors) {
        var exerciseOrEmpty = loadExerciseForRepoExport(exerciseId, exportErrors);
        if (exerciseOrEmpty.isEmpty()) {
            return Optional.empty();
        }
        var exercise = exerciseOrEmpty.get();
        String zippedRepoName = gitRepositoryExportService.getZippedRepoName(exercise, repositoryType.getName());
        var repositoryUri = exercise.getRepositoryURI(repositoryType);
        return exportRepository(repositoryUri, repositoryType.getName(), zippedRepoName, exercise, workingDir, outputDir);
    }

    /**
     * Exports an auxiliary repository for a given programming exercise.
     *
     * @param exerciseId          The id of the programming exercise that has the repository
     * @param auxiliaryRepository the auxiliary repository to export
     * @param workingDir          The directory used to clone the repository
     * @param outputDir           The directory used for storing the zip file
     * @param exportErrors        List of failures that occurred during the export
     * @return the zipped file containing the auxiliary repository
     */
    public Optional<File> exportInstructorAuxiliaryRepositoryForExercise(long exerciseId, AuxiliaryRepository auxiliaryRepository, Path workingDir, Path outputDir,
            List<String> exportErrors) {
        var exerciseOrEmpty = loadExerciseForRepoExport(exerciseId, exportErrors);
        if (exerciseOrEmpty.isEmpty()) {
            return Optional.empty();
        }
        var exercise = exerciseOrEmpty.get();
        String zippedRepoName = gitRepositoryExportService.getZippedRepoName(exercise, auxiliaryRepository.getRepositoryName());
        var repositoryUri = auxiliaryRepository.getVcsRepositoryUri();
        return exportRepository(repositoryUri, auxiliaryRepository.getName(), zippedRepoName, exercise, workingDir, outputDir);
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
     * Exports a given repository and stores it in a zip file.
     *
     * @param repositoryUri  the url of the repository
     * @param zippedRepoName the name of the zip file
     * @param workingDir     the directory used to clone the repository
     * @param outputDir      the directory used for store the zip file
     * @return an optional containing the path to the zip file if the export was successful
     */
    private Optional<File> exportRepository(LocalVCRepositoryUri repositoryUri, String repositoryName, String zippedRepoName, ProgrammingExercise exercise, Path workingDir,
            Path outputDir) {
        try {
            // It's not guaranteed that the repository uri is defined (old courses).
            if (repositoryUri == null) {
                var error = "Failed to export instructor repository " + repositoryName + " because the repository uri is not defined.";
                log.error(error);
                return Optional.empty();
            }

            Path zippedRepo = createZipForRepository(repositoryUri, zippedRepoName, workingDir, outputDir);
            if (zippedRepo != null) {
                return Optional.of(zippedRepo.toFile());
            }
        }
        catch (IOException | GitAPIException ex) {
            var error = "Failed to export instructor repository " + repositoryName + " for programming exercise '" + exercise.getTitle() + "' (id: " + exercise.getId() + ")";
            log.error("{}: {}", error, ex.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Get participations of programming exercises of a requested list of students packed together in one zip file.
     * <p>
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
                .orElseThrow();

        Path outputDir = fileService.getTemporaryUniquePathWithoutPathCreation(repoDownloadClonePath, 10);
        var zippedRepos = exportStudentRepositories(programmingExercise, participations, repositoryExportOptions, outputDir, outputDir, new ArrayList<>());

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
     * Creates directories of the participations of programming exercises of a requested list of students.
     *
     * @param programmingExercise     the programming exercise
     * @param participations          participations that should be exported
     * @param repositoryExportOptions the options that should be used for the export
     * @param workingDir              The directory used to clone the repositories
     * @param outputDir               The directory used for store the directories
     * @param exportErrors            A list of errors that occurred during export (populated by this function)
     * @return List of directory paths
     */
    public List<Path> exportStudentRepositories(ProgrammingExercise programmingExercise, @NotNull List<ProgrammingExerciseStudentParticipation> participations,
            RepositoryExportOptionsDTO repositoryExportOptions, Path workingDir, Path outputDir, List<String> exportErrors) {
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

        List<Path> exportedStudentRepositories = Collections.synchronizedList(new ArrayList<>());

        log.info("export student repositories for programming exercise {} in parallel", programmingExercise.getId());
        try (var threadPool = Executors.newFixedThreadPool(10)) {
            var futures = participations.stream().map(participation -> CompletableFuture.runAsync(() -> {
                try {
                    log.debug("invoke createZipForRepositoryWithParticipation for participation {}", participation.getId());
                    Path dir = getRepositoryWithParticipation(programmingExercise, participation, repositoryExportOptions, workingDir, outputDir, false);
                    if (dir != null) {
                        exportedStudentRepositories.add(dir);
                    }
                }
                catch (Exception exception) {
                    var error = "Failed to export the student repository with participation: " + participation.getId() + " for programming exercise '"
                            + programmingExercise.getTitle() + "' (id: " + programmingExercise.getId() + ") because the repository couldn't be downloaded. ";
                    exportErrors.add(error);
                }
            }, threadPool).toCompletableFuture()).toArray(CompletableFuture[]::new);
            // wait until all operations finish
            CompletableFuture.allOf(futures).thenRun(threadPool::shutdown).join();
        }
        return exportedStudentRepositories;
    }

    /**
     * Creates a zip file with the contents of the git repository. Note that the zip file is deleted in 5 minutes.
     *
     * @param repositoryUri The url of the repository to zip
     * @param zipFilename   The name of the zip file
     * @param outputDir     The directory used to store the zip file
     * @return The path to the zip file.
     * @throws IOException     if the zip file couldn't be created
     * @throws GitAPIException if the repo couldn't get checked out
     */
    private Path createZipForRepository(LocalVCRepositoryUri repositoryUri, String zipFilename, Path workingDir, Path outputDir) throws IOException, GitAPIException, GitException {
        var repositoryDir = fileService.getTemporaryUniquePathWithoutPathCreation(workingDir, 5);
        Path localRepoPath;

        // Checkout the repository
        try (Repository repository = gitService.getOrCheckoutRepositoryWithLocalPath(repositoryUri, repositoryDir, false, false)) {
            gitService.resetToOriginHead(repository);
            localRepoPath = repository.getLocalPath();
        }

        // Zip it and return the path to the file
        return gitRepositoryExportService.zipFiles(localRepoPath, zipFilename, outputDir.toString(), null);
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
     * Checks out the repository for the given participation and return the path to a copy of it.
     *
     * @param programmingExercise     The programming exercise for the participation
     * @param participation           The participation, for which the repository should get zipped
     * @param repositoryExportOptions The options, that should get applied to the zipped repo
     * @param workingDir              The directory used to clone the repository
     * @param outputDir               The directory where the zip file or directory is stored
     * @param zipOutput               If true the method returns a zip file otherwise a directory.
     * @return The checked out repository as a zip file or directory
     * @throws IOException if zip file creation failed
     */
    // TODO: we should check out the repo in memory and not clone it into the file system and additionally do multiple remote operations
    public Path getRepositoryWithParticipation(final ProgrammingExercise programmingExercise, final ProgrammingExerciseStudentParticipation participation,
            final RepositoryExportOptionsDTO repositoryExportOptions, Path workingDir, Path outputDir, boolean zipOutput) throws IOException {
        if (participation.getVcsRepositoryUri() == null) {
            log.warn("Ignore participation {} for export, because its repository URI is null", participation.getId());
            return null;
        }

        if (repositoryExportOptions.excludePracticeSubmissions() && participation.isPracticeMode()) {
            log.debug("Ignoring practice participation {}", participation);
            return null;
        }

        try {
            var tempRepositoryPath = fileService.getTemporaryUniquePathWithoutPathCreation(workingDir, 5);
            // Checkout the repository
            Repository repository = gitService.getOrCheckoutRepository(participation, tempRepositoryPath, false);
            if (repository == null) {
                log.warn("Cannot checkout repository for participation id: {}", participation.getId());
                return null;
            }

            // TODO: this operation is only necessary if the repo was not newly cloned
            gitService.resetToOriginHead(repository);

            if (repositoryExportOptions.filterLateSubmissions()) {
                filterLateSubmissions(repositoryExportOptions, participation, repository);
            }

            if (repositoryExportOptions.addParticipantName()) {
                log.debug("Adding student or team name to participation {}", participation);
                addParticipantIdentifierToProjectName(repository, programmingExercise, participation);
            }

            if (repositoryExportOptions.combineStudentCommits()) {
                log.debug("Combining commits for participation {}", participation);
                gitService.combineAllStudentCommits(repository, programmingExercise, repositoryExportOptions.anonymizeRepository());
            }

            if (repositoryExportOptions.anonymizeRepository()) {
                log.debug("Anonymizing commits for participation {}", participation);
                gitService.anonymizeStudentCommits(repository, programmingExercise);
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
            return gitRepositoryExportService.getRepositoryWithParticipation(repository, outputDir.toString(), repositoryExportOptions.anonymizeRepository(), zipOutput);
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
     * @param participation           The participation related to the repository
     * @param repo                    The repository for which to filter all late submissions
     */
    private void filterLateSubmissions(RepositoryExportOptionsDTO repositoryExportOptions, ProgrammingExerciseStudentParticipation participation, Repository repo) {
        log.debug("Filter late submissions for participation {}", participation.toString());
        final Optional<ZonedDateTime> latestAllowedDate;
        if (repositoryExportOptions.filterLateSubmissionsIndividualDueDate()) {
            latestAllowedDate = ExerciseDateService.getDueDate(participation);
        }
        else {
            latestAllowedDate = Optional.of(repositoryExportOptions.filterLateSubmissionsDate());
        }

        if (latestAllowedDate.isPresent()) {
            Optional<Submission> lastValidSubmission = participation.getSubmissions().stream()
                    .filter(submission -> submission.getSubmissionDate() != null && submission.getSubmissionDate().isBefore(latestAllowedDate.get()))
                    .max(Comparator.naturalOrder());
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
