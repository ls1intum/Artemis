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
import org.springframework.security.test.context.support.WithMockUser;
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
    StudentParticipationRepository studentParticipationRepository;

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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void resultListenerPostPersist_firstResultForStudentAndExercise_shouldPersistNewStudentScore() {
        List<StudentScore> savedStudentScores = studentScoreRepository.findAll();
        assertThat(savedStudentScores).isEmpty();
        User student1 = userRepository.findOneByLogin("student1").get();
        // saving the result should trigger the entity listener and create a new student score
        Result persistedResult = createParticipationSubmissionAndResult(idOfTextExercise, student1, 10.0, 10.0, 200, true);
        savedStudentScores = studentScoreRepository.findAll();
        assertThat(savedStudentScores).isNotEmpty();
        assertThat(savedStudentScores).size().isEqualTo(1);
        StudentScore savedStudentScore = savedStudentScores.get(0);
        assertStudentScoreStructure(savedStudentScore, idOfTextExercise, student1.getId(), persistedResult.getId(), persistedResult.getScore(), persistedResult.getId(),
                persistedResult.getScore());
        verify(this.studentScoreService, times(1)).updateStudentScores(any());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void resultListenerPostPersist_firstNOTRatedResultForStudentAndExercise_shouldPersistNewStudentScore() {
        List<StudentScore> savedStudentScores = studentScoreRepository.findAll();
        assertThat(savedStudentScores).isEmpty();
        User student1 = userRepository.findOneByLogin("student1").get();
        // saving the result should trigger the entity listener and create a new student score
        Result persistedResult = createParticipationSubmissionAndResult(idOfTextExercise, student1, 10.0, 10.0, 200, false);
        savedStudentScores = studentScoreRepository.findAll();
        assertThat(savedStudentScores).isNotEmpty();
        assertThat(savedStudentScores).size().isEqualTo(1);
        StudentScore savedStudentScore = savedStudentScores.get(0);
        assertStudentScoreStructure(savedStudentScore, idOfTextExercise, student1.getId(), persistedResult.getId(), persistedResult.getScore(), null, null);
        verify(this.studentScoreService, times(1)).updateStudentScores(any());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void resultListenerPostPersist_secondResultForStudentAndExercise_shouldUpdateExistingStudentScore() {
        List<StudentScore> savedStudentScores = studentScoreRepository.findAll();
        assertThat(savedStudentScores).isEmpty();
        User student1 = userRepository.findOneByLogin("student1").get();
        // saving the rated result should trigger the entity listener and create a new student score
        Result originalResult = createParticipationSubmissionAndResult(idOfTextExercise, student1, 10.0, 10.0, 200, true);
        savedStudentScores = studentScoreRepository.findAll();
        assertThat(savedStudentScores).isNotEmpty();
        assertThat(savedStudentScores).size().isEqualTo(1);
        StudentScore savedStudentScore = savedStudentScores.get(0);
        assertStudentScoreStructure(savedStudentScore, idOfTextExercise, student1.getId(), originalResult.getId(), originalResult.getScore(), originalResult.getId(),
                originalResult.getScore());
        // creating a new rated result should trigger the entity listener and update the student score
        StudentParticipation studentParticipation = studentParticipationRepository.findByExerciseIdAndStudentId(idOfTextExercise, student1.getId()).get(0);
        Result newResult = createSubmissionAndResult(studentParticipation, 100, true);
        savedStudentScores = studentScoreRepository.findAll();
        assertThat(savedStudentScores).isNotEmpty();
        assertThat(savedStudentScores).size().isEqualTo(1);
        StudentScore updatedStudentScore = savedStudentScores.get(0);
        assertStudentScoreStructure(updatedStudentScore, idOfTextExercise, student1.getId(), newResult.getId(), newResult.getScore(), newResult.getId(), newResult.getScore());
        verify(this.studentScoreService, times(2)).updateStudentScores(any(Result.class));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void resultListenerPostPersist_secondResultForStudentAndExerciseButNOTRated_shouldUpdateExistingStudentScoreButOnlyNotRatedPart() {
        List<StudentScore> savedStudentScores = studentScoreRepository.findAll();
        assertThat(savedStudentScores).isEmpty();
        User student1 = userRepository.findOneByLogin("student1").get();
        // saving the rated result should trigger the entity listener and create a new student score
        Result originalResult = createParticipationSubmissionAndResult(idOfTextExercise, student1, 10.0, 10.0, 200, true);
        savedStudentScores = studentScoreRepository.findAll();
        assertThat(savedStudentScores).isNotEmpty();
        assertThat(savedStudentScores).size().isEqualTo(1);
        StudentScore savedStudentScore = savedStudentScores.get(0);
        assertStudentScoreStructure(savedStudentScore, idOfTextExercise, student1.getId(), originalResult.getId(), originalResult.getScore(), originalResult.getId(),
                originalResult.getScore());
        // creating a new rated result should trigger the entity listener and update the student score BUT only the not rated part
        StudentParticipation studentParticipation = studentParticipationRepository.findByExerciseIdAndStudentId(idOfTextExercise, student1.getId()).get(0);
        Result newResult = createSubmissionAndResult(studentParticipation, 100, false);
        savedStudentScores = studentScoreRepository.findAll();
        assertThat(savedStudentScores).isNotEmpty();
        assertThat(savedStudentScores).size().isEqualTo(1);
        StudentScore updatedStudentScore = savedStudentScores.get(0);
        assertStudentScoreStructure(updatedStudentScore, idOfTextExercise, student1.getId(), newResult.getId(), newResult.getScore(), originalResult.getId(),
                originalResult.getScore());
        verify(this.studentScoreService, times(2)).updateStudentScores(any(Result.class));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void resultListenerPreRemove_removeResult_shouldRemoveStudentScoreBeforeRemovingResult() {
        List<StudentScore> savedStudentScores = studentScoreRepository.findAll();
        assertThat(savedStudentScores).isEmpty();
        User student1 = userRepository.findOneByLogin("student1").get();
        Result persistedResult = createParticipationSubmissionAndResult(idOfTextExercise, student1, 10.0, 10.0, 200, true);
        savedStudentScores = studentScoreRepository.findAll();
        assertThat(savedStudentScores).isNotEmpty();
        assertThat(savedStudentScores).size().isEqualTo(1);
        StudentScore savedStudentScore = savedStudentScores.get(0);
        assertStudentScoreStructure(savedStudentScore, idOfTextExercise, student1.getId(), persistedResult.getId(), persistedResult.getScore(), persistedResult.getId(),
                persistedResult.getScore());
        // removing the result should trigger the entity listener and remove the associated student score
        resultRepository.deleteById(persistedResult.getId());
        savedStudentScores = studentScoreRepository.findAll();
        List<Result> savedResults = resultRepository.findAll();
        assertThat(savedStudentScores).isEmpty();
        assertThat(savedResults).isEmpty();
        verify(this.studentScoreService, times(1)).removeAssociatedStudentScores(any(Result.class));
    }

    private void assertStudentScoreStructure(StudentScore studentScore, Long expectedExerciseId, Long expectedStudentId, Long expectedLastResultId, Long expectedLastScore,
            Long expectedLastRatedResultId, Long expectedLastRatedScore) {
        assertThat(studentScore.getExercise().getId()).isEqualTo(expectedExerciseId);
        assertThat(studentScore.getUser().getId()).isEqualTo(expectedStudentId);
        if (expectedLastResultId == null) {
            assertThat(studentScore.getLastScore()).isNull();
        }
        else {
            assertThat(studentScore.getLastResult().getId()).isEqualTo(expectedLastResultId);
        }
        assertThat(studentScore.getLastScore()).isEqualTo(expectedLastScore);

        if (expectedLastRatedResultId == null) {
            assertThat(studentScore.getLastRatedScore()).isNull();
        }
        else {
            assertThat(studentScore.getLastRatedResult().getId()).isEqualTo(expectedLastRatedResultId);
        }
        assertThat(studentScore.getLastRatedScore()).isEqualTo(expectedLastRatedScore);
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

    private Result createParticipationSubmissionAndResult(Long idOfExercise, Participant participant, Double pointsOfExercise, Double bonusPointsOfExercise, long scoreAwarded,
            boolean rated) {
        Exercise exercise = exerciseRepository.findById(idOfExercise).get();

        if (!exercise.getMaxScore().equals(pointsOfExercise)) {
            exercise.setMaxScore(pointsOfExercise);
        }
        if (!exercise.getBonusPoints().equals(bonusPointsOfExercise)) {
            exercise.setBonusPoints(bonusPointsOfExercise);
        }
        exercise = exerciseRepository.saveAndFlush(exercise);

        StudentParticipation studentParticipation = participationService.startExercise(exercise, participant, false);

        return createSubmissionAndResult(studentParticipation, scoreAwarded, rated);
    }

    private Result createSubmissionAndResult(StudentParticipation studentParticipation, long scoreAwarded, boolean rated) {
        Exercise exercise = studentParticipation.getExercise();
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
        submission = submissionRepository.saveAndFlush(submission);

        Result result = ModelFactory.generateResult(rated, scoreAwarded);
        result.setParticipation(studentParticipation);
        result.setSubmission(submission);
        return resultRepository.saveAndFlush(result);
    }

}
