package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class ParticipationIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    StudentParticipationRepository participationRepo;

    @Autowired
    SubmissionRepository submissionRepository;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    UserRepository userRepo;

    @Autowired
    RequestUtilService request;

    @Autowired
    DatabaseUtilService database;

    private Course course;

    private ModelingExercise modelingExercise;

    private TextExercise textExercise;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(2, 2, 2);

        // Add users that are not in the course/exercise
        userRepo.save(ModelFactory.generateActivatedUser("student3"));
        userRepo.save(ModelFactory.generateActivatedUser("tutor3"));
        userRepo.save(ModelFactory.generateActivatedUser("instructor3"));

        course = database.addCourseWithModelingAndTextExercise();
        for (Exercise exercise : course.getExercises()) {
            if (exercise instanceof ModelingExercise) {
                modelingExercise = (ModelingExercise) exercise;
            }
            if (exercise instanceof TextExercise) {
                textExercise = (TextExercise) exercise;
            }
        }
        modelingExercise.setTitle("UML Class Diagram");
        exerciseRepo.save(modelingExercise);

        programmingExercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), course);
        programmingExercise = exerciseRepo.save(programmingExercise);
        course.addExercises(programmingExercise);
        course = courseRepo.save(course);

        doReturn("Success").when(continuousIntegrationService).copyBuildPlan(any(), any(), any(), any(), any());
        doNothing().when(continuousIntegrationService).configureBuildPlan(any());
        doNothing().when(continuousIntegrationService).performEmptySetupCommit(any());
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
        Feature.PROGRAMMING_EXERCISES.enable();
    }

    @Test
    @WithMockUser(username = "student1")
    public void participateInModelingExercise() throws Exception {
        URI location = request.post("/api/courses/" + course.getId() + "/exercises/" + modelingExercise.getId() + "/participations", null, HttpStatus.CREATED);

        StudentParticipation participation = request.get(location.getPath(), HttpStatus.OK, StudentParticipation.class);
        assertThat(participation.getExercise()).as("participated in correct exercise").isEqualTo(modelingExercise);
        assertThat(participation.getStudent()).as("Student got set").isNotNull();
        assertThat(participation.getStudent().getLogin()).as("Correct student got set").isEqualTo("student1");
        Participation storedParticipation = participationRepo.findWithEagerSubmissionsByExerciseIdAndStudentLogin(modelingExercise.getId(), "student1").get();
        assertThat(storedParticipation.getSubmissions().size()).as("submission was initialized").isEqualTo(1);
        assertThat(storedParticipation.getSubmissions().iterator().next().getClass()).as("submission is of type modeling submission").isEqualTo(ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = "student2")
    public void participateInTextExercise() throws Exception {
        URI location = request.post("/api/courses/" + course.getId() + "/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.CREATED);

        StudentParticipation participation = request.get(location.getPath(), HttpStatus.OK, StudentParticipation.class);
        assertThat(participation.getExercise()).as("participated in correct exercise").isEqualTo(textExercise);
        assertThat(participation.getStudent()).as("Student got set").isNotNull();
        assertThat(participation.getStudent().getLogin()).as("Correct student got set").isEqualTo("student2");
        Participation storedParticipation = participationRepo.findWithEagerSubmissionsByExerciseIdAndStudentLogin(textExercise.getId(), "student2").get();
        assertThat(storedParticipation.getSubmissions().size()).as("submission was initialized").isEqualTo(1);
        assertThat(storedParticipation.getSubmissions().iterator().next().getClass()).as("submission is of type text submission").isEqualTo(TextSubmission.class);
    }

    @Test
    @WithMockUser(username = "student1")
    public void participateTwiceInModelingExercise_sameParticipation() throws Exception {
        var participation1 = request.post("/api/courses/" + course.getId() + "/exercises/" + modelingExercise.getId() + "/participations", null, HttpStatus.CREATED);
        var participation2 = request.post("/api/courses/" + course.getId() + "/exercises/" + modelingExercise.getId() + "/participations", null, HttpStatus.CREATED);
        assertThat(participation1.equals(participation2));
    }

    @Test
    @WithMockUser(username = "student1")
    public void participateTwiceInTextExercise_sameParticipation() throws Exception {
        var participation1 = request.post("/api/courses/" + course.getId() + "/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.CREATED);
        var participation2 = request.post("/api/courses/" + course.getId() + "/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.CREATED);
        assertThat(participation1.equals(participation2));
    }

    @Test
    @WithMockUser(username = "student2")
    public void participateInTextExercise_releaseDateNotReached() throws Exception {
        textExercise.setReleaseDate(ZonedDateTime.now().plusHours(2));
        exerciseRepo.save(textExercise);
        request.post("/api/courses/" + course.getId() + "/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "student3")
    public void participateInTextExercise_notStudentInCourse() throws Exception {
        request.post("/api/courses/" + course.getId() + "/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "student1")
    public void participateInProgrammingExercise_featureDisabled() throws Exception {
        Feature.PROGRAMMING_EXERCISES.disable();
        request.post("/api/courses/" + course.getId() + "/exercises/" + programmingExercise.getId() + "/participations", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "student1")
    public void participateInProgrammingExercise_dueDatePassed() throws Exception {
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(2));
        exerciseRepo.save(programmingExercise);
        request.post("/api/courses/" + course.getId() + "/exercises/" + programmingExercise.getId() + "/participations", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteParticipation() throws Exception {
        Submission submissionWithResult = database.addSubmission(modelingExercise, new ModelingSubmission(), "student1");
        Submission submissionWithoutResult = database.addSubmission((StudentParticipation) submissionWithResult.getParticipation(), new ModelingSubmission(), "student1");
        Long participationId = submissionWithResult.getParticipation().getId();
        database.addResultToSubmission(submissionWithResult);

        // Participation should now exist.
        assertThat(participationRepo.existsById(participationId)).isTrue();
        // There should be a submission and result assigned to the participation.
        assertThat(submissionRepository.findByParticipationId(participationId)).hasSize(2);
        assertThat(resultRepository.findByParticipationIdOrderByCompletionDateDesc(participationId)).hasSize(1);

        request.delete("/api/participations/" + participationId, HttpStatus.OK);
        Optional<StudentParticipation> participation = participationRepo.findById(participationId);
        // Participation should now be gone.
        assertThat(participation.isPresent()).isFalse();
        // Make sure that also the submission and result were deleted.
        assertThat(submissionRepository.findByParticipationId(participationId)).hasSize(0);
        assertThat(resultRepository.findByParticipationIdOrderByCompletionDateDesc(participationId)).hasSize(0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteSubmissionWithoutResult() throws Exception {
        Submission submissionWithoutResult = database.addSubmission(modelingExercise, new ModelingSubmission(), "student1");
        database.addSubmission((StudentParticipation) submissionWithoutResult.getParticipation(), submissionWithoutResult, "student1");
        Long participationId = submissionWithoutResult.getParticipation().getId();

        // Participation should now exist.
        assertThat(participationRepo.existsById(participationId)).isTrue();

        // There should be a submission and no result assigned to the participation.
        assertThat(submissionRepository.findByParticipationId(participationId)).hasSize(1);
        assertThat(resultRepository.findByParticipationIdOrderByCompletionDateDesc(participationId)).hasSize(0);

        request.delete("/api/participations/" + participationId, HttpStatus.OK);
        Optional<StudentParticipation> participation = participationRepo.findById(participationId);
        // Participation should now be gone.
        assertThat(participation.isPresent()).isFalse();
        // Make sure that the submission is deleted.
        assertThat(submissionRepository.findByParticipationId(participationId)).hasSize(0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteResultWithoutSubmission() throws Exception {
        StudentParticipation studentParticipation = database.addParticipationForExercise(modelingExercise, "student1");
        database.addResultToParticipation(studentParticipation);
        Long participationId = studentParticipation.getId();

        // Participation should now exist.
        assertThat(participationRepo.existsById(participationId)).isTrue();

        // There should be a submission and no result assigned to the participation.
        assertThat(submissionRepository.findByParticipationId(participationId)).hasSize(0);
        assertThat(resultRepository.findByParticipationIdOrderByCompletionDateDesc(participationId)).hasSize(1);

        request.delete("/api/participations/" + participationId, HttpStatus.OK);
        Optional<StudentParticipation> participation = participationRepo.findById(participationId);
        // Participation should now be gone.
        assertThat(participation.isPresent()).isFalse();
        // Make sure that the result is deleted.
        assertThat(resultRepository.findByParticipationIdOrderByCompletionDateDesc(participationId)).hasSize(0);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void deleteParticipation_student() throws Exception {
        // Allow students to delete their own participation if it belongs to a guided tour
        StudentParticipation studentParticipation = database.addParticipationForExercise(modelingExercise, "student1");
        request.delete("/api/guided-tour/participations/" + studentParticipation.getId(), HttpStatus.OK);

        // Returns forbidden if users do not delete their own participation
        StudentParticipation studentParticipation2 = database.addParticipationForExercise(modelingExercise, "student2");
        request.delete("/api/guided-tour/participations/" + studentParticipation2.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void deleteParticipation_tutor() throws Exception {
        // Allow tutors to delete their own participation if it belongs to a guided tour
        StudentParticipation studentParticipation = database.addParticipationForExercise(modelingExercise, "tutor1");
        request.delete("/api/guided-tour/participations/" + studentParticipation.getId(), HttpStatus.OK);

        // Returns forbidden if tutors do not delete their own participation
        StudentParticipation studentParticipation2 = database.addParticipationForExercise(modelingExercise, "student1");
        request.delete("/api/guided-tour/participations/" + studentParticipation2.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteParticipation_notFound() throws Exception {
        request.delete("/api/participations/" + 100, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void resumeProgrammingExerciseParticipation() throws Exception {
        var participation = ModelFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INITIALIZED, programmingExercise, database.getUserByLogin("student1"));
        participationRepo.save(participation);
        request.putWithResponseBody("/api/courses/" + course.getId() + "/exercises/" + programmingExercise.getId() + "/resume-programming-participation", null,
                ProgrammingExerciseStudentParticipation.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void resumeProgrammingExerciseParticipation_wrongExerciseId() throws Exception {
        var participation = ModelFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INITIALIZED, programmingExercise, database.getUserByLogin("student1"));
        participationRepo.save(participation);
        request.putWithResponseBody("/api/courses/" + course.getId() + "/exercises/100/resume-programming-participation", null, ProgrammingExerciseStudentParticipation.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getAllParticipationsForExercise() throws Exception {
        database.addParticipationForExercise(textExercise, "student1");
        database.addParticipationForExercise(textExercise, "student2");
        var participations = request.getList("/api/exercise/" + textExercise.getId() + "/participations", HttpStatus.OK, StudentParticipation.class);
        assertThat(participations.size()).as("Exactly 2 participations are returned").isEqualTo(2);
        assertThat(participations.stream().allMatch(participation -> participation.getStudent() != null)).as("Only participation that has student are returned").isTrue();
        assertThat(participations.stream().allMatch(participation -> participation.getSubmissionCount() == 0)).as("No submissions should exist for participations").isTrue();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getAllParticipationsForExercise_withLatestResult() throws Exception {
        database.addParticipationForExercise(textExercise, "student1");
        var participation = database.addParticipationForExercise(textExercise, "student2");
        database.addResultToParticipation(participation);
        var result = ModelFactory.generateResult(true, 70).participation(participation);
        resultRepository.save(result);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("withLatestResult", "true");
        var participations = request.getList("/api/exercise/" + textExercise.getId() + "/participations", HttpStatus.OK, StudentParticipation.class, params);
        assertThat(participations.size()).as("Exactly 2 participations are returned").isEqualTo(2);
        assertThat(participations.stream().allMatch(p -> p.getStudent() != null)).as("Only participation that has student are returned").isTrue();
        assertThat(participations.stream().allMatch(p -> p.getSubmissionCount() == 0)).as("No submissions should exist for participations").isTrue();
        var participationWithResult = participations.stream().filter(p -> p.getStudent().equals(database.getUserByLogin("student2"))).findFirst().get();
        assertThat(participationWithResult.getResults().size()).isEqualTo(1);
        assertThat(participationWithResult.getResults().stream().findFirst().get()).isEqualTo(result);
    }

    @Test
    @WithMockUser(username = "tutor3", roles = "TA")
    public void getAllParticipationsForExercise_NotTutorInCourse() throws Exception {
        request.getList("/api/exercise/" + textExercise.getId() + "/participations", HttpStatus.FORBIDDEN, StudentParticipation.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getAllParticipationsForCourse() throws Exception {
        database.addParticipationForExercise(programmingExercise, "student1");
        database.addParticipationForExercise(textExercise, "student2");
        database.addParticipationForExercise(modelingExercise, "student1");
        var quizEx = ModelFactory.generateQuizExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), course);
        exerciseRepo.save(quizEx);
        database.addParticipationForExercise(quizEx, "student2");

        var participations = request.getList("/api/courses/" + course.getId() + "/participations", HttpStatus.OK, StudentParticipation.class);
        assertThat(participations.size()).isEqualTo(4);
        participations.forEach(participation -> {
            var exercise = participation.getExercise();
            assertThat(exercise.getCourse()).isNull();
            assertThat(exercise.getStudentParticipations()).isEmpty();
            assertThat(exercise.getTutorParticipations()).isEmpty();
            assertThat(exercise.getExampleSubmissions()).isEmpty();
            assertThat(exercise.getAttachments()).isEmpty();
            assertThat(exercise.getCategories()).isEmpty();
            assertThat(exercise.getProblemStatement()).isNull();
            assertThat(exercise.getStudentQuestions()).isEmpty();
            assertThat(exercise.getGradingInstructions()).isNull();
            assertThat(exercise.getDifficulty()).isNull();
            assertThat(exercise.getMode()).isNull();
            if (exercise instanceof ProgrammingExercise) {
                ProgrammingExercise programmingExercise = (ProgrammingExercise) exercise;
                assertThat(programmingExercise.getSolutionParticipation()).isNull();
                assertThat(programmingExercise.getTemplateParticipation()).isNull();
                assertThat(programmingExercise.getTestRepositoryUrl()).isNull();
                assertThat(programmingExercise.getShortName()).isNull();
                assertThat(programmingExercise.isPublishBuildPlanUrl()).isNull();
                assertThat(programmingExercise.getProgrammingLanguage()).isNull();
                assertThat(programmingExercise.getPackageName()).isNull();
                assertThat(programmingExercise.isAllowOnlineEditor()).isNull();
            }
            else if (exercise instanceof QuizExercise) {
                QuizExercise quizExercise = (QuizExercise) exercise;
                assertThat(quizExercise.getQuizQuestions()).isEmpty();
            }
            else if (exercise instanceof TextExercise) {
                TextExercise textExercise = (TextExercise) exercise;
                assertThat(textExercise.getSampleSolution()).isNull();
            }
            else if (exercise instanceof ModelingExercise) {
                ModelingExercise modelingExercise = (ModelingExercise) exercise;
                assertThat(modelingExercise.getSampleSolutionModel()).isNull();
                assertThat(modelingExercise.getSampleSolutionExplanation()).isNull();
            }
        });
    }

    @Test
    @WithMockUser(username = "instructor3", roles = "INSTRUCTOR")
    public void getAllParticipationsForCourse_noInstructorInCourse() throws Exception {
        request.getList("/api/courses/" + course.getId() + "/participations", HttpStatus.FORBIDDEN, StudentParticipation.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void updateParticipation() throws Exception {
        var participation = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise, database.getUserByLogin("student1"));
        participation.setPresentationScore(1);
        participation = participationRepo.save(participation);
        participation.setPresentationScore(null);
        var actualParticipation = request.putWithResponseBody("/api/participations", participation, StudentParticipation.class, HttpStatus.OK);
        assertThat(actualParticipation).as("The participation was updated").isNotNull();
        assertThat(actualParticipation.getPresentationScore()).as("Presentation score was set to 0").isEqualTo(0);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void updateParticipation_notStored() throws Exception {
        var participation = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise, database.getUserByLogin("student1"));
        request.putWithResponseBody("/api/participations", participation, StudentParticipation.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void updateParticipation_presentationScoreMoreThan0() throws Exception {
        var participation = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise, database.getUserByLogin("student1"));
        participation = participationRepo.save(participation);
        participation.setPresentationScore(2);
        var actualParticipation = request.putWithResponseBody("/api/participations", participation, StudentParticipation.class, HttpStatus.OK);
        assertThat(actualParticipation).as("The participation was updated").isNotNull();
        assertThat(actualParticipation.getPresentationScore()).as("Presentation score was set to 1").isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "tutor3", roles = "TA")
    public void updateParticipation_notTutorInCourse() throws Exception {
        var participation = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise, database.getUserByLogin("student1"));
        request.putWithResponseBody("/api/participations", participation, StudentParticipation.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getParticipationWithLatestResult() throws Exception {
        var participation = database.addParticipationForExercise(textExercise, "student1");
        database.addResultToParticipation(participation);
        var result = ModelFactory.generateResult(true, 70);
        result.participation(participation).setCompletionDate(ZonedDateTime.now().minusHours(2));
        resultRepository.save(result);
        var actualParticipation = request.get("/api/participations/" + participation.getId() + "/withLatestResult", HttpStatus.OK, StudentParticipation.class);

        assertThat(actualParticipation).isNotNull();
        assertThat(actualParticipation.getResults().size()).isEqualTo(1);
        assertThat(actualParticipation.getResults().iterator().next()).as("Only latest result is returned").isEqualTo(result);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getParticipationBuildArtifact() throws Exception {
        var participation = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        doReturn(new ResponseEntity<>(null, HttpStatus.OK)).when(continuousIntegrationService).retrieveLatestArtifact(participation);
        request.getNullable("/api/participations/" + participation.getId() + "/buildArtifact", HttpStatus.OK, Object.class);
        verify(continuousIntegrationService).retrieveLatestArtifact(participation);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getSubmissionOfParticipation() throws Exception {
        var participation = database.addParticipationForExercise(textExercise, "student1");
        var submission1 = database.addSubmission(participation, ModelFactory.generateTextSubmission("text", Language.ENGLISH, true), "student1");
        var submission2 = database.addSubmission(participation, ModelFactory.generateTextSubmission("text2", Language.ENGLISH, true), "student1");
        var submissions = request.getList("/api/participations/" + participation.getId() + "/submissions", HttpStatus.OK, Submission.class);
        assertThat(submissions).contains(submission1, submission2);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void cleanupBuildPlan() throws Exception {
        var participation = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        var actualParticipation = request.putWithResponseBody("/api/participations/" + participation.getId() + "/cleanupBuildPlan", null, Participation.class, HttpStatus.OK);
        assertThat(actualParticipation).isEqualTo(participation);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getParticipation() throws Exception {
        var participation = database.addParticipationForExercise(textExercise, "student1");
        var actualParticipation = request.get("/api/exercises/" + textExercise.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getParticipation_quizExerciseNotStarted() throws Exception {
        var quizEx = ModelFactory.generateQuizExercise(ZonedDateTime.now().plusHours(2), ZonedDateTime.now().plusDays(1), course).isPlannedToStart(false);
        quizEx = exerciseRepo.save(quizEx);
        var actualParticipation = request.get("/api/exercises/" + quizEx.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation.getExercise()).isEqualTo(quizEx);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getParticipation_quizExerciseStartedAndNoParticipation() throws Exception {
        var quizEx = ModelFactory.generateQuizExercise(ZonedDateTime.now().minusMinutes(2), ZonedDateTime.now().plusMinutes(10), course).isPlannedToStart(true);
        quizEx = exerciseRepo.save(quizEx);
        request.getNullable("/api/exercises/" + quizEx.getId() + "/participation", HttpStatus.NO_CONTENT, StudentParticipation.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getParticipation_quizExerciseStartedAndSubmissionAllowed() throws Exception {
        var quizEx = ModelFactory.generateQuizExercise(ZonedDateTime.now().minusMinutes(1), ZonedDateTime.now().plusMinutes(5), course).isPlannedToStart(true).duration(360);
        quizEx = exerciseRepo.save(quizEx);
        var participation = request.get("/api/exercises/" + quizEx.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(participation.getExercise()).as("Participation contains exercise").isEqualTo(quizEx);
        assertThat(participation.getResults().size()).as("New result was added to the participation").isEqualTo(1);
        assertThat(participation.getInitializationState()).as("Participation was initialized").isEqualTo(InitializationState.INITIALIZED);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getParticipation_quizExerciseFinished() throws Exception {
        var quizEx = ModelFactory.generateQuizExercise(ZonedDateTime.now().minusMinutes(20), ZonedDateTime.now().minusMinutes(20), course).isPlannedToStart(true);
        quizEx = exerciseRepo.save(quizEx);
        var participation = database.addParticipationForExercise(quizEx, "student1");
        var submission = database.addSubmission(participation, new QuizSubmission().scoreInPoints(11D).submitted(true), "student1");
        database.addResultToParticipation(participation, submission);
        var actualParticipation = request.get("/api/exercises/" + quizEx.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getParticipation_noParticipation() throws Exception {
        request.get("/api/exercises/" + textExercise.getId() + "/participation", HttpStatus.FAILED_DEPENDENCY, StudentParticipation.class);
    }

    @Test
    @WithMockUser(username = "student3", roles = "USER")
    public void getParticipation_notStudentInCourse() throws Exception {
        request.get("/api/exercises/" + textExercise.getId() + "/participation", HttpStatus.FORBIDDEN, StudentParticipation.class);
    }
}
