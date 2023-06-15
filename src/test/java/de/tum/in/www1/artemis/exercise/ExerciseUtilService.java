package de.tum.in.www1.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.time.ZonedDateTime;
import java.util.*;

import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exercise.fileuploadexercise.FileUploadExerciseUtilService;
import de.tum.in.www1.artemis.exercise.modelingexercise.ModelingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ModelingSubmissionService;
import de.tum.in.www1.artemis.user.UserUtilService;

/**
 * Service responsible for initializing the database with specific testdata related to exercises for use in integration tests.
 */
@Service
public class ExerciseUtilService {

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private StudentParticipationRepository studentParticipationRepo;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ResultRepository resultRepo;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ModelingSubmissionService modelSubmissionService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private FileUploadExerciseUtilService fileUploadExerciseUtilService;

    public Exercise addMaxScoreAndBonusPointsToExercise(Exercise exercise) {
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        exercise.setMaxPoints(100.0);
        exercise.setBonusPoints(10.0);
        return exerciseRepo.save(exercise);
    }

    public List<GradingCriterion> addGradingInstructionsToExercise(Exercise exercise) {
        GradingCriterion emptyCriterion = ExerciseFactory.generateGradingCriterion(null);
        List<GradingInstruction> instructionWithNoCriteria = ExerciseFactory.generateGradingInstructions(emptyCriterion, 1, 0);
        instructionWithNoCriteria.get(0).setCredits(1);
        instructionWithNoCriteria.get(0).setUsageCount(0);
        emptyCriterion.setExercise(exercise);
        emptyCriterion.setStructuredGradingInstructions(instructionWithNoCriteria);

        GradingCriterion testCriterion = ExerciseFactory.generateGradingCriterion("test title");
        List<GradingInstruction> instructions = ExerciseFactory.generateGradingInstructions(testCriterion, 3, 1);
        testCriterion.setStructuredGradingInstructions(instructions);

        GradingCriterion testCriterion2 = ExerciseFactory.generateGradingCriterion("test title2");
        List<GradingInstruction> instructionsWithBigLimit = ExerciseFactory.generateGradingInstructions(testCriterion2, 1, 4);
        testCriterion2.setStructuredGradingInstructions(instructionsWithBigLimit);

        testCriterion.setExercise(exercise);
        var criteria = new ArrayList<GradingCriterion>();
        criteria.add(emptyCriterion);
        criteria.add(testCriterion);
        criteria.add(testCriterion2);
        exercise.setGradingCriteria(criteria);
        return exercise.getGradingCriteria();
    }

    public <T extends Exercise> T getFirstExerciseWithType(Course course, Class<T> clazz) {
        var exercise = course.getExercises().stream().filter(ex -> ex.getClass().equals(clazz)).findFirst().get();
        return (T) exercise;
    }

    public <T extends Exercise> T getFirstExerciseWithType(Exam exam, Class<T> clazz) {
        var exercise = exam.getExerciseGroups().stream().map(ExerciseGroup::getExercises).flatMap(Collection::stream).filter(ex -> ex.getClass().equals(clazz)).findFirst().get();
        return (T) exercise;
    }

    /**
     * Generates a course with one specific exercise, and an arbitrare amount of submissions.
     *
     * @param exerciseType        - the type of exercise which should be generated: programming, file-pload or text
     * @param numberOfSubmissions - the amount of submissions which should be generated for an exercise
     * @return a course with an exercise with submissions
     */
    public Course addCourseWithOneExerciseAndSubmissions(String userPrefix, String exerciseType, int numberOfSubmissions) {
        return addCourseWithOneExerciseAndSubmissions(userPrefix, exerciseType, numberOfSubmissions, Optional.empty());
    }

    /**
     * Generates a course with one specific exercise, and an arbitrare amount of submissions.
     *
     * @param exerciseType             - the type of exercise which should be generated: modeling, programming, file-pload or text
     * @param numberOfSubmissions      - the amount of submissions which should be generated for an exercise
     * @param modelForModelingExercise - the model string for a modeling exercise
     * @return a course with an exercise with submissions
     */
    public Course addCourseWithOneExerciseAndSubmissions(String userPrefix, String exerciseType, int numberOfSubmissions, Optional<String> modelForModelingExercise) {
        Course course;
        Exercise exercise;
        switch (exerciseType) {
            case "modeling" -> {
                course = modelingExerciseUtilService.addCourseWithOneModelingExercise();
                exercise = exerciseRepo.findAllExercisesByCourseId(course.getId()).iterator().next();
                for (int j = 1; j <= numberOfSubmissions; j++) {
                    StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, userPrefix + "student" + j);
                    assertThat(modelForModelingExercise).isNotEmpty();
                    ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(modelForModelingExercise.get(), true);
                    var user = userUtilService.getUserByLogin(userPrefix + "student" + j);
                    modelSubmissionService.handleModelingSubmission(submission, (ModelingExercise) exercise, user);
                    studentParticipationRepo.save(participation);
                }
                return course;
            }
            case "programming" -> {
                course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
                exercise = exerciseRepo.findAllExercisesByCourseId(course.getId()).iterator().next();
                for (int j = 1; j <= numberOfSubmissions; j++) {
                    ProgrammingSubmission submission = new ProgrammingSubmission();
                    programmingExerciseUtilService.addProgrammingSubmission((ProgrammingExercise) exercise, submission, userPrefix + "student" + j);
                }
                return course;
            }
            case "text" -> {
                course = textExerciseUtilService.addCourseWithOneFinishedTextExercise();
                exercise = exerciseRepo.findAllExercisesByCourseId(course.getId()).iterator().next();
                for (int j = 1; j <= numberOfSubmissions; j++) {
                    TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Text" + j + j, null, true);
                    textExerciseUtilService.saveTextSubmission((TextExercise) exercise, textSubmission, userPrefix + "student" + j);
                }
                return course;
            }
            case "file-upload" -> {
                course = fileUploadExerciseUtilService.addCourseWithFileUploadExercise();
                exercise = exerciseRepo.findAllExercisesByCourseId(course.getId()).iterator().next();
                for (int j = 1; j <= numberOfSubmissions; j++) {
                    FileUploadSubmission submission = ParticipationFactory.generateFileUploadSubmissionWithFile(true, "path/to/file.pdf");
                    fileUploadExerciseUtilService.saveFileUploadSubmission((FileUploadExercise) exercise, submission, userPrefix + "student" + j);
                }
                return course;
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * Adds an automatic assessment to all submissions of an exercise
     *
     * @param exercise - the exercise of which the submissions are assessed
     */
    public void addAutomaticAssessmentToExercise(Exercise exercise) {
        var participations = studentParticipationRepo.findByExerciseIdAndTestRunWithEagerSubmissionsResultAssessor(exercise.getId(), false);
        participations.forEach(participation -> {
            Submission submission = submissionRepository.findAllByParticipationId(participation.getId()).get(0);
            submission = submissionRepository.findOneWithEagerResultAndFeedback(submission.getId());
            participation = studentParticipationRepo.findWithEagerResultsById(participation.getId()).orElseThrow();
            Result result = participationUtilService.generateResult(submission, null);
            result.setAssessmentType(AssessmentType.AUTOMATIC);
            submission.addResult(result);
            participation.addResult(result);
            studentParticipationRepo.save(participation);
            submissionRepository.save(submission);
        });
    }

    /**
     * Adds a result to all submissions of an exercise
     *
     * @param exercise - the exercise of which the submissions are assessed
     * @param assessor - the assessor which is set for the results of the submission
     */
    public void addAssessmentToExercise(Exercise exercise, User assessor) {
        var participations = studentParticipationRepo.findByExerciseIdAndTestRunWithEagerSubmissionsResultAssessor(exercise.getId(), false);
        participations.forEach(participation -> {
            Submission submission = submissionRepository.findAllByParticipationId(participation.getId()).get(0);
            submission = submissionRepository.findOneWithEagerResultAndFeedback(submission.getId());
            participation = studentParticipationRepo.findWithEagerResultsById(participation.getId()).orElseThrow();
            Result result = participationUtilService.generateResult(submission, assessor);
            submission.addResult(result);
            participation.addResult(result);
            studentParticipationRepo.save(participation);
            submissionRepository.save(submission);
        });
    }

    public void updateExerciseDueDate(long exerciseId, ZonedDateTime newDueDate) {
        Exercise exercise = exerciseRepo.findById(exerciseId).orElseThrow(() -> new IllegalArgumentException("Exercise with given ID " + exerciseId + " could not be found"));
        exercise.setDueDate(newDueDate);
        if (exercise instanceof ProgrammingExercise) {
            ((ProgrammingExercise) exercise).setBuildAndTestStudentSubmissionsAfterDueDate(newDueDate);
        }
        exerciseRepo.save(exercise);
    }

    public void updateAssessmentDueDate(long exerciseId, ZonedDateTime newDueDate) {
        Exercise exercise = exerciseRepo.findById(exerciseId).orElseThrow(() -> new IllegalArgumentException("Exercise with given ID " + exerciseId + " could not be found"));
        exercise.setAssessmentDueDate(newDueDate);
        exerciseRepo.save(exercise);
    }

    public void updateResultCompletionDate(long resultId, ZonedDateTime newCompletionDate) {
        Result result = resultRepo.findById(resultId).orElseThrow(() -> new IllegalArgumentException("Result with given ID " + resultId + " could not be found"));
        result.setCompletionDate(newCompletionDate);
        resultRepo.save(result);
    }

    // TODO: find some generic solution for the following duplicated code

    @NotNull
    public FileUploadExercise findFileUploadExerciseWithTitle(Collection<Exercise> exercises, String title) {
        Optional<Exercise> exercise = exercises.stream().filter(e -> e.getTitle().equals(title)).findFirst();
        if (exercise.isEmpty()) {
            fail("Could not find file upload exercise with title " + title);
        }
        else {
            if (exercise.get() instanceof FileUploadExercise) {
                return (FileUploadExercise) exercise.get();
            }
        }
        fail("Could not find file upload exercise with title " + title);
        // just to prevent compiler warnings, we have failed anyway here
        return new FileUploadExercise();
    }

    @NotNull
    public ModelingExercise findModelingExerciseWithTitle(Collection<Exercise> exercises, String title) {
        Optional<Exercise> exercise = exercises.stream().filter(e -> e.getTitle().equals(title)).findFirst();
        if (exercise.isEmpty()) {
            fail("Could not find modeling exercise with title " + title);
        }
        else {
            if (exercise.get() instanceof ModelingExercise) {
                return (ModelingExercise) exercise.get();
            }
        }
        fail("Could not find modeling exercise with title " + title);
        // just to prevent compiler warnings, we have failed anyway here
        return new ModelingExercise();
    }

    @NotNull
    public TextExercise findTextExerciseWithTitle(Collection<Exercise> exercises, String title) {
        Optional<Exercise> exercise = exercises.stream().filter(e -> e.getTitle().equals(title)).findFirst();
        if (exercise.isEmpty()) {
            fail("Could not find text exercise with title " + title);
        }
        else {
            if (exercise.get() instanceof TextExercise) {
                return (TextExercise) exercise.get();
            }
        }
        fail("Could not find text exercise with title " + title);
        // just to prevent compiler warnings, we have failed anyway here
        return new TextExercise();
    }

    @NotNull
    public ProgrammingExercise findProgrammingExerciseWithTitle(Collection<Exercise> exercises, String title) {
        Optional<Exercise> exercise = exercises.stream().filter(e -> e.getTitle().equals(title)).findFirst();
        if (exercise.isEmpty()) {
            fail("Could not find programming exercise with title " + title);
        }
        else {
            if (exercise.get() instanceof ProgrammingExercise) {
                return (ProgrammingExercise) exercise.get();
            }
        }
        fail("Could not find programming exercise with title " + title);
        // just to prevent compiler warnings, we have failed anyway here
        return new ProgrammingExercise();
    }
}
