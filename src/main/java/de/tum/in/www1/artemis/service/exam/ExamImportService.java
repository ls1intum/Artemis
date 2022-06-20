package de.tum.in.www1.artemis.service.exam;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ModelingExerciseImportService;
import de.tum.in.www1.artemis.service.QuizExerciseImportService;
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

    private final QuizExerciseRepository quizExerciseRepository;

    private final QuizExerciseImportService quizExerciseImportService;

    public ExamImportService(TextExerciseImportService textExerciseImportService, TextExerciseRepository textExerciseRepository,
            ModelingExerciseImportService modelingExerciseImportService, ModelingExerciseRepository modelingExerciseRepository, ExamRepository examRepository,
            ExerciseGroupRepository exerciseGroupRepository, ExamAccessService examAccessService, QuizExerciseRepository quizExerciseRepository,
            QuizExerciseImportService importQuizExercise) {
        this.textExerciseImportService = textExerciseImportService;
        this.textExerciseRepository = textExerciseRepository;
        this.modelingExerciseImportService = modelingExerciseImportService;
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.examRepository = examRepository;
        this.exerciseGroupRepository = exerciseGroupRepository;
        this.examAccessService = examAccessService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.quizExerciseImportService = importQuizExercise;
    }

    @NotNull
    public Exam importExamWithExercises(Exam examToCopy, long targetCourseId) {

        examAccessService.checkCourseAccessForInstructorElseThrow(targetCourseId);

        // In the first step, we save the Exam without Exercises, because the Exercises are imported in the next step
        List<ExerciseGroup> exerciseGroupsToCopy = examToCopy.getExerciseGroups();
        examToCopy.setExerciseGroups(new ArrayList<>());

        Exam examCopied = examRepository.save(examToCopy);

        // Exam Import with Exercises
        examCopied = importExerciseGroupsWithExercises(exerciseGroupsToCopy, examCopied);

        return examCopied;
    }

    /**
     * Method to create a copy of the given exerciseGroups and their exercises
     *
     * @param exerciseGroupsToCopy the exerciseGrops to be copied
     * @param examCopied           the nex exam to which the new exerciseGroups should be linked
     * @return the examCopied with the copied exerciseGroups and exercises
     */
    @NotNull
    public Exam importExerciseGroupsWithExercises(List<ExerciseGroup> exerciseGroupsToCopy, Exam examCopied) {
        // Copy each exerciseGroup
        exerciseGroupsToCopy.stream().filter(exerciseGroup -> !exerciseGroup.getExercises().isEmpty()).forEach(exerciseGroupToCopy -> {
            // Helper Method to copy the Exercise Group and the exercises
            ExerciseGroup exerciseGroupCopied = copyExerciseGroupWithExercises(exerciseGroupToCopy, examCopied);
            // Attach the new Exercise Group with the newly created Exercise to the new Exam
            examCopied.addExerciseGroup(exerciseGroupCopied);
        });
        // Save the whole package and return it
        return examRepository.save(examCopied);
    }

    /**
     * Helper method to create a copy of the given ExerciseGroups and its Exercises
     *
     * @param exerciseGroupToCopy the exercise group to copy
     * @param examCopied          the already copied exam to attach to the exercise group
     * @return a new exercise group with the same properties and copied exercises
     */
    @NotNull
    private ExerciseGroup copyExerciseGroupWithExercises(ExerciseGroup exerciseGroupToCopy, Exam examCopied) {
        // Create a new ExerciseGroup with the same name and boolean:mandatory
        ExerciseGroup exerciseGroupCopied = copyExerciseGroupWithTitleAndIsMandatory(exerciseGroupToCopy, examCopied);
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
                case PROGRAMMING -> {
                    return;
                }
                case FILE_UPLOAD -> {
                    return;
                }
                case QUIZ -> {
                    final Optional<QuizExercise> optionalOriginalQuizExercise = quizExerciseRepository.findById(exerciseToCopy.getId());
                    // We do not want to abort the whole exam import process, we only skip the relevant exercise
                    if (optionalOriginalQuizExercise.isEmpty()) {
                        return;
                    }
                    exerciseCopied = quizExerciseImportService.importQuizExercise(optionalOriginalQuizExercise.get(), (QuizExercise) exerciseToCopy);
                }

                default -> {
                    return;
                }
            }
            // Attach the newly created Exercise to the new Exercise Group
            exerciseGroupCopied.addExercise(exerciseCopied);
        });
        return exerciseGroupRepository.save(exerciseGroupCopied);
    }

    /**
     * Creates a new Exercise Group with the title and boolean:isMandatory of the provided Exercise Group
     *
     * @param exerciseGroupToCopy Exercise Group, which title and isMandatory should be copied
     * @return a new Exercise Group with the same title and isMandatory
     */
    private ExerciseGroup copyExerciseGroupWithTitleAndIsMandatory(ExerciseGroup exerciseGroupToCopy, Exam newExam) {
        ExerciseGroup exerciseGroupCopied = new ExerciseGroup();
        exerciseGroupCopied.setTitle(exerciseGroupToCopy.getTitle());
        exerciseGroupCopied.setIsMandatory(exerciseGroupToCopy.getIsMandatory());
        exerciseGroupCopied.setExam(newExam);
        return exerciseGroupRepository.save(exerciseGroupCopied);
    }
}
