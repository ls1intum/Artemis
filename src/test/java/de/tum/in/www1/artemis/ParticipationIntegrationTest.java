package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
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

    @BeforeEach
    public void initTestCase() {
        database.addUsers(2, 2, 2);
        database.addCourseWithModelingAndTextExercise();
        course = courseRepo.findAll().get(0);
        modelingExercise = (ModelingExercise) exerciseRepo.findAll().get(0);
        textExercise = (TextExercise) exerciseRepo.findAll().get(1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
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
    public void deleteParticipation_forbidden_student() throws Exception {
        request.delete("/api/participations/" + 1, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void deleteParticipation_forbidden_tutor() throws Exception {
        request.delete("/api/participations/" + 1, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteParticipation_notFound() throws Exception {
        request.delete("/api/participations/" + 1, HttpStatus.NOT_FOUND);
    }
}
