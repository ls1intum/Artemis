package de.tum.in.www1.artemis.plagiarism;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exercise.modelingexercise.ModelingExerciseFactory;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseFactory;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.user.UserUtilService;

/**
 * Service responsible for initializing the database with specific testdata related to plagiarisms for use in integration tests.
 */
@Service
public class PlagiarismUtilService {

    private static final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(1);

    private static final ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(2);

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

    public Course addCourseWithOneFinishedTextExerciseAndSimilarSubmissions(String userPrefix, String similarSubmissionText, int studentsAmount) {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        userUtilService.addUsers(userPrefix, studentsAmount, 1, 1, 1);

        // Add text exercise to the course
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setTitle("Finished");
        textExercise.getCategories().add("Text");
        course.addExercises(textExercise);
        courseRepo.save(course);
        exerciseRepo.save(textExercise);

        Set<StudentParticipation> participations = new HashSet<>();

        for (int i = 0; i < studentsAmount; i++) {
            User participant = userUtilService.getUserByLogin(userPrefix + "student" + (i + 1));
            StudentParticipation participation = ParticipationFactory.generateStudentParticipation(InitializationState.FINISHED, textExercise, participant);
            participation.setParticipant(participant);
            TextSubmission submission = ParticipationFactory.generateTextSubmission(similarSubmissionText, Language.ENGLISH, true);
            participation = studentParticipationRepo.save(participation);
            submission.setParticipation(participation);
            submissionRepository.save(submission);

            participation.setSubmissions(Set.of(submission));
            participations.add(participation);
        }

        textExercise.participations(participations);
        exerciseRepo.save(textExercise);

        return course;
    }

    public Course addOneFinishedModelingExerciseAndSimilarSubmissionsToTheCourse(String userPrefix, String similarSubmissionModel, int studentsAmount, Course course) {
        // Add text exercise to the course
        ModelingExercise exercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, pastTimestamp, futureTimestamp, DiagramType.ClassDiagram, course);
        exercise.setTitle("finished");
        exercise.getCategories().add("Model");
        course.addExercises(exercise);

        courseRepo.save(course);
        exerciseRepo.save(exercise);

        Set<StudentParticipation> participations = new HashSet<>();

        for (int i = 0; i < studentsAmount; i++) {
            User participant = userUtilService.getUserByLogin(userPrefix + "student" + (i + 1));
            StudentParticipation participation = ParticipationFactory.generateStudentParticipation(InitializationState.FINISHED, exercise, participant);
            participation.setParticipant(participant);
            ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(similarSubmissionModel, true);
            participation = studentParticipationRepo.save(participation);
            submission.setParticipation(participation);
            submissionRepository.save(submission);

            participation.setSubmissions(Set.of(submission));
            participations.add(participation);
        }

        exercise.participations(participations);
        exerciseRepo.save(exercise);

        return course;
    }

    @NotNull
    public LinkedMultiValueMap<String, String> getDefaultPlagiarismOptions() {
        return getPlagiarismOptions(50D, 0, 0);
    }

    @NotNull
    public LinkedMultiValueMap<String, String> getPlagiarismOptions(double similarityThreshold, int minimumScore, int minimumSize) {
        // Use default options for plagiarism detection
        var params = new LinkedMultiValueMap<String, String>();
        params.add("similarityThreshold", String.valueOf(similarityThreshold));
        params.add("minimumScore", String.valueOf(minimumScore));
        params.add("minimumSize", String.valueOf(minimumSize));
        return params;
    }
}
