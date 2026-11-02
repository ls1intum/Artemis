package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.ARTEMIS_FILE_PATH_PREFIX;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.service.ArchivalReportEntry;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.ZipFileService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionExportOptionsDTO;
import de.tum.cit.aet.artemis.exercise.repository.PlagiarismDetectionConfigRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamAssignmentConfigRepository;

/**
 * Service for exporting Exercises with the student submissions.
 */
// We cannot remove the abstract as this breaks the Spring Dependency Injection because then Spring doesn't know which bean to inject
@Profile(PROFILE_CORE)
@Lazy
@Service
public abstract class ExerciseWithSubmissionsExportService {

    public static final String EXPORTED_EXERCISE_DETAILS_FILE_PREFIX = "Exercise-Details";

    public static final String EXPORTED_EXERCISE_PROBLEM_STATEMENT_FILE_PREFIX = "Problem-Statement";

    // Dependency to ARTEMIS_FILE_PATH_PREFIX is OK because parsing problem statements is business logic
    private static final String API_MARKDOWN_FILE_PATH = "%smarkdown/".formatted(ARTEMIS_FILE_PATH_PREFIX);

    /**
     * A link to an uploaded markdown file, in Markdown or HTML syntax, with the file name as group 1. The name ends where the link ends, so
     * several links on one line are separate matches.
     */
    private static final Pattern EMBEDDED_FILE = Pattern.compile(Pattern.quote(API_MARKDOWN_FILE_PATH) + "([^\\s()\"'<>]+)");

    private static final Logger log = LoggerFactory.getLogger(ExerciseWithSubmissionsExportService.class);

    private final JsonMapper objectMapper;

    private final SubmissionExportService submissionExportService;

    private final TeamAssignmentConfigRepository teamAssignmentConfigRepository;

    private final PlagiarismDetectionConfigRepository plagiarismDetectionConfigRepository;

    protected final FileService fileService;

    protected final ZipFileService zipFileService;

    protected ExerciseWithSubmissionsExportService(JsonMapper objectMapper, SubmissionExportService submissionExportService,
            TeamAssignmentConfigRepository teamAssignmentConfigRepository, PlagiarismDetectionConfigRepository plagiarismDetectionConfigRepository, FileService fileService,
            ZipFileService zipFileService) {
        this.objectMapper = objectMapper;
        this.submissionExportService = submissionExportService;
        this.teamAssignmentConfigRepository = teamAssignmentConfigRepository;
        this.plagiarismDetectionConfigRepository = plagiarismDetectionConfigRepository;
        this.fileService = fileService;
        this.zipFileService = zipFileService;
    }

    /**
     * Exports the exercise definition for download by an instructor: the problem statement with its embedded files and
     * the exercise details, in the same layout as in a course archive, but without any submissions.
     *
     * @param exercise the exercise to export, with the associations its details should contain already loaded
     * @return the path to the zip file, which is deleted again after a few minutes
     * @throws IOException if the export is incomplete, for example because an embedded file is missing, or the zip file could not be created
     */
    public Path exportExerciseForDownload(Exercise exercise) throws IOException {
        // createTemporaryDirectory never returns null, which static analysis cannot infer through its deletion scheduling
        Path exportDir = Objects.requireNonNull(fileService.createTemporaryDirectory(FilePathConverter.getTempFilePath(), "exercise-export-", 5));
        List<Path> pathsToBeZipped = new ArrayList<>();
        List<String> exportErrors = new ArrayList<>();
        exportProblemStatementAndEmbeddedFilesAndExerciseDetails(exercise, exportErrors, exportDir, pathsToBeZipped);
        // An exercise without a problem statement has nothing to export there, which the archive reports but is complete for a download.
        // Any other error leaves out a part the import needs, so the instructor gets an error instead of a file that does not work.
        if (exercise.getProblemStatement() != null && !exportErrors.isEmpty()) {
            throw new IOException(String.join(" ", exportErrors));
        }
        var timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-Hmss"));
        String zipFileName = exercise.getCourseViaExerciseGroupOrCourseMemberElseThrow().getShortName() + "-" + exercise.getTitle() + "-" + exercise.getId() + "-" + timestamp
                + ".zip";
        Path zipFilePath = exportDir.resolve(FileUtil.sanitizeFilename(zipFileName));
        zipFileService.createTemporaryZipFile(zipFilePath, pathsToBeZipped, 5);
        return zipFilePath;
    }

    /**
     * Exports the problem statement as markdown file and copies all embedded files to the export directory.
     *
     * @param exercise        the exercise that is exported
     * @param exportErrors    a list of errors that occurred during the export
     * @param exportDir       the directory where the content of the export is stored
     * @param pathsToBeZipped the paths that should be included in the zip file
     */
    protected void exportProblemStatementAndEmbeddedFilesAndExerciseDetails(Exercise exercise, List<String> exportErrors, Path exportDir, List<Path> pathsToBeZipped)
            throws IOException {
        exportProblemStatementWithEmbeddedFiles(exercise, exportErrors, exportDir, pathsToBeZipped);
        exportExerciseDetails(exercise, exportDir, pathsToBeZipped);
    }

    private void exportProblemStatementWithEmbeddedFiles(Exercise exercise, List<String> exportErrors, Path exportDir, List<Path> pathsToBeZipped) throws IOException {
        var problemStatementFileExtension = ".md";
        String problemStatementFileName = EXPORTED_EXERCISE_PROBLEM_STATEMENT_FILE_PREFIX + "-" + exercise.getSanitizedExerciseTitle() + problemStatementFileExtension;
        String cleanProblemStatementFileName = FileUtil.sanitizeFilename(problemStatementFileName);
        var problemStatementExportPath = exportDir.resolve(cleanProblemStatementFileName);
        if (exercise.getProblemStatement() != null) {
            FileUtils.writeStringToFile(problemStatementExportPath.toFile(), exercise.getProblemStatement(), StandardCharsets.UTF_8);
            copyEmbeddedFiles(exercise, exportDir, pathsToBeZipped, exportErrors);
            pathsToBeZipped.add(problemStatementExportPath);
        }
        else {
            exportErrors.add("Could not export problem statement for exercise " + exercise.getId() + " because it is null.");
            log.warn("Could not export problem statement for exercise {} because it is null.", exercise.getId());
        }
    }

    /**
     * In case the problem statement contains embedded files, they need to be part of the zip, so they can be imported again.
     *
     * @param exercise        the exercise that is exported
     * @param outputDir       the directory where the content of the export is stored
     * @param pathsToBeZipped the paths that should be included in the zip file
     * @param exportErrors    List of failures that occurred during the export
     */
    private void copyEmbeddedFiles(Exercise exercise, Path outputDir, List<Path> pathsToBeZipped, List<String> exportErrors) {
        Set<String> fileNames = new LinkedHashSet<>();
        Matcher matcher = EMBEDDED_FILE.matcher(exercise.getProblemStatement());
        while (matcher.find()) {
            String fileName = matcher.group(1);
            // Uploaded markdown files lie directly in the markdown directory. A link with a path names none of them, and an editor must not
            // be able to make the export copy a file from outside that directory.
            if (!fileName.contains("/") && !fileName.contains("\\") && !fileName.equals("..")) {
                fileNames.add(fileName);
            }
        }
        log.debug("Found embedded files: {} ", fileNames);
        if (fileNames.isEmpty()) {
            return;
        }
        Path embeddedFilesDir = outputDir.resolve("files");
        try {
            Files.createDirectories(embeddedFilesDir);
        }
        catch (IOException e) {
            exportErrors.add("Could not create directory for embedded files: " + e.getMessage());
            log.warn("Could not create directory for embedded files. Won't include embedded files.", e);
            return;
        }
        pathsToBeZipped.add(embeddedFilesDir);
        for (String fileName : fileNames) {
            try {
                FileUtils.copyFile(FilePathConverter.getMarkdownFilePath().resolve(fileName).toFile(), embeddedFilesDir.resolve(fileName).toFile());
            }
            catch (IOException e) {
                exportErrors.add("Failed to copy embedded files: " + e.getMessage());
                log.warn("Could not copy embedded file {} for exercise with id {}", fileName, exercise.getId());
            }
        }
    }

    /**
     * Projects the exercise into the record written to the exercise details file. Each exercise type returns the same
     * record its REST endpoints return, so the exported JSON keeps the field names the import and the clients read.
     *
     * @param exercise the exercise that is exported
     * @return the entity-free exercise details
     */
    protected abstract Record exerciseDetailsForExport(Exercise exercise);

    /**
     * Exports the exercise details as json file.
     *
     * @param exercise        the exercise that is exported
     * @param exportDir       the directory where the content of the export is stored
     * @param pathsToBeZipped the paths that should be included in the zip file
     */
    private void exportExerciseDetails(Exercise exercise, Path exportDir, List<Path> pathsToBeZipped) {
        var exerciseDetailsFileExtension = ".json";
        String exerciseDetailsFileName = EXPORTED_EXERCISE_DETAILS_FILE_PREFIX + "-" + exercise.getTitle() + exerciseDetailsFileExtension;
        String cleanExerciseDetailsFileName = FileUtil.sanitizeFilename(exerciseDetailsFileName);
        var exerciseDetailsExportPath = exportDir.resolve(cleanExerciseDetailsFileName);
        // The exported details report the team assignment and the plagiarism detection configuration, which an exercise does
        // not carry by itself.
        teamAssignmentConfigRepository.attachTo(exercise);
        plagiarismDetectionConfigRepository.attachTo(exercise);
        pathsToBeZipped.add(FileUtil.writeObjectToJsonFile(exerciseDetailsForExport(exercise), this.objectMapper, exerciseDetailsExportPath));
    }

    protected Path exportExerciseWithSubmissions(Exercise exercise, SubmissionExportOptionsDTO optionsDTO, Path exportDir, List<String> exportErrors,
            List<ArchivalReportEntry> reportEntries) {
        List<Path> pathsToBeZipped = new ArrayList<>();
        try {
            exportProblemStatementAndEmbeddedFilesAndExerciseDetails(exercise, exportErrors, exportDir, pathsToBeZipped);
        }
        // Jackson 3 exceptions are unchecked and no longer extend IOException, so a serialization failure would
        // otherwise unwind past here and drop the whole exercise from the archive instead of adding one line
        // to exportErrors and carrying on.
        catch (IOException | JacksonException e) {
            exportErrors.add("Failed to export problem statement and embedded files and exercise details for exercise " + exercise.getId() + ": " + e.getMessage());

        }
        submissionExportService.exportStudentSubmissions(exercise.getId(), optionsDTO, false, exportDir, exportErrors, reportEntries);
        return exportDir;
    }

}
