package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.service.ArchivalReportEntry;
import de.tum.cit.aet.artemis.core.service.FilePathService;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.export.DataExportQuizExerciseCreationService;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;

/**
 * Service responsible for exporting quiz exercises with their submissions.
 */

@Profile(PROFILE_CORE)
@Service
public class QuizExerciseWithSubmissionsExportService {

    private final QuizExerciseRepository quizExerciseRepository;

    private final ObjectMapper objectMapper;

    private final DataExportQuizExerciseCreationService dataExportQuizExerciseCreationService;

    private final FileService fileService;

    public QuizExerciseWithSubmissionsExportService(QuizExerciseRepository quizExerciseRepository, MappingJackson2HttpMessageConverter springMvcJacksonConverter,
            DataExportQuizExerciseCreationService dataExportQuizExerciseCreationService, FileService fileService) {
        this.quizExerciseRepository = quizExerciseRepository;
        this.objectMapper = springMvcJacksonConverter.getObjectMapper();
        this.dataExportQuizExerciseCreationService = dataExportQuizExerciseCreationService;
        this.fileService = fileService;
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
        quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaElseThrow(quizExercise.getId());
        // do not store unnecessary information in the JSON file
        quizExercise.setCourse(null);
        quizExercise.setExerciseGroup(null);
        try {
            fileService.writeObjectToJsonFile(quizExercise, objectMapper, exerciseExportDir.resolve("Exercise-Details-" + quizExercise.getSanitizedExerciseTitle() + ".json"));
        }
        catch (IOException e) {
            exportErrors.add("Failed to export quiz exercise details " + quizExercise.getTitle() + " with id " + quizExercise.getId() + " due to a JSON processing error.");
        }
        List<Path> imagesToExport = new ArrayList<>();
        for (var quizQuestion : quizExercise.getQuizQuestions()) {
            if (quizQuestion instanceof DragAndDropQuestion dragAndDropQuestion) {
                if (dragAndDropQuestion.getBackgroundFilePath() != null) {
                    imagesToExport.add(FilePathService.actualPathForPublicPath(URI.create(dragAndDropQuestion.getBackgroundFilePath())));
                }
                for (var dragItem : dragAndDropQuestion.getDragItems()) {
                    if (dragItem.getPictureFilePath() != null) {
                        imagesToExport.add(FilePathService.actualPathForPublicPath(URI.create(dragItem.getPictureFilePath())));

                    }
                }
                if (!imagesToExport.isEmpty()) {
                    var imagesDir = exerciseExportDir.resolve("images-for-drag-and-drop-question-" + dragAndDropQuestion.getId());
                    fileService.createDirectory(imagesDir);
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
        dataExportQuizExerciseCreationService.exportStudentSubmissionsForArchival(quizExercise, exerciseExportDir, exportErrors, reportEntries);
        return exerciseExportDir;
    }

}
