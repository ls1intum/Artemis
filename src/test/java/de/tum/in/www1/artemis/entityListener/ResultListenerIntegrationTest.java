package de.tum.in.www1.artemis.entityListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participant;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.StudentScoreService;
import de.tum.in.www1.artemis.util.ModelFactory;

@ExtendWith(SpringExtension.class)
public class ResultListenerIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    Long idOfCourse;

    Long idOfTextExercise;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    ExerciseRepository exerciseRepository;

    @Autowired
    SubmissionRepository submissionRepository;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    ParticipationService participationService;

    @Autowired
    StudentScoreRepository studentScoreRepository;

    @Autowired
    UserRepository userRepository;

    @SpyBean
    private StudentScoreService studentScoreService;

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @BeforeEach
    public void setupTestScenario() throws Exception {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        // creating the users student1-student5, tutor1-tutor10 and instructors1-instructor10
        this.database.addUsers(5, 10, 10);
        // creating course
        Course course = this.database.createCourse();
        idOfCourse = course.getId();
        createTextExercise(pastTimestamp, pastTimestamp, pastTimestamp);
    }

    @Test
    public void resultListenerPostPersist_newResultCreatedForExercise_shouldPersistNewStudentScore() {
        List<StudentScore> savedStudentScores = studentScoreRepository.findAll();
        assertThat(savedStudentScores).isEmpty();
        User student1 = userRepository.findOneByLogin("student1").get();
        createParticipationSubmissionAndResult(idOfTextExercise, student1, 10.0, 10.0, 200, true);
        savedStudentScores = studentScoreRepository.findAll();
        assertThat(savedStudentScores).isNotEmpty();
        assertThat(savedStudentScores).size().isEqualTo(1);
        StudentScore savedStudentScore = savedStudentScores.get(0);
        verify(this.studentScoreService, times(2)).updateResult(any());
    }

    private void createTextExercise(ZonedDateTime pastTimestamp, ZonedDateTime futureTimestamp, ZonedDateTime futureFutureTimestamp) {
        Course course;
        // creating text exercise with Result
        course = courseRepository.findWithEagerExercisesById(idOfCourse);
        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setMaxScore(10.0);
        textExercise.setBonusPoints(0.0);
        textExercise = exerciseRepository.save(textExercise);
        idOfTextExercise = textExercise.getId();
    }

    private void createParticipationSubmissionAndResult(Long idOfExercise, Participant participant, Double pointsOfExercise, Double bonusPointsOfExercise, long scoreAwarded,
            boolean rated) {
        Exercise exercise = exerciseRepository.findById(idOfExercise).get();

        if (!exercise.getMaxScore().equals(pointsOfExercise)) {
            exercise.setMaxScore(pointsOfExercise);
        }
        if (!exercise.getBonusPoints().equals(bonusPointsOfExercise)) {
            exercise.setBonusPoints(bonusPointsOfExercise);
        }
        exercise = exerciseRepository.save(exercise);

        StudentParticipation studentParticipation = participationService.startExercise(exercise, participant, false);

        Submission submission;
        if (exercise instanceof ProgrammingExercise) {
            submission = new ProgrammingSubmission();
        }
        else if (exercise instanceof ModelingExercise) {
            submission = new ModelingSubmission();
        }
        else if (exercise instanceof TextExercise) {
            submission = new TextSubmission();
        }
        else if (exercise instanceof FileUploadExercise) {
            submission = new FileUploadSubmission();
        }
        else if (exercise instanceof QuizExercise) {
            submission = new QuizSubmission();
        }
        else {
            throw new RuntimeException("Unsupported exercise type: " + exercise);
        }

        submission.setType(SubmissionType.MANUAL);
        submission.setParticipation(studentParticipation);
        submission = submissionRepository.save(submission);

        // result
        Result result = ModelFactory.generateResult(rated, scoreAwarded);
        result.setParticipation(studentParticipation);
        result = resultRepository.save(result);

        submission.addResult(result);
        result.setSubmission(submission);
        submissionRepository.save(submission);
    }

}
