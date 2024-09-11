package de.tum.cit.aet.artemis.service.export;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.service.FilePathService;
import de.tum.cit.aet.artemis.service.FileService;
import de.tum.cit.aet.artemis.service.archival.ArchivalReportEntry;
import de.tum.cit.aet.artemis.web.rest.dto.SubmissionExportOptionsDTO;

/**
 * Service for exporting Exercises with the student submissions.
 */
// We cannot remove the abstract as this breaks the Spring Dependency Injection because then Spring doesn't know which bean to inject
@Profile(PROFILE_CORE)
@Service
public abstract class ExerciseWithSubmissionsExportService {

    public static final String EXPORTED_EXERCISE_DETAILS_FILE_PREFIX = "Exercise-Details";

    public static final String EXPORTED_EXERCISE_PROBLEM_STATEMENT_FILE_PREFIX = "Problem-Statement";

    private static final String EMBEDDED_FILE_MARKDOWN_SYNTAX_REGEX = "\\[.*] *\\(/api/files/markdown/.*\\)";

    private static final String EMBEDDED_FILE_HTML_SYNTAX_REGEX = "<img src=\"/api/files/markdown/.*\".*>";

    private static final String API_MARKDOWN_FILE_PATH = "/api/files/markdown/";

    private static final Logger log = LoggerFactory.getLogger(ExerciseWithSubmissionsExportService.class);

    private final FileService fileService;

    private final ObjectMapper objectMapper;

    private final SubmissionExportService submissionExportService;

    ExerciseWithSubmissionsExportService(FileService fileService, MappingJackson2HttpMessageConverter springMvcJacksonConverter, SubmissionExportService submissionExportService) {
        this.fileService = fileService;
        this.objectMapper = springMvcJacksonConverter.getObjectMapper();
        this.submissionExportService = submissionExportService;
    }

    /**
     * Exports the problem statement as markdown file and copies all embedded files to the export directory.
     *
     * @param exercise        the exercise that is exported
     * @param exportErrors    a list of errors that occurred during the export
     * @param exportDir       the directory where the content of the export is stored
     * @param pathsToBeZipped the paths that should be included in the zip file
     */
    void exportProblemStatementAndEmbeddedFilesAndExerciseDetails(Exercise exercise, List<String> exportErrors, Path exportDir, List<Path> pathsToBeZipped) throws IOException {
        exportProblemStatementWithEmbeddedFiles(exercise, exportErrors, exportDir, pathsToBeZipped);
        exportExerciseDetails(exercise, exportDir, pathsToBeZipped);
    }

    private void exportProblemStatementWithEmbeddedFiles(Exercise exercise, List<String> exportErrors, Path exportDir, List<Path> pathsToBeZipped) throws IOException {
        var problemStatementFileExtension = ".md";
        String problemStatementFileName = EXPORTED_EXERCISE_PROBLEM_STATEMENT_FILE_PREFIX + "-" + exercise.getSanitizedExerciseTitle() + problemStatementFileExtension;
        String cleanProblemStatementFileName = FileService.sanitizeFilename(problemStatementFileName);
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
     * @param exercise        the programming exercise that is exported
     * @param outputDir       the directory where the content of the export is stored
     * @param pathsToBeZipped the paths that should be included in the zip file
     */
    private void copyEmbeddedFiles(Exercise exercise, Path outputDir, List<Path> pathsToBeZipped, List<String> exportErrors) {
        Set<String> embeddedFilesWithMarkdownSyntax = new HashSet<>();
        Set<String> embeddedFilesWithHtmlSyntax = new HashSet<>();

        Matcher matcherForMarkdownSyntax = Pattern.compile(EMBEDDED_FILE_MARKDOWN_SYNTAX_REGEX).matcher(exercise.getProblemStatement());
        Matcher matcherForHtmlSyntax = Pattern.compile(EMBEDDED_FILE_HTML_SYNTAX_REGEX).matcher(exercise.getProblemStatement());
        checkForMatchesInProblemStatementAndCreateDirectoryForFiles(outputDir, pathsToBeZipped, exportErrors, embeddedFilesWithMarkdownSyntax, matcherForMarkdownSyntax);
        Path embeddedFilesDir = checkForMatchesInProblemStatementAndCreateDirectoryForFiles(outputDir, pathsToBeZipped, exportErrors, embeddedFilesWithHtmlSyntax,
                matcherForHtmlSyntax);
        // if the returned path is null the directory could not be created
        if (embeddedFilesDir == null) {
            return;
        }
        copyFilesEmbeddedWithMarkdownSyntax(exercise, exportErrors, embeddedFilesWithMarkdownSyntax, embeddedFilesDir);
        copyFilesEmbeddedWithHtmlSyntax(exercise, exportErrors, embeddedFilesWithHtmlSyntax, embeddedFilesDir);

    }

    /**
     * Copies the files that are embedded with Markdown syntax to the embedded files' directory.
     *
     * @param exercise                        the programming exercise that is exported
     * @param exportErrors                    List of failures that occurred during the export
     * @param embeddedFilesWithMarkdownSyntax the files that are embedded with Markdown syntax
     * @param embeddedFilesDir                the directory where the embedded files are stored
     */
    private void copyFilesEmbeddedWithMarkdownSyntax(Exercise exercise, List<String> exportErrors, Set<String> embeddedFilesWithMarkdownSyntax, Path embeddedFilesDir) {
        for (String embeddedFile : embeddedFilesWithMarkdownSyntax) {
            // avoid matching other closing ] or () in the squared brackets by getting the index of the last ]
            String lastPartOfMatchedString = embeddedFile.substring(embeddedFile.lastIndexOf("]") + 1);
            String filePath = lastPartOfMatchedString.substring(lastPartOfMatchedString.indexOf("(") + 1, lastPartOfMatchedString.indexOf(")"));
            constructFilenameAndCopyFile(exercise, exportErrors, embeddedFilesDir, filePath);
        }
    }

    /**
     * Copies the files that are embedded with html syntax to the embedded files' directory.
     *
     * @param exercise                    the programming exercise that is exported
     * @param exportErrors                List of failures that occurred during the export
     * @param embeddedFilesWithHtmlSyntax the files that are embedded with html syntax
     * @param embeddedFilesDir            the directory where the embedded files are stored
     */
    private void copyFilesEmbeddedWithHtmlSyntax(Exercise exercise, List<String> exportErrors, Set<String> embeddedFilesWithHtmlSyntax, Path embeddedFilesDir) {
        for (String embeddedFile : embeddedFilesWithHtmlSyntax) {
            int indexOfFirstQuotationMark = embeddedFile.indexOf('"');
            String filePath = embeddedFile.substring(embeddedFile.indexOf("src=") + 5, embeddedFile.indexOf('"', indexOfFirstQuotationMark + 1));
            constructFilenameAndCopyFile(exercise, exportErrors, embeddedFilesDir, filePath);
        }
    }

    /**
     * Extracts the filename from the matched string and copies the file to the embedded files' directory.
     *
     * @param exercise         the programming exercise that is exported
     * @param exportErrors     List of failures that occurred during the export
     * @param embeddedFilesDir the directory where the embedded files are stored
     * @param filePath         the path of the file that should be copied
     */
    private void constructFilenameAndCopyFile(Exercise exercise, List<String> exportErrors, Path embeddedFilesDir, String filePath) {
        String fileName = filePath.replace(API_MARKDOWN_FILE_PATH, "");
        Path imageFilePath = FilePathService.getMarkdownFilePath().resolve(fileName);
        Path imageExportPath = embeddedFilesDir.resolve(fileName);
        // we need this check as it might be that the matched string is different and not filtered out above but the file is already copied
        if (!Files.exists(imageExportPath)) {
            try {
                FileUtils.copyFile(imageFilePath.toFile(), imageExportPath.toFile());
            }
            catch (IOException e) {
                exportErrors.add("Failed to copy embedded files: " + e.getMessage());
                log.warn("Could not copy embedded file {} for exercise with id {}", fileName, exercise.getId());
            }
        }
    }

    /**
     * Checks for matches in the problem statement and creates a directory for the embedded files.
     *
     * @param outputDir       the directory where the content of the export is stored
     * @param pathsToBeZipped the paths that should be included in the zip file
     * @param exportErrors    List of failures that occurred during the export
     * @param embeddedFiles   the files that are embedded in the problem statement
     * @param matcher         the matcher that is used to find the embedded files
     * @return the path to the embedded files directory or null if the directory could not be created
     */
    private Path checkForMatchesInProblemStatementAndCreateDirectoryForFiles(Path outputDir, List<Path> pathsToBeZipped, List<String> exportErrors, Set<String> embeddedFiles,
            Matcher matcher) {
        while (matcher.find()) {
            embeddedFiles.add(matcher.group());
        }
        log.debug("Found embedded files: {} ", embeddedFiles);
        Path embeddedFilesDir = outputDir.resolve("files");
        if (!embeddedFiles.isEmpty()) {
            if (!Files.exists(embeddedFilesDir)) {
                try {
                    Files.createDirectory(embeddedFilesDir);
                }
                catch (IOException e) {
                    exportErrors.add("Could not create directory for embedded files: " + e.getMessage());
                    log.warn("Could not create directory for embedded files. Won't include embedded files.", e);
                    return null;
                }
            }
            pathsToBeZipped.add(embeddedFilesDir);
        }
        return embeddedFilesDir;
    }

    /**
     * Exports the exercise details as json file. The exercise details are just the exercise object.
     *
     * @param exercise        the exercise that is exported
     * @param exportDir       the directory where the content of the export is stored
     * @param pathsToBeZipped the paths that should be included in the zip file
     */
    private void exportExerciseDetails(Exercise exercise, Path exportDir, List<Path> pathsToBeZipped) throws IOException {
        var exerciseDetailsFileExtension = ".json";
        String exerciseDetailsFileName = EXPORTED_EXERCISE_DETAILS_FILE_PREFIX + "-" + exercise.getTitle() + exerciseDetailsFileExtension;
        String cleanExerciseDetailsFileName = FileService.sanitizeFilename(exerciseDetailsFileName);
        var exerciseDetailsExportPath = exportDir.resolve(cleanExerciseDetailsFileName);
        // do not include duplicate information
        exercise.getCourseViaExerciseGroupOrCourseMember().setExercises(null);
        exercise.getCourseViaExerciseGroupOrCourseMember().setExams(null);
        // do not include related entities ids
        Optional.ofNullable(exercise.getPlagiarismDetectionConfig()).ifPresent(it -> it.setId(null));
        Optional.ofNullable(exercise.getTeamAssignmentConfig()).ifPresent(it -> it.setId(null));
        pathsToBeZipped.add(fileService.writeObjectToJsonFile(exercise, this.objectMapper, exerciseDetailsExportPath));
    }

    Path exportExerciseWithSubmissions(Exercise exercise, SubmissionExportOptionsDTO optionsDTO, Path exportDir, List<String> exportErrors,
            List<ArchivalReportEntry> reportEntries) {
        List<Path> pathsToBeZipped = new ArrayList<>();
        try {
            exportProblemStatementAndEmbeddedFilesAndExerciseDetails(exercise, exportErrors, exportDir, pathsToBeZipped);
        }
        catch (IOException e) {
            exportErrors.add("Failed to export problem statement and embedded files and exercise details for exercise " + exercise.getId() + ": " + e.getMessage());

        }
        submissionExportService.exportStudentSubmissions(exercise.getId(), optionsDTO, false, exportDir, exportErrors, reportEntries);
        return exportDir;
    }

}
