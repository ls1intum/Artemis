package de.tum.cit.aet.artemis.math.util;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;

import java.time.ZonedDateTime;
import java.util.HashSet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.repository.MathExerciseRepository;
import de.tum.cit.aet.artemis.math.repository.MathSubmissionRepository;

@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class MathExerciseUtilService {

    private static final ZonedDateTime PAST = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(1);

    private static final ZonedDateTime FAR_FUTURE = ZonedDateTime.now().plusDays(2);

    @Autowired
    private CourseTestRepository courseRepo;

    @Autowired
    private ExerciseTestRepository exerciseRepository;

    @Autowired
    private MathExerciseRepository mathExerciseRepository;

    @Autowired
    private MathSubmissionRepository mathSubmissionRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ExampleSubmissionRepository exampleSubmissionRepository;

    public void addMathExerciseToCourse(Course course) {
        MathExercise exercise = MathExerciseFactory.generateMathExercise(PAST, FUTURE, FAR_FUTURE, course);
        exercise = exerciseRepository.save(exercise);
        course.addExercises(exercise);
    }

    public Course addCourseWithMathExercise() {
        Course course = CourseFactory.generateCourse(null, PAST, FAR_FUTURE, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);
        addMathExerciseToCourse(course);
        return course;
    }

    public void saveExercise(MathExercise exercise) {
        mathExerciseRepository.save(exercise);
    }

    public MathSubmission createAndSaveSubmissionForExercise(MathExercise exercise, String login, boolean submitted) {
        var participation = participationUtilService.createAndSaveParticipationForExercise(exercise, login);
        MathSubmission submission = MathExerciseFactory.generateMathSubmission(submitted);
        submission.setParticipation(participation);
        return mathSubmissionRepository.save(submission);
    }

    /**
     * Creates and saves an ExampleSubmission (with its own MathSubmission carrying {@code content}) for the given exercise.
     *
     * @param exercise the exercise the example submission belongs to
     * @param content  the opaque work payload stored on the underlying MathSubmission
     * @return the saved ExampleSubmission
     */
    public ExampleSubmission addExampleSubmissionToMathExercise(MathExercise exercise, String content) {
        MathSubmission submission = MathExerciseFactory.generateMathSubmission(true);
        submission.setExampleSubmission(true);
        submission.setContent(content);
        submission = mathSubmissionRepository.save(submission);
        ExampleSubmission exampleSubmission = new ExampleSubmission();
        exampleSubmission.setExercise(exercise);
        exampleSubmission.setSubmission(submission);
        return exampleSubmissionRepository.save(exampleSubmission);
    }

    /**
     * Creates and saves an ExampleSubmission whose MathSubmission carries an example {@link Result} with a single {@link Feedback}.
     * Used to exercise the assessment-copying path of the exercise import.
     *
     * @param exercise     the exercise the example submission belongs to
     * @param content      the opaque work payload stored on the underlying MathSubmission
     * @param score        the score of the example result
     * @param feedbackText the detail text of the single feedback attached to the result
     * @return the saved ExampleSubmission (its submission has one result with one feedback)
     */
    public ExampleSubmission addExampleSubmissionWithAssessmentToMathExercise(MathExercise exercise, String content, double score, String feedbackText) {
        MathSubmission submission = MathExerciseFactory.generateMathSubmission(true);
        submission.setExampleSubmission(true);
        submission.setContent(content);
        submission = mathSubmissionRepository.save(submission);

        Feedback feedback = new Feedback();
        feedback.setDetailText(feedbackText);
        feedback.setCredits(score);
        feedback.setType(FeedbackType.MANUAL);

        Result result = new Result();
        result.setSubmission(submission);
        result.setExerciseId(exercise.getId());
        result.setScore(score);
        result.setRated(true);
        result.setExampleResult(true);
        result.setAssessmentType(AssessmentType.MANUAL);
        result.setCompletionDate(ZonedDateTime.now());
        result.addFeedback(feedback);

        // Submission.results cascades ALL, which persists the result and (via Result.feedbacks) its feedback.
        submission.addResult(result);
        submission = mathSubmissionRepository.save(submission);

        ExampleSubmission exampleSubmission = new ExampleSubmission();
        exampleSubmission.setExercise(exercise);
        exampleSubmission.setSubmission(submission);
        return exampleSubmissionRepository.save(exampleSubmission);
    }
}
