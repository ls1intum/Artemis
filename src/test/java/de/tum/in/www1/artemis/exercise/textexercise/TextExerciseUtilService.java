package de.tum.in.www1.artemis.exercise.textexercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.analytics.TextAssessmentEvent;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.participation.Participant;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismResultRepository;
import de.tum.in.www1.artemis.user.UserUtilService;

/**
 * Service responsible for initializing the database with specific testdata related to text exercises for use in integration tests.
 */
@Service
public class TextExerciseUtilService {

    private static final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(1);

    private static final ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(2);

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private TextSubmissionRepository textSubmissionRepo;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ResultRepository resultRepo;

    @Autowired
    private StudentParticipationRepository studentParticipationRepo;

    @Autowired
    private FeedbackRepository feedbackRepo;

    @Autowired
    private TextBlockRepository textBlockRepo;

    @Autowired
    private PlagiarismResultRepository plagiarismResultRepo;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private UserUtilService userUtilService;

    /**
     * Creates and saves a TextExercise with feedback suggestions enabled.
     *
     * @param count expected size of TextBlock set
     * @return Set of dummy TextBlocks
     */
    public Set<TextBlock> generateTextBlocks(int count) {
        Set<TextBlock> textBlocks = new HashSet<>();
        TextBlock textBlock;
        for (int i = 0; i < count; i++) {
            textBlock = new TextBlock();
            textBlock.setText("TextBlock" + i);
            textBlocks.add(textBlock);
        }
        return textBlocks;
    }

    /**
     * Create an example text exercise
     *
     * @param course The course to which the exercise belongs
     * @return the created text exercise
     * @param course The Course to which the exercise belongs
     * @return The created TextExercise
     */
    public TextExercise createSampleTextExercise(Course course) {
        var textExercise = new TextExercise();
        textExercise.setCourse(course);
        textExercise.setTitle("Title");
        textExercise.setShortName("Shortname");
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(0.0);
        textExercise = textExerciseRepository.save(textExercise);
        return textExercise;
    }

    /**
     * Creates and saves a TextExercise. Also creates and saves the given number of StudentParticipations and TextSubmissions.
     *
     * @param course          The Course to which the TextExercise belongs
     * @param textBlocks      A list of TextBlocks to be used for the TextSubmissions (must be equal to submissionCount * submissionSize)
     * @param submissionCount The number of TextSubmissions to be created
     * @param submissionSize  The number of TextBlocks to be used for each TextSubmission
     * @return The created TextExercise
     */
    public TextExercise createSampleTextExerciseWithSubmissions(Course course, List<TextBlock> textBlocks, int submissionCount, int submissionSize) {
        if (textBlocks.size() != submissionCount * submissionSize) {
            throw new IllegalArgumentException("number of textBlocks must be equal to submissionCount * submissionSize");
        }
        TextExercise textExercise = createSampleTextExercise(course);

        // submissions.length must be equal to studentParticipations.length;
        for (int i = 0; i < submissionCount; i++) {
            TextSubmission submission = new TextSubmission();
            StudentParticipation studentParticipation = new StudentParticipation();
            studentParticipation.setParticipant(userRepository.getUser());
            studentParticipation.setExercise(textExercise);
            studentParticipation = participationRepository.save(studentParticipation);

            submission.setParticipation(studentParticipation);
            submission.setLanguage(Language.ENGLISH);
            submission.setText("Test123");
            submission.setBlocks(new HashSet<>(textBlocks.subList(i * submissionSize, (i + 1) * submissionSize)));
            submission.setSubmitted(true);
            submission.setSubmissionDate(ZonedDateTime.now());
            textBlocks.subList(i * submissionSize, (i + 1) * submissionSize).forEach(textBlock -> textBlock.setSubmission(submission));

            studentParticipation.addSubmission(submission);
            textSubmissionRepo.save(submission);

            textExercise.getStudentParticipations().add(studentParticipation);
        }
        return textExercise;
    }

    /**
     * Creates and saves a TextExercise with 10 achievable points and 0 bonus points.
     *
     * @param course            The Course to which the exercise belongs
     * @param releaseDate       The release date of the TextExercise
     * @param dueDate           The due date of the TextExercise
     * @param assessmentDueDate The assessment due date of the TextExercise
     * @return The created TextExercise
     */
    public TextExercise createIndividualTextExercise(Course course, ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate) {
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(releaseDate, dueDate, assessmentDueDate, course);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(0.0);
        return exerciseRepo.save(textExercise);
    }

    /**
     * Creates and saves a TextExercise with 10 achievable points and 0 bonus points for a team.
     *
     * @param course            The Course to which the TextExercise belongs
     * @param releaseDate       The release date of the TextExercise
     * @param dueDate           The due date of the TextExercise
     * @param assessmentDueDate The assessment due date of the TextExercise
     * @return The created TextExercise
     */
    public TextExercise createTeamTextExercise(Course course, ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate) {
        TextExercise teamTextExercise = TextExerciseFactory.generateTextExercise(releaseDate, dueDate, assessmentDueDate, course);
        teamTextExercise.setMaxPoints(10.0);
        teamTextExercise.setBonusPoints(0.0);
        teamTextExercise.setMode(ExerciseMode.TEAM);
        return exerciseRepo.save(teamTextExercise);
    }

    /**
     * Creates and saves a Course with one TextExercise.
     *
     * @param title The title of the created TextExercise
     * @return The newly created Course
     */
    public Course addCourseWithOneReleasedTextExercise(String title) {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setTitle(title);
        course.addExercises(textExercise);
        course = courseRepo.save(course);
        textExercise = exerciseRepo.save(textExercise);
        assertThat(courseRepo.findWithEagerExercisesById(course.getId()).getExercises()).as("course contains the exercise").contains(textExercise);
        assertThat(textExercise.getPresentationScoreEnabled()).as("presentation score is enabled").isTrue();

        return course;
    }

    /**
     * Renames and saves the passed TextExercise using the given title.
     *
     * @param textExercise The TextExercise to be renamed
     * @param title        The new title of the TextExercise
     * @return The updated TextExercise
     */
    public TextExercise renameTextExercise(TextExercise textExercise, String title) {
        textExercise.setTitle(title);
        textExerciseRepository.save(textExercise);

        return textExercise;
    }

    /**
     * Creates and saves a Course with one TextExercise.
     *
     * @return The created Course
     */
    public Course addCourseWithOneReleasedTextExercise() {
        return addCourseWithOneReleasedTextExercise("Text");
    }

    /**
     * Creates and saves a Course with an Exam with one mandatory ExerciseGroup with one TextExercise.
     *
     * @param title The title of the created TextExercise
     * @return The created TextExercise
     */
    public TextExercise addCourseExamExerciseGroupWithOneTextExercise(String title) {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        if (title != null) {
            textExercise.setTitle(title);
        }
        return exerciseRepo.save(textExercise);
    }

    /**
     * Creates and saves a Course with an Exam with one mandatory ExerciseGroup with one TextExercise.
     *
     * @return The created TextExercise
     */
    public TextExercise addCourseExamExerciseGroupWithOneTextExercise() {
        return addCourseExamExerciseGroupWithOneTextExercise(null);
    }

    /**
     * Creates and saves a Course with an Exam with one mandatory ExerciseGroup with one TextExercise. The exam has a review date [now; now + 60min].
     *
     * @return The created TextExercise
     */
    public TextExercise addCourseExamWithReviewDatesExerciseGroupWithOneTextExercise() {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamWithReviewDatesAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        return exerciseRepo.save(textExercise);
    }

    /**
     * Creates and saves a StudentParticipation for the given TextExercise, TextSubmission, and login.
     *
     * @param exercise   The TextExercise to which the StudentParticipation belongs
     * @param submission The TextSubmission that belongs to the StudentParticipation
     * @param login      The login of the user the TextSubmission belongs to
     * @return The updated TextSubmission
     */
    public TextSubmission saveTextSubmission(TextExercise exercise, TextSubmission submission, String login) {
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        submission = textSubmissionRepo.save(submission);
        return submission;
    }

    /**
     * Creates and saves a StudentParticipation for the given TextExercise, TextSubmission, and login. Also creates and saves a Result for the StudentParticipation given the
     * assessorLogin.
     *
     * @param exercise      The TextExercise the TextSubmission belongs to
     * @param submission    The TextSubmission that belongs to the StudentParticipation
     * @param studentLogin  The login of the user the TextSubmission belongs to (for individual exercises)
     * @param teamId        The id of the team the TextSubmission belongs to (for team exercises)
     * @param assessorLogin The login of the assessor the Result belongs to
     * @return The updated TextSubmission
     */
    private TextSubmission saveTextSubmissionWithResultAndAssessor(TextExercise exercise, TextSubmission submission, String studentLogin, Long teamId, String assessorLogin) {
        StudentParticipation participation = Optional.ofNullable(studentLogin).map(login -> participationUtilService.createAndSaveParticipationForExercise(exercise, login))
                .orElseGet(() -> participationUtilService.addTeamParticipationForExercise(exercise, teamId));

        submissionRepository.save(submission);

        participation.addSubmission(submission);
        Result result = new Result();
        result.setAssessor(userUtilService.getUserByLogin(assessorLogin));
        result.setScore(100D);
        if (exercise.getReleaseDate() != null) {
            result.setCompletionDate(exercise.getReleaseDate());
        }
        else { // exam exercises do not have a release date
            result.setCompletionDate(ZonedDateTime.now());
        }
        result = resultRepo.save(result);
        result.setSubmission(submission);
        submission.setParticipation(participation);
        submission.addResult(result);
        submission.getParticipation().addResult(result);
        submission = textSubmissionRepo.save(submission);
        resultRepo.save(result);
        studentParticipationRepo.save(participation);

        submission = textSubmissionRepo.save(submission);
        return submission;
    }

    /**
     * Creates and saves a StudentParticipation for the given TextExercise, TextSubmission, and login. Also creates and saves a Result for the StudentParticipation given the
     * assessorLogin.
     *
     * @param exercise      The TextExercise the TextSubmission belongs to
     * @param submission    The TextSubmission that belongs to the StudentParticipation
     * @param login         The login of the user the TextSubmission belongs to
     * @param assessorLogin The login of the assessor the Result belongs to
     * @return The updated TextSubmission
     */
    public TextSubmission saveTextSubmissionWithResultAndAssessor(TextExercise exercise, TextSubmission submission, String login, String assessorLogin) {
        return saveTextSubmissionWithResultAndAssessor(exercise, submission, login, null, assessorLogin);
    }

    /**
     * Creates and saves a StudentParticipation for the given TextExercise, TextSubmission, and teamId. Also creates and saves a Result for the StudentParticipation given the
     * assessorLogin.
     *
     * @param exercise      The TextExercise the TextSubmission belongs to
     * @param submission    The TextSubmission that belongs to the StudentParticipation
     * @param teamId        The id of the team the TextSubmission belongs to
     * @param assessorLogin The login of the assessor the Result belongs to
     */
    public void saveTextSubmissionWithResultAndAssessor(TextExercise exercise, TextSubmission submission, long teamId, String assessorLogin) {
        saveTextSubmissionWithResultAndAssessor(exercise, submission, null, teamId, assessorLogin);
    }

    /**
     * Creates and saves a StudentParticipation for the given TextExercise, TextSubmission, and login. Also creates and saves a Result for the StudentParticipation given the
     * assessorLogin and List of Feedbacks.
     *
     * @param exercise      The TextExercise the TextSubmission belongs to
     * @param submission    The TextSubmission that belongs to the StudentParticipation
     * @param studentLogin  The login of the user the TextSubmission belongs to
     * @param assessorLogin The login of the assessor the Result belongs to
     * @param feedbacks     The Feedbacks that belong to the Result
     * @return The updated TextSubmission
     */
    public TextSubmission addTextSubmissionWithResultAndAssessorAndFeedbacks(TextExercise exercise, TextSubmission submission, String studentLogin, String assessorLogin,
            List<Feedback> feedbacks) {
        submission = saveTextSubmissionWithResultAndAssessor(exercise, submission, studentLogin, null, assessorLogin);
        Result result = submission.getLatestResult();
        for (Feedback feedback : feedbacks) {
            // Important note to prevent 'JpaSystemException: null index column for collection':
            // 1) save the child entity (without connection to the parent entity) and make sure to re-assign the return value
            feedback = feedbackRepo.save(feedback);
            // this also invokes feedback.setResult(result)
            // Important note to prevent 'JpaSystemException: null index column for collection':
            // 2) connect child and parent entity
            result.addFeedback(feedback);
        }
        // this automatically saves the feedback because of the CascadeType.All annotation
        // Important note to prevent 'JpaSystemException: null index column for collection':
        // 3) save the parent entity and make sure to re-assign the return value
        resultRepo.save(result);

        return submission;
    }

    /**
     * Saves the given TextBlocks for the given TextSubmission.
     *
     * @param blocks     The TextBlocks to be saved
     * @param submission The TextSubmission the TextBlocks belong to
     * @return The updated TextSubmission
     */
    public TextSubmission addAndSaveTextBlocksToTextSubmission(Set<TextBlock> blocks, TextSubmission submission) {
        blocks.forEach(block -> {
            block.setSubmission(submission);
            block.setTextFromSubmission();
            block.computeId();
        });
        submission.setBlocks(blocks);
        textBlockRepo.saveAll(blocks);
        return textSubmissionRepo.save(submission);
    }

    /**
     * Creates and saves a TextSubmission for the given TextExercise and Participant.
     *
     * @param textExercise The TextExercise the TextSubmission belongs to
     * @param participant  The Participant the TextSubmission belongs to
     * @param text         The text of the TextSubmission
     * @return The created TextSubmission
     */
    public TextSubmission createSubmissionForTextExercise(TextExercise textExercise, Participant participant, String text) {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission(text, Language.ENGLISH, true);
        textSubmission = textSubmissionRepo.save(textSubmission);

        StudentParticipation studentParticipation;
        if (participant instanceof User user) {
            studentParticipation = ParticipationFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise, user);
        }
        else if (participant instanceof Team team) {
            studentParticipation = participationUtilService.addTeamParticipationForExercise(textExercise, team.getId());
        }
        else {
            throw new RuntimeException("Unsupported participant!");
        }
        studentParticipation.addSubmission(textSubmission);

        studentParticipationRepo.save(studentParticipation);
        textSubmissionRepo.save(textSubmission);
        return textSubmission;
    }

    /**
     * Creates and saves a TextPlagiarismResult for the given Exercise.
     *
     * @param exercise The Exercise the TextPlagiarismResult belongs to
     * @return The created TextPlagiarismResult
     */
    public TextPlagiarismResult createTextPlagiarismResultForExercise(Exercise exercise) {
        TextPlagiarismResult result = new TextPlagiarismResult();
        result.setExercise(exercise);
        result.setSimilarityDistribution(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 });
        result.setDuration(4);
        return plagiarismResultRepo.save(result);
    }

    /**
     * Creates a TextAssessmentEvent with the given parameters.
     *
     * @param courseId        The id of the Course the TextAssessmentEvent belongs to
     * @param userId          The id of the User the TextAssessmentEvent belongs to
     * @param exerciseId      The id of the Exercise the TextAssessmentEvent belongs to
     * @param participationId The id of the Participation the TextAssessmentEvent belongs to
     * @param submissionId    The id of the Submission the TextAssessmentEvent belongs to
     * @return The created TextAssessmentEvent
     */
    public TextAssessmentEvent createSingleTextAssessmentEvent(Long courseId, Long userId, Long exerciseId, Long participationId, Long submissionId) {
        return TextExerciseFactory.generateTextAssessmentEvent(TextAssessmentEventType.EDIT_AUTOMATIC_FEEDBACK, FeedbackType.AUTOMATIC, TextBlockType.AUTOMATIC, courseId, userId,
                exerciseId, participationId, submissionId);
    }

    /**
     * Creates and saves a Course with one finished TextExercise (release, due, and assessment due date in the past).
     *
     * @return The created Course
     */
    public Course addCourseWithOneFinishedTextExercise() {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        TextExercise finishedTextExercise = TextExerciseFactory.generateTextExercise(pastTimestamp, pastTimestamp.plusHours(12), pastTimestamp.plusHours(24), course);
        finishedTextExercise.setTitle("Finished");
        course.addExercises(finishedTextExercise);
        course = courseRepo.save(course);
        exerciseRepo.save(finishedTextExercise);
        return course;
    }

    /**
     * Creates and saves a TextExercise for an Exam.
     *
     * @param exerciseGroup The ExerciseGroup to which the exercise belongs
     * @return The created TextExercise
     */
    public TextExercise createTextExerciseForExam(ExerciseGroup exerciseGroup) {
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        textExerciseRepository.save(textExercise);
        return textExercise;
    }

    /**
     * Creates and saves a TextSubmission and StudentParticipation for the given TextExercise, and studentLogin. Also creates and saves a Result for the
     * StudentParticipation given the assessorLogin.
     *
     * @param textExercise  The TextExercise the TextSubmission belongs to
     * @param studentLogin  The login of the user the TextSubmission belongs to
     * @param assessorLogin The login of the assessor the Result belongs to
     * @return The created TextSubmission
     */
    public TextSubmission createTextSubmissionWithResultAndAssessor(TextExercise textExercise, String studentLogin, String assessorLogin) {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, studentLogin, assessorLogin);

        return textSubmission;
    }
}
