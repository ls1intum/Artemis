package de.tum.in.www1.artemis.service.export;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.QuizExerciseRepository;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.archival.ArchivalReportEntry;

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

    public Path exportExerciseWithSubmissions(QuizExercise exercise, Path exerciseExportDir, List<String> exportErrors, List<ArchivalReportEntry> reportEntries) {

        var quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsAndCompetenciesElseThrow(exercise.getId());
        try {
            fileService.writeObjectToJsonFile(exercise, objectMapper, exerciseExportDir.resolve("Exercise-Details-" + exercise.getSanitizedExerciseTitle() + ".json"));
        }
        catch (IOException e) {
            exportErrors.add("Failed to export quiz exercise details " + exercise.getTitle() + " with id " + exercise.getId() + " due to a JSON processing error.");
        }
        dataExportQuizExerciseCreationService.exportStudentSubmissionsForArchival(quizExercise, exerciseExportDir, exportErrors, reportEntries);
        return exerciseExportDir;
    }

}
