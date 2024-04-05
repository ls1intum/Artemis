package de.tum.in.www1.artemis.exercise;

import static org.assertj.core.api.Assertions.*;

import java.time.ZonedDateTime;
import java.util.*;

import jakarta.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;
import de.tum.in.www1.artemis.exercise.fileuploadexercise.FileUploadExerciseUtilService;
import de.tum.in.www1.artemis.exercise.modelingexercise.ModelingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.post.ConversationFactory;
import de.tum.in.www1.artemis.post.ConversationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.AnswerPostRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
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

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private ConversationUtilService conversationUtilService;

    /**
     * Sets the max points of an exercise to 100 and sets bonus points to 10. IncludedInOverallScore of the exercise is set to INCLUDED_COMPLETELY.
     *
     * @param exercise The exercise to be updated.
     * @return The saved exercise.
     */
    public Exercise addMaxScoreAndBonusPointsToExercise(Exercise exercise) {
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        exercise.setMaxPoints(100.0);
        exercise.setBonusPoints(10.0);
        return exerciseRepo.save(exercise);
    }

    /**
     * Adds grading instructions from three different grading criteria to an exercise. One criterion does not have a title.
     *
     * @param exercise The exercise to which the grading instructions should be added.
     * @return The list of grading criteria.
     */
    public Set<GradingCriterion> addGradingInstructionsToExercise(Exercise exercise) {
        GradingCriterion emptyCriterion = ExerciseFactory.generateGradingCriterion(null);
        Set<GradingInstruction> instructionWithNoCriteria = ExerciseFactory.generateGradingInstructions(emptyCriterion, 1, 0);
        assertThat(instructionWithNoCriteria).hasSize(1);
        GradingInstruction instructionWithNoCriterion = instructionWithNoCriteria.stream().findFirst().orElseThrow();
        instructionWithNoCriterion.setCredits(1);
        instructionWithNoCriterion.setUsageCount(0);
        emptyCriterion.setExercise(exercise);
        emptyCriterion.setStructuredGradingInstructions(instructionWithNoCriteria);

        GradingCriterion testCriterion = ExerciseFactory.generateGradingCriterion("test title");
        Set<GradingInstruction> instructions = ExerciseFactory.generateGradingInstructions(testCriterion, 3, 1);
        testCriterion.setStructuredGradingInstructions(instructions);

        GradingCriterion testCriterion2 = ExerciseFactory.generateGradingCriterion("test title2");
        Set<GradingInstruction> instructionsWithBigLimit = ExerciseFactory.generateGradingInstructions(testCriterion2, 1, 4);
        testCriterion2.setStructuredGradingInstructions(instructionsWithBigLimit);

        testCriterion.setExercise(exercise);
        Set<GradingCriterion> criteria = new HashSet<>();
        criteria.add(emptyCriterion);
        criteria.add(testCriterion);
        criteria.add(testCriterion2);
        exercise.setGradingCriteria(criteria);
        return exercise.getGradingCriteria();
    }

    /**
     * Accesses the first found exercise of a course with the passed type. The course stores exercises in a set, therefore any
     * exercise with the corresponding type could be accessed.
     *
     * @param course The course which should be searched for the exercise.
     * @param clazz  The class (type) of the exercise to look for.
     * @return The first exercise which was found in the course and is of the expected type.
     */
    public <T extends Exercise> T getFirstExerciseWithType(Course course, Class<T> clazz) {
        var exercise = course.getExercises().stream().filter(ex -> ex.getClass().equals(clazz)).findFirst().orElseThrow();
        return (T) exercise;
    }

    /**
     * Accesses the first found exercise of an exam with the passed type. The course stores exercises in a set, therefore any
     * exercise with the corresponding type could be accessed.
     *
     * @param exam  The exam which should be searched for the exercise.
     * @param clazz The class (type) of the exercise to look for.
     * @return The first exercise which was found in the course and is of the expected type.
     */
    public <T extends Exercise> T getFirstExerciseWithType(Exam exam, Class<T> clazz) {
        var exercise = exam.getExerciseGroups().stream().map(ExerciseGroup::getExercises).flatMap(Collection::stream).filter(ex -> ex.getClass().equals(clazz)).findFirst()
                .orElseThrow();
        return (T) exercise;
    }

    /**
     * Accesses the first exercise of a student exam with the passed type.
     *
     * @param studentExam The student exam which should be searched for the exercise.
     * @param clazz       The class (type) of the exercise to look for.
     * @return The first exercise which was found in the course and is of the expected type.
     */
    public <T extends Exercise> T getFirstExerciseWithType(StudentExam studentExam, Class<T> clazz) {
        var exercise = studentExam.getExercises().stream().filter(ex -> ex.getClass().equals(clazz)).findFirst().orElseThrow();
        return (T) exercise;
    }

    /**
     * Generates a course with one specific exercise and the passed amount of submissions.
     *
     * @param exerciseType        The type of exercise which should be generated: programming, file-upload or text.
     * @param numberOfSubmissions The amount of submissions which should be generated for an exercise.
     * @return A course containing an exercise with submissions
     */
    public Course addCourseWithOneExerciseAndSubmissions(String userPrefix, String exerciseType, int numberOfSubmissions) {
        return addCourseWithOneExerciseAndSubmissions(userPrefix, exerciseType, numberOfSubmissions, Optional.empty());
    }

    /**
     * Generates a course with one specific exercise and the passed amount of submissions.
     *
     * @param exerciseType             The type of exercise which should be generated: modeling, programming, file-upload or text.
     * @param numberOfSubmissions      The amount of submissions which should be generated for an exercise.
     * @param modelForModelingExercise The model string for a modeling exercise.
     * @return A course containing an exercise with submissions.
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
     * Adds an automatic assessment to all submissions of an exercise.
     *
     * @param exercise The exercise of which the submissions are assessed.
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
     * Adds a result to all submissions of an exercise.
     *
     * @param exercise The exercise of which the submissions are assessed.
     * @param assessor The assessor that is set for the results of the submission.
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

    /**
     * Updates the due date of the exercise with the passed id. The updated exercise is saved in the repository.
     *
     * @param exerciseId The id of the exercise which should be updated. It is used to access the exercise.
     * @param newDueDate The new due date of the exercise.
     */
    public void updateExerciseDueDate(long exerciseId, ZonedDateTime newDueDate) {
        Exercise exercise = exerciseRepo.findById(exerciseId).orElseThrow(() -> new IllegalArgumentException("Exercise with given ID " + exerciseId + " could not be found"));
        exercise.setDueDate(newDueDate);
        if (exercise instanceof ProgrammingExercise) {
            ((ProgrammingExercise) exercise).setBuildAndTestStudentSubmissionsAfterDueDate(newDueDate);
        }
        exerciseRepo.save(exercise);
    }

    /**
     * Updates the assessment due date of the exercise with the passed id. The updated exercise is saved in the repository.
     *
     * @param exerciseId The id of the exercise which should be updated. It is used to access the exercise.
     * @param newDueDate The new assessment due date of the exercise.
     */
    public void updateAssessmentDueDate(long exerciseId, ZonedDateTime newDueDate) {
        Exercise exercise = exerciseRepo.findById(exerciseId).orElseThrow(() -> new IllegalArgumentException("Exercise with given ID " + exerciseId + " could not be found"));
        exercise.setAssessmentDueDate(newDueDate);
        exerciseRepo.save(exercise);
    }

    /**
     * Updates the result completion date using the new passed completion date.
     *
     * @param resultId          The id of the result, used to access the result from the repository.
     * @param newCompletionDate The new completion date which is set.
     */
    public void updateResultCompletionDate(long resultId, ZonedDateTime newCompletionDate) {
        Result result = resultRepo.findById(resultId).orElseThrow(() -> new IllegalArgumentException("Result with given ID " + resultId + " could not be found"));
        result.setCompletionDate(newCompletionDate);
        resultRepo.save(result);
    }

    // TODO: find some generic solution for the following duplicated code

    /**
     * Looks for a file upload exercise with the passed title within an exercise collection. If the exercise is not found, the test fails.
     *
     * @param exercises Collection in which the file upload exercise with the title should be located.
     * @param title     The title of the exercise to look for.
     * @return The found file upload exercise.
     */
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

    /**
     * Looks for a modeling exercise with the passed title within an exercise collection. If the exercise is not found, the test fails.
     *
     * @param exercises Collection in which the modeling exercise with the title should be located.
     * @param title     The title of the exercise to look for.
     * @return The found modeling exercise.
     */
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

    /**
     * Looks for a text exercise with the passed title within an exercise collection. If the exercise is not found, the test fails.
     *
     * @param exercises Collection in which the text exercise with the title should be located.
     * @param title     The title of the exercise to look for.
     * @return The found text exercise.
     */
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

    /**
     * Looks for a programming exercise with the passed title within an exercise collection. If the exercise is not found, the test fails.
     *
     * @param exercises Collection in which the programming exercise with the title should be located.
     * @param title     The title of the exercise to look for.
     * @return The found programming exercise.
     */
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

    /**
     * Creates a plagiarism case for the passed exercise.
     *
     * @param exercise   The exercise to which a plagiarism case should be added.
     * @param user       The user with plagiarism, who responds to the plagiarism case.
     * @param userPrefix The prefix of test users.
     * @param verdict    The verdict of the plagiarism case.
     */
    public void createPlagiarismCaseForUserForExercise(Exercise exercise, User user, String userPrefix, PlagiarismVerdict verdict) {
        PlagiarismCase plagiarismCase = new PlagiarismCase();
        plagiarismCase.setExercise(exercise);
        plagiarismCase = plagiarismCaseRepository.save(plagiarismCase);
        var post = conversationUtilService.createBasicPost(plagiarismCase, userPrefix);
        plagiarismCase.setExercise(exercise);
        plagiarismCase.setStudent(user);
        var answerPost = new AnswerPost();
        answerPost.setPost(post);
        answerPost.setAuthor(user);
        answerPost.setContent("No I don't think so");
        answerPost = answerPostRepository.save(answerPost);
        post.setAnswers(Set.of(answerPost));
        post = postRepository.save(post);
        plagiarismCase.setPost(post);
        plagiarismCase.setVerdictDate(ZonedDateTime.now().minusMinutes(1));
        plagiarismCase.setVerdict(verdict);
        if (verdict == PlagiarismVerdict.WARNING) {
            plagiarismCase.setVerdictMessage("Last warning");
        }
        else if (verdict == PlagiarismVerdict.POINT_DEDUCTION) {
            plagiarismCase.setVerdictPointDeduction(1);
        }
        plagiarismCaseRepository.save(plagiarismCase);
    }

    /**
     * Creates a channel and adds it to an exercise.
     *
     * @param exercise The exercise to which a channel should be added.
     * @return The newly created and saved channel.
     */
    public Channel addChannelToExercise(Exercise exercise) {
        Channel channel = ConversationFactory.generateCourseWideChannel(exercise.getCourseViaExerciseGroupOrCourseMember());
        channel.setExercise(exercise);
        return channelRepository.save(channel);
    }
}
