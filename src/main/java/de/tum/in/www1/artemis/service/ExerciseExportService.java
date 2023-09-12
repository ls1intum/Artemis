package de.tum.in.www1.artemis.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.Exercise;

@Service
public abstract class ExerciseExportService {

    public static final String EXPORTED_EXERCISE_DETAILS_FILE_PREFIX = "Exercise-Details";

    public static final String EXPORTED_EXERCISE_PROBLEM_STATEMENT_FILE_PREFIX = "Problem-Statement";

    private static final String EMBEDDED_FILE_REGEX = "\\[.*] *\\(/api/files/markdown/.*\\)";

    private static final String API_MARKDOWN_FILE_PATH = "/api/files/markdown/";

    private final Logger log = LoggerFactory.getLogger(ExerciseExportService.class);

    private final FileService fileService;

    private final ObjectMapper objectMapper;

    protected ExerciseExportService(FileService fileService, MappingJackson2HttpMessageConverter springMvcJacksonConverter) {
        this.fileService = fileService;
        this.objectMapper = springMvcJacksonConverter.getObjectMapper();
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
        var problemStatementFileExtension = ".md";
        String problemStatementFileName = EXPORTED_EXERCISE_PROBLEM_STATEMENT_FILE_PREFIX + "-" + exercise.getTitle() + problemStatementFileExtension;
        String cleanProblemStatementFileName = FileService.sanitizeFilename(problemStatementFileName);
        var problemStatementExportPath = exportDir.resolve(cleanProblemStatementFileName);
        if (exercise.getProblemStatement() != null) {
            Files.writeString(problemStatementExportPath, exercise.getProblemStatement());
            copyEmbeddedFiles(exercise, exportDir, pathsToBeZipped, exportErrors);
        }
        else {
            exportErrors.add("Could not export problem statement for exercise " + exercise.getId() + " because it is null.");
            log.warn("Could not export problem statement for exercise {} because it is null.", exercise.getId());
        }
        exportExerciseDetails(exercise, exportDir, pathsToBeZipped);

    }

    private void exportExerciseDetails(Exercise exercise, Path exportDir, List<Path> pathsToBeZipped) {
        var exerciseDetailsFileExtension = ".json";
        String exerciseDetailsFileName = EXPORTED_EXERCISE_DETAILS_FILE_PREFIX + "-" + exercise.getTitle() + exerciseDetailsFileExtension;
        String cleanExerciseDetailsFileName = FileService.sanitizeFilename(exerciseDetailsFileName);
        var exerciseDetailsExportPath = exportDir.resolve(cleanExerciseDetailsFileName);
        // do not include duplicate information
        exercise.getCourseViaExerciseGroupOrCourseMember().setExercises(null);
        exercise.getCourseViaExerciseGroupOrCourseMember().setExams(null);
        pathsToBeZipped.add(fileService.writeObjectToJsonFile(exercise, this.objectMapper, exerciseDetailsExportPath));
    }

    /**
     * In case the problem statement contains embedded files, they need to be part of the zip, so they can be imported again.
     *
     * @param exercise        the programming exercise that is exported
     * @param outputDir       the directory where the content of the export is stored
     * @param pathsToBeZipped the paths that should be included in the zip file
     */

    private void copyEmbeddedFiles(Exercise exercise, Path outputDir, List<Path> pathsToBeZipped, List<String> exportErrors) {
        Set<String> embeddedFiles = new HashSet<>();

        Matcher matcher = Pattern.compile(EMBEDDED_FILE_REGEX).matcher(exercise.getProblemStatement());
        while (matcher.find()) {
            embeddedFiles.add(matcher.group());
        }
        log.debug("Found embedded files:{} ", embeddedFiles);
        Path embeddedFilesDir = outputDir.resolve("files");
        if (!embeddedFiles.isEmpty()) {
            if (!Files.exists(embeddedFilesDir)) {
                try {
                    Files.createDirectory(embeddedFilesDir);
                }
                catch (IOException e) {
                    exportErrors.add("Could not create directory for embedded files: " + e.getMessage());
                    log.warn("Could not create directory for embedded files. Won't include embedded files: " + e.getMessage());
                    return;
                }
            }
            pathsToBeZipped.add(embeddedFilesDir);
        }
        for (String embeddedFile : embeddedFiles) {
            // avoid matching other closing ] or () in the squared brackets by getting the index of the last ]
            String lastPartOfMatchedString = embeddedFile.substring(embeddedFile.lastIndexOf("]") + 1);
            String filePath = lastPartOfMatchedString.substring(lastPartOfMatchedString.indexOf("(") + 1, lastPartOfMatchedString.indexOf(")"));
            String fileName = filePath.replace(API_MARKDOWN_FILE_PATH, "");
            Path imageFilePath = Path.of(FilePathService.getMarkdownFilePath(), fileName);
            Path imageExportPath = embeddedFilesDir.resolve(fileName);
            // we need this check as it might be that the matched string is different and not filtered out above but the file is already copied
            if (!Files.exists(imageExportPath)) {
                try {
                    Files.copy(imageFilePath, imageExportPath);
                }
                catch (IOException e) {
                    exportErrors.add("Failed to copy embedded files: " + e.getMessage());
                    log.warn("Could not copy embedded file {} for exercise with id {}", fileName, exercise.getId());
                }
            }
        }
    }

}
