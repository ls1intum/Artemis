package de.tum.in.www1.artemis.service.exam;

import java.util.List;

import javax.validation.constraints.NotNull;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ModelingExerciseRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.service.ModelingExerciseImportService;
import de.tum.in.www1.artemis.service.TextExerciseImportService;

@Service
public class ExamImportService {

    private final TextExerciseImportService textExerciseImportService;

    private final TextExerciseRepository textExerciseRepository;

    private final ModelingExerciseImportService modelingExerciseImportService;

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final ExamRepository examRepository;

    public ExamImportService(TextExerciseImportService textExerciseImportService, TextExerciseRepository textExerciseRepository,
            ModelingExerciseImportService modelingExerciseImportService, ModelingExerciseRepository modelingExerciseRepository, ExamRepository examRepository) {
        this.textExerciseImportService = textExerciseImportService;
        this.textExerciseRepository = textExerciseRepository;
        this.modelingExerciseImportService = modelingExerciseImportService;
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.examRepository = examRepository;
    }

    @NotNull
    public Exam importExerciseGroups(List<ExerciseGroup> exerciseGroupsToCopy, Exam newExam) {
        // Copy each exerciseGroup
        exerciseGroupsToCopy.forEach(exerciseGroupToCopy -> {
            // Create a new ExerciseGroup
            ExerciseGroup exerciseGroupCopied = exerciseGroupToCopy.copyExerciseGroup(exerciseGroupToCopy);
            // Copy each exercise within the existing Exercise Group
            exerciseGroupToCopy.getExercises().forEach(exerciseToCopy -> {
                Exercise exerciseCopied;
                switch (exerciseToCopy.getExerciseType()) {
                    case MODELING -> {
                        final ModelingExercise originalModellingExercise = modelingExerciseRepository.findByIdWithExampleSubmissionsAndResultsElseThrow(exerciseToCopy.getId());
                        exerciseCopied = modelingExerciseImportService.importModelingExercise(originalModellingExercise, (ModelingExercise) exerciseToCopy);
                    }
                    case TEXT -> {
                        final TextExercise originalTextExercise = textExerciseRepository.findByIdWithExampleSubmissionsAndResultsElseThrow(exerciseToCopy.getId());
                        exerciseCopied = textExerciseImportService.importTextExercise(originalTextExercise, (TextExercise) exerciseToCopy);
                    }
                    /*
                     * case PROGRAMMING -> System.out.println("hello"); case QUIZ -> System.out.println("hello"); case FILE_UPLOAD -> System.out.println("hello");
                     */
                    default -> {
                        return;
                    }
                }
                // Attach the newly created Exercise to the new Exercise Group
                exerciseGroupCopied.addExercise(exerciseCopied);
            });
            // Attach the new Exercise Group with the newly created Exercise to the new Exam
            newExam.addExerciseGroup(exerciseGroupCopied);
        });
        // Save the whole package and return it
        return examRepository.save(newExam);
    }
}
