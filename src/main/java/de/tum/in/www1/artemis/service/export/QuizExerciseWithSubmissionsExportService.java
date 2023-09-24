package de.tum.in.www1.artemis.service.export;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.quiz.DragAndDropQuestion;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.QuizExerciseRepository;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.archival.ArchivalReportEntry;

@Service
public class QuizExerciseWithSubmissionsExportService {

    private final QuizExerciseRepository quizExerciseRepository;

    private final ObjectMapper objectMapper;

    private final DataExportQuizExerciseCreationService dataExportQuizExerciseCreationService;

    private final FileService fileService;

    private final FilePathService filePathService;

    public QuizExerciseWithSubmissionsExportService(QuizExerciseRepository quizExerciseRepository, MappingJackson2HttpMessageConverter springMvcJacksonConverter,
            DataExportQuizExerciseCreationService dataExportQuizExerciseCreationService, FileService fileService, FilePathService filePathService) {
        this.quizExerciseRepository = quizExerciseRepository;
        this.objectMapper = springMvcJacksonConverter.getObjectMapper();
        this.dataExportQuizExerciseCreationService = dataExportQuizExerciseCreationService;
        this.fileService = fileService;
        this.filePathService = filePathService;
    }

    public Path exportExerciseWithSubmissions(QuizExercise exercise, Path exerciseExportDir, List<String> exportErrors, List<ArchivalReportEntry> reportEntries) {

        var quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsAndCompetenciesElseThrow(exercise.getId());
        try {
            fileService.writeObjectToJsonFile(exercise, objectMapper, exerciseExportDir.resolve("Exercise-Details-" + exercise.getSanitizedExerciseTitle() + ".json"));
        }
        catch (IOException e) {
            exportErrors.add("Failed to export quiz exercise details " + exercise.getTitle() + " with id " + exercise.getId() + " due to a JSON processing error.");
        }
        List<Path> imagesToExport = new ArrayList<>();
        for (var quizQuestion : quizExercise.getQuizQuestions()) {
            if (quizQuestion instanceof DragAndDropQuestion dragAndDropQuestion) {
                if (dragAndDropQuestion.getBackgroundFilePath() != null) {
                    imagesToExport.add(filePathService.actualPathForPublicPath(URI.create(dragAndDropQuestion.getBackgroundFilePath())));
                }
                for (var dragItem : dragAndDropQuestion.getDragItems()) {
                    if (dragItem.getPictureFilePath() != null) {
                        imagesToExport.add(filePathService.actualPathForPublicPath(URI.create(dragItem.getPictureFilePath())));

                    }
                }
                if (!imagesToExport.isEmpty()) {
                    var imagesDir = exerciseExportDir.resolve("images-for-drag-and-drop-question-" + dragAndDropQuestion.getId());
                    fileService.createDirectory(imagesDir);
                    imagesToExport.forEach(path -> {
                        try {
                            Files.copy(path, imagesDir.resolve(path.getFileName()));
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
