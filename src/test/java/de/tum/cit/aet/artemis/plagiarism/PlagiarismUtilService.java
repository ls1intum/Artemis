package de.tum.cit.aet.artemis.plagiarism;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.course.CourseFactory;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.Submission;
import de.tum.cit.aet.artemis.domain.TextExercise;
import de.tum.cit.aet.artemis.domain.enumeration.DiagramType;
import de.tum.cit.aet.artemis.domain.enumeration.ExerciseMode;
import de.tum.cit.aet.artemis.domain.enumeration.InitializationState;
import de.tum.cit.aet.artemis.domain.enumeration.Language;
import de.tum.cit.aet.artemis.domain.modeling.ModelingExercise;
import de.tum.cit.aet.artemis.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.modeling.ModelingExerciseFactory;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.text.TextExerciseFactory;
import de.tum.cit.aet.artemis.participation.ParticipationFactory;
import de.tum.cit.aet.artemis.participation.ParticipationUtilService;
import de.tum.cit.aet.artemis.team.TeamUtilService;
import de.tum.cit.aet.artemis.user.UserUtilService;

/**
 * Service responsible for initializing the database with specific testdata related to plagiarisms for use in integration tests.
 */
@Service
public class PlagiarismUtilService {

    private static final ZonedDateTime PAST_TIMESTAMP = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime FUTURE_TIMESTAMP = ZonedDateTime.now().plusDays(1);

    private static final ZonedDateTime FUTURE_FUTURE_TIMESTAMP = ZonedDateTime.now().plusDays(2);

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private StudentParticipationRepository studentParticipationRepo;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private Course createCourseWithUsers(String userPrefix, int studentsAmount) {
        Course course = CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_TIMESTAMP, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        userUtilService.addUsers(userPrefix, studentsAmount, 1, 1, 1);
        return course;
    }

    private TextExercise createTextExercise(String userPrefix, int studentsAmount, ExerciseMode mode) {
        var course = createCourseWithUsers(userPrefix, studentsAmount);
        var exercise = TextExerciseFactory.generateTextExercise(PAST_TIMESTAMP, FUTURE_TIMESTAMP, FUTURE_FUTURE_TIMESTAMP, course);
        exercise.setMode(mode);
        course.addExercises(exercise);
        courseRepo.save(course);
        return exerciseRepo.save(exercise);
    }

    private ModelingExercise createModelingExercise(String userPrefix, int studentsAmount, ExerciseMode mode) {
        var course = createCourseWithUsers(userPrefix, studentsAmount);
        var exercise = ModelingExerciseFactory.generateModelingExercise(PAST_TIMESTAMP, PAST_TIMESTAMP, FUTURE_TIMESTAMP, DiagramType.ClassDiagram, course);
        exercise.setMode(mode);
        course.addExercises(exercise);
        courseRepo.save(course);
        return exerciseRepo.save(exercise);
    }

    private StudentParticipation saveParticipationAndAddSubmission(StudentParticipation participation, Submission submission) {
        var savedParticipation = studentParticipationRepo.save(participation);
        submission.setParticipation(savedParticipation);
        submissionRepository.save(submission);

        savedParticipation.setSubmissions(Set.of(submission));
        return savedParticipation;
    }

    /**
     * Creates an individual TextExercise. Also creates and saves a Course and a StudentParticipations with similar Submissions.
     *
     * @param userPrefix            The prefix for the user logins
     * @param similarSubmissionText The text that each student submits
     * @param studentsAmount        The number of students that submitted the text
     * @return id of created exercise
     */
    public long createTextExerciseAndSimilarSubmissions(String userPrefix, String similarSubmissionText, int studentsAmount) {
        var exercise = createTextExercise(userPrefix, studentsAmount, ExerciseMode.INDIVIDUAL);
        for (int i = 0; i < studentsAmount; i++) {
            var participant = userUtilService.getUserByLogin(userPrefix + "student" + (i + 1));
            var participation = ParticipationFactory.generateStudentParticipation(InitializationState.FINISHED, exercise, participant);
            participation.setParticipant(participant);
            var submission = ParticipationFactory.generateTextSubmission(similarSubmissionText, Language.ENGLISH, true);
            saveParticipationAndAddSubmission(participation, submission);
            exercise.addParticipation(participation);
        }
        exerciseRepo.save(exercise);
        return exercise.getId();
    }

    /**
     * Creates a team TextExercise. Also creates and saves a Course and a StudentParticipations with similar Submissions.
     *
     * @param userPrefix            The prefix for the user logins
     * @param similarSubmissionText The text that each student submits
     * @param teamsAmount           The number of teams that submitted the text
     * @return id of created exercise
     */
    public long createTeamTextExerciseAndSimilarSubmissions(String userPrefix, String similarSubmissionText, int teamsAmount) {
        var exercise = createTextExercise(userPrefix, 0, ExerciseMode.TEAM);
        var instructor = userUtilService.getUserByLogin(userPrefix + "instructor1");
        var teams = teamUtilService.addTeamsForExercise(exercise, "team-" + userPrefix, userPrefix, teamsAmount, instructor);
        for (var team : teams) {
            var participation = participationUtilService.addTeamParticipationForExercise(exercise, team.getId());
            var submission = ParticipationFactory.generateTextSubmission(similarSubmissionText, Language.ENGLISH, true);
            saveParticipationAndAddSubmission(participation, submission);
            exercise.addParticipation(participation);
        }
        exerciseRepo.save(exercise);
        return exercise.getId();
    }

    /**
     * Creates and saves an individual ModelingExercise. Also creates and saves a Course and a StudentParticipations with similar Submissions.
     *
     * @param userPrefix             The prefix for the user logins
     * @param similarSubmissionModel The model that each student submits
     * @param studentsAmount         The number of students that submitted the model
     * @return id of created exercise
     */
    public long createModelingExerciseAndSimilarSubmissionsToTheCourse(String userPrefix, String similarSubmissionModel, int studentsAmount) {
        var exercise = createModelingExercise(userPrefix, studentsAmount, ExerciseMode.INDIVIDUAL);
        for (int i = 0; i < studentsAmount; i++) {
            var participant = userUtilService.getUserByLogin(userPrefix + "student" + (i + 1));
            var participation = ParticipationFactory.generateStudentParticipation(InitializationState.FINISHED, exercise, participant);
            participation.setParticipant(participant);
            var submission = ParticipationFactory.generateModelingSubmission(similarSubmissionModel, true);
            saveParticipationAndAddSubmission(participation, submission);
            exercise.addParticipation(participation);
        }
        exerciseRepo.save(exercise);
        return exercise.getId();
    }

    /**
     * Creates and saves an individual ModelingExercise. Also creates and saves a Course and a StudentParticipations with similar Submissions.
     *
     * @param userPrefix             The prefix for the user logins
     * @param similarSubmissionModel The model that each student submits
     * @param teamsAmount            The number of teams that submitted the text
     * @return id of created exercise
     */
    public long createTeamModelingExerciseAndSimilarSubmissionsToTheCourse(String userPrefix, String similarSubmissionModel, int teamsAmount) {
        var exercise = createModelingExercise(userPrefix, 0, ExerciseMode.TEAM);
        var instructor = userUtilService.getUserByLogin(userPrefix + "instructor1");
        var teams = teamUtilService.addTeamsForExercise(exercise, "team-" + userPrefix, userPrefix, teamsAmount, instructor);
        for (var team : teams) {
            var participation = participationUtilService.addTeamParticipationForExercise(exercise, team.getId());
            var submission = ParticipationFactory.generateModelingSubmission(similarSubmissionModel, true);
            saveParticipationAndAddSubmission(participation, submission);
            exercise.addParticipation(participation);
        }
        exerciseRepo.save(exercise);
        return exercise.getId();
    }

    /**
     * Generates a LinkedMultiValueMap with default parameters for the plagiarism detection. The map is used for REST calls and maps the parameters to the values.
     * The keys and values are "similarityThreshold" = 50, "minimumScore" = 0 and "minimumSize" = 0.
     *
     * @return The generated LinkedMultiValueMap
     */
    @NotNull
    public LinkedMultiValueMap<String, String> getDefaultPlagiarismOptions() {
        return getPlagiarismOptions(50, 0, 0);
    }

    /**
     * Generates a LinkedMultiValueMap with the given parameters for the plagiarism detection. The map is used for REST calls and maps the parameters to the values.
     * The keys are "similarityThreshold", "minimumScore" and "minimumSize".
     *
     * @param similarityThreshold The similarity threshold
     * @param minimumScore        The minimum score
     * @param minimumSize         The minimum size
     * @return The generated LinkedMultiValueMap
     */
    @NotNull
    public LinkedMultiValueMap<String, String> getPlagiarismOptions(int similarityThreshold, int minimumScore, int minimumSize) {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("similarityThreshold", String.valueOf(similarityThreshold));
        params.add("minimumScore", String.valueOf(minimumScore));
        params.add("minimumSize", String.valueOf(minimumSize));
        return params;
    }
}
