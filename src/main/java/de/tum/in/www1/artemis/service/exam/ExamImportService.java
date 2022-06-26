package de.tum.in.www1.artemis.service.exam;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import javax.validation.constraints.NotNull;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ModelingExerciseImportService;
import de.tum.in.www1.artemis.service.QuizExerciseImportService;
import de.tum.in.www1.artemis.service.TextExerciseImportService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseService;
import de.tum.in.www1.artemis.web.rest.errors.ExamConfigurationException;

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

    private final CourseRepository courseRepository;

    private final ProgrammingExerciseService programmingExerciseService;

    public ExamImportService(TextExerciseImportService textExerciseImportService, TextExerciseRepository textExerciseRepository,
            ModelingExerciseImportService modelingExerciseImportService, ModelingExerciseRepository modelingExerciseRepository, ExamRepository examRepository,
            ExerciseGroupRepository exerciseGroupRepository, ExamAccessService examAccessService, QuizExerciseRepository quizExerciseRepository,
            QuizExerciseImportService importQuizExercise, CourseRepository courseRepository, ProgrammingExerciseService programmingExerciseService,
            ProgrammingExerciseService programmingExerciseService1) {
        this.textExerciseImportService = textExerciseImportService;
        this.textExerciseRepository = textExerciseRepository;
        this.modelingExerciseImportService = modelingExerciseImportService;
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.examRepository = examRepository;
        this.exerciseGroupRepository = exerciseGroupRepository;
        this.examAccessService = examAccessService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.quizExerciseImportService = importQuizExercise;
        this.courseRepository = courseRepository;
        this.programmingExerciseService = programmingExerciseService1;
    }

    /**
     * Imports the given Exam with ExerciseGroups and Exercises to the given target Course
     *
     * @param examToCopy     the exam which should be copied together with exercise groups and exercises
     * @param targetCourseId the course to which the exam should be imported
     * @return the copied Exam with Exercise Groups and Exercises
     */
    @NotNull
    public Exam importExamWithExercises(Exam examToCopy, long targetCourseId) {

        examAccessService.checkCourseAccessForInstructorElseThrow(targetCourseId);
        Course targetCourse = courseRepository.findByIdElseThrow(targetCourseId);

        preCheckProgrammingExercisesForShortNameUniqueness(examToCopy.getExerciseGroups(), targetCourse.getShortName());

        // 1st: Save the exam without exercises to the database
        List<ExerciseGroup> exerciseGroupsToCopy = examToCopy.getExerciseGroups();
        Exam examCopied = createCopyOfExamWithoutConductionSpecificAttributes(examToCopy, targetCourse);

        // 2nd: Copy the exercise groups to the exam
        examCopied = copyExerciseGroupsWithExercisesToExam(exerciseGroupsToCopy, examCopied);

        return examCopied;
    }

    /**
     * Imports the given ExerciseGroups with exercises to the given exam
     *
     * @param exerciseGroupsToCopy the Exercise Groups to be imported
     * @param targetExamId         the target exam id
     * @param courseId             the associated course of the exam
     * @return a List of all Exercise Groups of the target exam
     */
    @NotNull
    public List<ExerciseGroup> importExerciseGroupsWithExercisesToExistingExam(List<ExerciseGroup> exerciseGroupsToCopy, long targetExamId, long courseId) {

        examAccessService.checkCourseAndExamAccessForEditorElseThrow(courseId, targetExamId);

        Course targetCourse = courseRepository.findByIdElseThrow(courseId);

        preCheckProgrammingExercisesForShortNameUniqueness(exerciseGroupsToCopy, targetCourse.getShortName());

        Exam targetExam = examRepository.findByIdElseThrow(targetExamId);

        // The Exam is used to ensure the connection ExerciseGroups <-> Exam
        targetExam = copyExerciseGroupsWithExercisesToExam(exerciseGroupsToCopy, targetExam);

        return targetExam.getExerciseGroups();
    }

    /**
     * Checks the programming exercises of the given exercise groups for the uniqueness of the projectKey chosen by the user.
     * If a non-unique project key is discovered, the short name is resettet, so that the user can input a new one
     *
     * @param exerciseGroupList the list of all exercises (not only programming) to be checked
     * @throws ExamConfigurationException in case one or more programming exercise project keys are not unique
     */
    private void preCheckProgrammingExercisesForShortNameUniqueness(List<ExerciseGroup> exerciseGroupList, String targetCourseShortName) {
        // Flag to determine, if a programming exercise with an invalid shortName was found
        AtomicInteger numberOfInvalidProgrammingExercises = new AtomicInteger();
        // Iterate over all exercises
        exerciseGroupList.forEach(exerciseGroup -> {
            exerciseGroup.getExercises().forEach(exercise -> {
                if (exercise.getExerciseType() == ExerciseType.PROGRAMMING) {
                    // Method to check, if the project already exists.
                    // boolean validShortName = programmingExerciseService.checkIfProjectExistsOnVCSOrCI((ProgrammingExercise) exercise, false);
                    // if (!validShortName) {
                    // If the project already exists and thus the short name isn't valid, it is removed
                    boolean validShortName = programmingExerciseService.preCheckProjectExistsOnVCSOrCI((ProgrammingExercise) exercise, targetCourseShortName);
                    exercise.setShortName("");
                    exercise.setTitle("");
                    numberOfInvalidProgrammingExercises.getAndIncrement();
                    // }
                }
            });
        });
        if (numberOfInvalidProgrammingExercises.get() > 0) {
            // In case of an invalid configuration, the exam is sent back to the client with the short names removed, wherever a new one must be chosen
            throw new ExamConfigurationException(exerciseGroupList, numberOfInvalidProgrammingExercises.get());
        }
    }

    /**
     * Method to create a copy of the given exerciseGroups and their exercises
     *
     * @param exerciseGroupsToCopy the exerciseGroups to be copied
     * @param targetExam           the nex exam to which the new exerciseGroups should be linked
     * @return the targetExam with the copied exerciseGroups and exercises
     */
    @NotNull
    private Exam copyExerciseGroupsWithExercisesToExam(List<ExerciseGroup> exerciseGroupsToCopy, Exam targetExam) {
        // Copy each exerciseGroup containing exercises
        List<ExerciseGroup> exerciseGroupsCopied = exerciseGroupsToCopy.stream().filter(exerciseGroup -> !exerciseGroup.getExercises().isEmpty()).map(exerciseGroupToCopy -> {
            // Helper Method to copy the single Exercise Group and the exercises
            return copySingleExerciseGroupWithExercises(exerciseGroupToCopy, targetExam);
        }).toList();

        // Attach the new Exercise Group with the newly created Exercise to the Exam
        exerciseGroupsCopied.forEach(targetExam::addExerciseGroup);
        // Save the exam to ensure the connection ExerciseGroups <-> Exam
        return examRepository.save(targetExam);
    }

    /**
     * Helper method to create a copy of the given ExerciseGroups and its Exercises
     *
     * @param exerciseGroupToCopy the exercise group to copy
     * @param examCopied          the already copied exam to attach to the exercise group
     * @return a new exercise group with the same properties and copied exercises
     */
    @NotNull
    private ExerciseGroup copySingleExerciseGroupWithExercises(ExerciseGroup exerciseGroupToCopy, Exam examCopied) {
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
     * @param targetExam          The Exam to which the new exerciseGroup should be linked
     * @return a new Exercise Group with the same title and isMandatory
     */
    private ExerciseGroup copyExerciseGroupWithTitleAndIsMandatory(ExerciseGroup exerciseGroupToCopy, Exam targetExam) {
        ExerciseGroup exerciseGroupCopied = new ExerciseGroup();
        exerciseGroupCopied.setTitle(exerciseGroupToCopy.getTitle());
        exerciseGroupCopied.setIsMandatory(exerciseGroupToCopy.getIsMandatory());
        exerciseGroupCopied.setExam(targetExam);
        return exerciseGroupRepository.save(exerciseGroupCopied);
    }

    /**
     * Helper method to create a copy of the given Exam without conduction specific attributes
     *
     * @param examToCopy the exam to be copied
     * @return a copy of the given exam without conduction specific attributes
     */
    private Exam createCopyOfExamWithoutConductionSpecificAttributes(Exam examToCopy, Course targetCourse) {
        examToCopy.setExerciseGroups(new ArrayList<>());
        examToCopy.setRegisteredUsers(new HashSet<>());
        examToCopy.setStudentExams(new HashSet<>());
        examToCopy.setId(null);
        examToCopy.setCourse(targetCourse);

        return examRepository.save(examToCopy);
    }
}
