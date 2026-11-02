package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.admin.service.export.DataExportQuizExerciseCreationService;
import de.tum.cit.aet.artemis.core.service.ArchivalReportEntry;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.ZipFileService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseDetailsDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;

/**
 * Service responsible for exporting quiz exercises with their submissions.
 */

@Profile(PROFILE_CORE)
@Lazy
@Service
public class QuizExerciseWithSubmissionsExportService {

    private final QuizExerciseRepository quizExerciseRepository;

    private final JsonMapper objectMapper;

    private final DataExportQuizExerciseCreationService dataExportQuizExerciseCreationService;

    private final FileService fileService;

    private final ZipFileService zipFileService;

    public QuizExerciseWithSubmissionsExportService(QuizExerciseRepository quizExerciseRepository, JsonMapper objectMapper,
            DataExportQuizExerciseCreationService dataExportQuizExerciseCreationService, FileService fileService, ZipFileService zipFileService) {
        this.quizExerciseRepository = quizExerciseRepository;
        this.objectMapper = objectMapper;
        this.dataExportQuizExerciseCreationService = dataExportQuizExerciseCreationService;
        this.fileService = fileService;
        this.zipFileService = zipFileService;
    }

    /**
     * Exports the given quiz exercise as JSON file with all its submissions and stores it in the given directory.
     *
     * @param quizExercise      the quiz exercise to export
     * @param exerciseExportDir the directory where the quiz exercise should be exported to
     * @param exportErrors      a list of errors that occurred during the export
     * @param reportEntries     a list of report entries that occurred during the export
     * @return the path to the directory where the quiz exercise was exported to
     */
    public Path exportExerciseWithSubmissions(QuizExercise quizExercise, Path exerciseExportDir, List<String> exportErrors, List<ArchivalReportEntry> reportEntries) {
        quizExercise = quizExerciseRepository.findByIdWithQuestionsAndCompetenciesAndBatchesAndGradingCriteriaElseThrow(quizExercise.getId());
        exportExerciseDefinition(quizExercise, exerciseExportDir, exportErrors);
        dataExportQuizExerciseCreationService.exportStudentSubmissionsForArchival(quizExercise, exerciseExportDir, exportErrors, reportEntries);
        return exerciseExportDir;
    }

    /**
     * Exports the quiz exercise definition for download by an instructor: the exercise details and the drag and drop
     * images, in the same layout as in a course archive, but without any submissions.
     *
     * @param quizExercise the quiz exercise to export, with its questions, categories and grading criteria loaded
     * @return the path to the zip file, which is deleted again after a few minutes
     * @throws IOException if the export is incomplete, for example because an image is missing, or the zip file could not be created
     */
    public Path exportQuizExerciseForDownload(QuizExercise quizExercise) throws IOException {
        var timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-Hmss"));
        String zipFileName = quizExercise.getCourseViaExerciseGroupOrCourseMemberElseThrow().getShortName() + "-" + quizExercise.getTitle() + "-" + quizExercise.getId() + "-"
                + timestamp + ".zip";
        Path exportDir = fileService.createTemporaryDirectory(FilePathConverter.getTempFilePath(), "exercise-export-", 5);
        List<String> exportErrors = new ArrayList<>();
        List<Path> pathsToBeZipped = exportExerciseDefinition(quizExercise, exportDir, exportErrors);
        // A missing image or details file would make the import fail, so the instructor gets an error instead of the file.
        if (!exportErrors.isEmpty()) {
            throw new IOException(String.join(" ", exportErrors));
        }
        Path zipFilePath = exportDir.resolve(FileUtil.sanitizeFilename(zipFileName));
        zipFileService.createTemporaryZipFile(zipFilePath, pathsToBeZipped, 5);
        return zipFilePath;
    }

    /**
     * Writes the exercise details as JSON file and copies the images of the drag and drop questions into the given directory.
     *
     * @param quizExercise      the quiz exercise to export, with its questions loaded
     * @param exerciseExportDir the directory where the quiz exercise should be exported to
     * @param exportErrors      a list of errors that occurred during the export
     * @return the paths of the written details file and image directories
     */
    private List<Path> exportExerciseDefinition(QuizExercise quizExercise, Path exerciseExportDir, List<String> exportErrors) {
        List<Path> exportedPaths = new ArrayList<>();
        // do not store unnecessary information in the JSON file
        quizExercise.setCourse(null);
        quizExercise.setExerciseGroup(null);
        try {
            // the same record the quiz endpoints return, so the file keeps the questions with their solutions but no entity graph
            exportedPaths.add(FileUtil.writeObjectToJsonFile(QuizExerciseDetailsDTO.forExport(quizExercise), objectMapper,
                    exerciseExportDir.resolve("Exercise-Details-" + quizExercise.getSanitizedExerciseTitle() + ".json")));
        }
        // Jackson 3 exceptions are unchecked and no longer extend IOException, so a serialization failure would
        // otherwise unwind past here and drop the whole exercise from the archive instead of adding one line
        // to exportErrors and carrying on.
        catch (JacksonException e) {
            exportErrors.add("Failed to export quiz exercise details " + quizExercise.getTitle() + " with id " + quizExercise.getId() + " due to a JSON processing error.");
        }
        for (var quizQuestion : quizExercise.getQuizQuestions()) {
            if (quizQuestion instanceof DragAndDropQuestion dragAndDropQuestion) {
                List<Path> imagesToExport = new ArrayList<>();
                String backgroundFilePath = dragAndDropQuestion.getBackgroundFilePath();
                if (backgroundFilePath != null) {
                    imagesToExport.add(new FileSystemLocation.DragAndDropBackground(backgroundFilePath).path());
                }
                for (var dragItem : dragAndDropQuestion.getDragItems()) {
                    String pictureFilePath = dragItem.getPictureFilePath();
                    if (pictureFilePath != null) {
                        imagesToExport.add(new FileSystemLocation.DragItem(pictureFilePath).path());
                    }
                }
                if (!imagesToExport.isEmpty()) {
                    var imagesDir = exerciseExportDir.resolve("images-for-drag-and-drop-question-" + dragAndDropQuestion.getId());
                    FileUtil.createDirectory(imagesDir);
                    exportedPaths.add(imagesDir);
                    imagesToExport.forEach(path -> {
                        try {
                            FileUtils.copyFile(path.toFile(), imagesDir.resolve(path.getFileName()).toFile());
                        }
                        catch (IOException e) {
                            exportErrors.add("Failed to export image file with file path " + path + " for drag and drop question with id " + dragAndDropQuestion.getId());
                        }
                    });
                }
            }
        }
        return exportedPaths;
    }

}
