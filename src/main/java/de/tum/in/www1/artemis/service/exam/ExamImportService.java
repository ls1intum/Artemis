package de.tum.in.www1.artemis.service.exam;

import java.util.List;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExerciseGroupRepository;
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

    private final ExerciseGroupRepository exerciseGroupRepository;

    private final ExamAccessService examAccessService;

    public ExamImportService(TextExerciseImportService textExerciseImportService, TextExerciseRepository textExerciseRepository,
            ModelingExerciseImportService modelingExerciseImportService, ModelingExerciseRepository modelingExerciseRepository, ExamRepository examRepository,
            ExerciseGroupRepository exerciseGroupRepository, ExamAccessService examAccessService) {
        this.textExerciseImportService = textExerciseImportService;
        this.textExerciseRepository = textExerciseRepository;
        this.modelingExerciseImportService = modelingExerciseImportService;
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.examRepository = examRepository;
        this.exerciseGroupRepository = exerciseGroupRepository;
        this.examAccessService = examAccessService;
    }

    @NotNull
    public Exam importExamWithExercises(Exam examToCopy, long targetCourseId) {

        examAccessService.checkCourseAccessForInstructorElseThrow(targetCourseId);

        // In the first step, we save the Exam without Exercises, because the Exercises are imported in the next step
        List<ExerciseGroup> exerciseGroupsToCopy = examToCopy.getExerciseGroups();
        examToCopy.setExerciseGroups(null);

        Exam examCopied = examRepository.save(examToCopy);

        // Exam Import with Exercises
        examCopied = importExerciseGroupsWithExercises(exerciseGroupsToCopy, examCopied);

        return examCopied;
    }

    /**
     * Method to create a copy of the given exerciseGroups and their exercises
     *
     * @param exerciseGroupsToCopy the exerciseGrops to be copied
     * @param newExam              the nex exam to which the new exerciseGroups should be linked
     * @return the newExam with the copied exerciseGroups and exercises
     */
    @NotNull
    public Exam importExerciseGroupsWithExercises(List<ExerciseGroup> exerciseGroupsToCopy, Exam newExam) {
        // Copy each exerciseGroup
        exerciseGroupsToCopy.forEach(exerciseGroupToCopy -> {
            ExerciseGroup exerciseGroupCopied = copyExerciseGroupWithExercises(exerciseGroupToCopy);
            // Attach the new Exercise Group with the newly created Exercise to the new Exam
            newExam.addExerciseGroup(exerciseGroupCopied);
        });
        // Save the whole package and return it
        return examRepository.save(newExam);
    }

    /**
     * Helper method to create a copy of the given ExerciseGroups and its Exercises
     *
     * @param exerciseGroupToCopy the exercise group to copy
     * @return a new exercise group with the same properties and copied exercises
     */
    @NotNull
    private ExerciseGroup copyExerciseGroupWithExercises(ExerciseGroup exerciseGroupToCopy) {
        // Create a new ExerciseGroup
        ExerciseGroup exerciseGroupCopied = exerciseGroupToCopy.copyExerciseGroup(exerciseGroupToCopy);
        // Copy each exercise within the existing Exercise Group
        exerciseGroupToCopy.getExercises().forEach(exerciseToCopy -> {
            // We need to set the new Exercise Group to the old exercise, so the new exercise group is correctly set for the new exercise
            exerciseToCopy.setExerciseGroup(exerciseGroupCopied);
            Exercise exerciseCopied;
            switch (exerciseToCopy.getExerciseType()) {
                case MODELING -> {
                    final Optional<ModelingExercise> optionalOriginalModellingExercise = modelingExerciseRepository
                            .findByIdWithExampleSubmissionsAndResults(exerciseToCopy.getId());
                    // We do not want to abort the whole exam import process, we only skip the relevant exercise
                    if (optionalOriginalModellingExercise.isEmpty()) {
                        return;
                    }
                    exerciseCopied = modelingExerciseImportService.importModelingExercise(optionalOriginalModellingExercise.get(), (ModelingExercise) exerciseToCopy);
                }
                case TEXT -> {
                    final Optional<TextExercise> optionalOriginalTextExercise = textExerciseRepository.findByIdWithExampleSubmissionsAndResults(exerciseToCopy.getId());
                    // We do not want to abort the whole exam import process, we only skip the relevant exercise
                    if (optionalOriginalTextExercise.isEmpty()) {
                        return;
                    }
                    exerciseCopied = textExerciseImportService.importTextExercise(optionalOriginalTextExercise.get(), (TextExercise) exerciseToCopy);
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
        return exerciseGroupRepository.save(exerciseGroupCopied);
    }
}
