package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
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
    @WithMockUser(username = "tutor1", roles = "TA")
    public void createParticipation() throws Exception {
        var participation = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise, database.getUserByLogin("student1"));
        var actualParticipation = request.postWithResponseBody("/api/participations", participation, Participation.class, HttpStatus.CREATED);
        var expectedParticipation = participationRepo.findById(actualParticipation.getId()).get();
        assertThat(actualParticipation).isEqualTo(expectedParticipation);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void createParticipation_idExists() throws Exception {
        var participation = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise, database.getUserByLogin("student1"));
        participation.setId(1L);
        request.postWithResponseBody("/api/participations", participation, Participation.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void createParticipation_programmingExercisesFeatureDisabled() throws Exception {
        var programmingExercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now(), ZonedDateTime.now(), course);
        exerciseRepo.save(programmingExercise);
        var participation = ModelFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INITIALIZED, programmingExercise, database.getUserByLogin("student1"));
        participation.setId(1L);
        Feature.PROGRAMMING_EXERCISES.disable();
        request.postWithResponseBody("/api/participations", participation, Participation.class, HttpStatus.FORBIDDEN);
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
    @WithMockUser(username = "student1", roles = "USER")
    public void resumeProgrammingExerciseParticipation_noParticipation() throws Exception {
        var participation = ModelFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INITIALIZED, programmingExercise, database.getUserByLogin("student1"));
        participation.setExercise(textExercise);
        participationRepo.save(participation);
        request.putWithResponseBody("/api/courses/" + course.getId() + "/exercises/" + textExercise.getId() + "/resume-programming-participation", null,
                ProgrammingExerciseStudentParticipation.class, HttpStatus.BAD_REQUEST);
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
        var result = ModelFactory.generateResult(true, 70);
        result = database.addResultToParticipation(participation);
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
}
