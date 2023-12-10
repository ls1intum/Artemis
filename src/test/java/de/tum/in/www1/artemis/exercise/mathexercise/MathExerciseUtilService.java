package de.tum.in.www1.artemis.exercise.mathexercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.user.UserUtilService;

/**
 * Service responsible for initializing the database with specific testdata related to text exercises for use in integration tests.
 */
@Service
public class MathExerciseUtilService {

    private static final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(1);

    private static final ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(2);

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ResultRepository resultRepo;

    @Autowired
    private StudentParticipationRepository studentParticipationRepo;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private MathSubmissionRepository mathSubmissionRepository;

    /**
     * Creates and saves a MathExercise with 10 achievable points and 0 bonus points.
     *
     * @param course            The Course to which the exercise belongs
     * @param releaseDate       The release date of the MathExercise
     * @param dueDate           The due date of the MathExercise
     * @param assessmentDueDate The assessment due date of the MathExercise
     * @return The created MathExercise
     */
    public MathExercise createIndividualMathExercise(Course course, ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate) {
        MathExercise mathExercise = MathExerciseFactory.generateMathExercise(releaseDate, dueDate, assessmentDueDate, course);
        mathExercise.setMaxPoints(10.0);
        mathExercise.setBonusPoints(0.0);
        return exerciseRepo.save(mathExercise);
    }

    /**
     * Creates and saves a Course with one MathExercise.
     *
     * @param title The title of the created MathExercise
     * @return The newly created Course
     */
    public Course addCourseWithOneReleasedMathExercise(String title) {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        MathExercise mathExercise = MathExerciseFactory.generateMathExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        mathExercise.setTitle(title);
        course.addExercises(mathExercise);
        course = courseRepo.save(course);
        mathExercise = exerciseRepo.save(mathExercise);
        assertThat(courseRepo.findWithEagerExercisesById(course.getId()).getExercises()).as("course contains the exercise").contains(mathExercise);
        assertThat(mathExercise.getPresentationScoreEnabled()).as("presentation score is enabled").isTrue();
        return course;
    }

    /**
     * Creates and saves a Course with one MathExercise.
     *
     * @return The created Course
     */
    public Course addCourseWithOneReleasedMathExercise() {
        return addCourseWithOneReleasedMathExercise("Math");
    }

    /**
     * Creates and saves a Course with an Exam with one mandatory ExerciseGroup with one MathExercise.
     *
     * @param title The title of the created MathExercise
     * @return The created MathExercise
     */
    public MathExercise addCourseExamExerciseGroupWithOneMathExercise(String title) {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        MathExercise mathExercise = MathExerciseFactory.generateMathExerciseForExam(exerciseGroup);
        if (title != null) {
            mathExercise.setTitle(title);
        }
        return exerciseRepo.save(mathExercise);
    }

    /**
     * Creates and saves a Course with an Exam with one mandatory ExerciseGroup with one MathExercise.
     *
     * @return The created MathExercise
     */
    public MathExercise addCourseExamExerciseGroupWithOneMathExercise() {
        return addCourseExamExerciseGroupWithOneMathExercise(null);
    }

    /**
     * Creates and saves a StudentParticipation for the given MathExercise, MathSubmission, and login.
     *
     * @param exercise   The MathExercise to which the StudentParticipation belongs
     * @param submission The MathSubmission that belongs to the StudentParticipation
     * @param login      The login of the user the MathSubmission belongs to
     * @return The updated MathSubmission
     */
    public MathSubmission saveMathSubmission(MathExercise exercise, MathSubmission submission, String login) {
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        submission = mathSubmissionRepository.save(submission);
        return submission;
    }

    /**
     * Creates and saves a StudentParticipation for the given MathExercise, MathSubmission, and login. Also creates and saves a Result for the StudentParticipation given the
     * assessorLogin.
     *
     * @param exercise      The MathExercise the MathSubmission belongs to
     * @param submission    The MathSubmission that belongs to the StudentParticipation
     * @param studentLogin  The login of the user the MathSubmission belongs to (for individual exercises)
     * @param teamId        The id of the team the MathSubmission belongs to (for team exercises)
     * @param assessorLogin The login of the assessor the Result belongs to
     * @return The updated MathSubmission
     */
    private MathSubmission saveMathSubmissionWithResultAndAssessor(MathExercise exercise, MathSubmission submission, String studentLogin, Long teamId, String assessorLogin) {
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
        submission = mathSubmissionRepository.save(submission);
        resultRepo.save(result);
        studentParticipationRepo.save(participation);

        submission = mathSubmissionRepository.save(submission);
        return submission;
    }

    /**
     * Creates and saves a StudentParticipation for the given MathExercise, MathSubmission, and login. Also creates and saves a Result for the StudentParticipation given the
     * assessorLogin.
     *
     * @param exercise      The MathExercise the MathSubmission belongs to
     * @param submission    The MathSubmission that belongs to the StudentParticipation
     * @param login         The login of the user the MathSubmission belongs to
     * @param assessorLogin The login of the assessor the Result belongs to
     * @return The updated MathSubmission
     */
    public MathSubmission saveMathSubmissionWithResultAndAssessor(MathExercise exercise, MathSubmission submission, String login, String assessorLogin) {
        return saveMathSubmissionWithResultAndAssessor(exercise, submission, login, null, assessorLogin);
    }

    /**
     * Creates and saves a Course with one finished MathExercise (release, due, and assessment due date in the past).
     *
     * @return The created Course
     */
    public Course addCourseWithOneFinishedMathExercise() {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        MathExercise finishedMathExercise = de.tum.in.www1.artemis.exercise.mathexercise.MathExerciseFactory.generateMathExercise(pastTimestamp, pastTimestamp.plusHours(12),
                pastTimestamp.plusHours(24), course);
        finishedMathExercise.setTitle("Finished");
        course.addExercises(finishedMathExercise);
        course = courseRepo.save(course);
        exerciseRepo.save(finishedMathExercise);
        return course;
    }
}
