package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
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
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggleService;
import de.tum.in.www1.artemis.util.ModelFactory;

public class ParticipationIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private StudentParticipationRepository participationRepo;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private FeatureToggleService featureToggleService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private ParticipationService participationService;

    private Course course;

    private ModelingExercise modelingExercise;

    private TextExercise textExercise;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(2, 2, 0, 2);

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

        doReturn("Success").when(continuousIntegrationService).copyBuildPlan(any(), any(), any(), any(), any(), anyBoolean());
        doNothing().when(continuousIntegrationService).configureBuildPlan(any());
        doNothing().when(continuousIntegrationService).performEmptySetupCommit(any());
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
        featureToggleService.enableFeature(Feature.PROGRAMMING_EXERCISES);
    }

    @Test
    @WithMockUser(username = "student1")
    public void participateInModelingExercise() throws Exception {
        URI location = request.post("/api/exercises/" + modelingExercise.getId() + "/participations", null, HttpStatus.CREATED);

        StudentParticipation participation = request.get(location.getPath(), HttpStatus.OK, StudentParticipation.class);
        assertThat(participation.getExercise()).as("participated in correct exercise").isEqualTo(modelingExercise);
        assertThat(participation.getStudent()).as("Student got set").isNotNull();
        assertThat(participation.getParticipantIdentifier()).as("Correct student got set").isEqualTo("student1");
        Participation storedParticipation = participationRepo.findWithEagerLegalSubmissionsByExerciseIdAndStudentLogin(modelingExercise.getId(), "student1").get();
        assertThat(storedParticipation.getSubmissions().size()).as("submission was initialized").isEqualTo(1);
        assertThat(storedParticipation.getSubmissions().iterator().next().getClass()).as("submission is of type modeling submission").isEqualTo(ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = "student2")
    public void participateInTextExercise() throws Exception {
        URI location = request.post("/api/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.CREATED);

        StudentParticipation participation = request.get(location.getPath(), HttpStatus.OK, StudentParticipation.class);
        assertThat(participation.getExercise()).as("participated in correct exercise").isEqualTo(textExercise);
        assertThat(participation.getStudent()).as("Student got set").isNotNull();
        assertThat(participation.getParticipantIdentifier()).as("Correct student got set").isEqualTo("student2");
        Participation storedParticipation = participationRepo.findWithEagerLegalSubmissionsByExerciseIdAndStudentLogin(textExercise.getId(), "student2").get();
        assertThat(storedParticipation.getSubmissions().size()).as("submission was initialized").isEqualTo(1);
        assertThat(storedParticipation.getSubmissions().iterator().next().getClass()).as("submission is of type text submission").isEqualTo(TextSubmission.class);
    }

    @Test
    @WithMockUser(username = "student1")
    public void participateTwiceInModelingExercise_sameParticipation() throws Exception {
        var participation1 = request.post("/api/exercises/" + modelingExercise.getId() + "/participations", null, HttpStatus.CREATED);
        var participation2 = request.post("/api/exercises/" + modelingExercise.getId() + "/participations", null, HttpStatus.CREATED);
        assertThat(participation1).isEqualTo(participation2);
    }

    @Test
    @WithMockUser(username = "student1")
    public void participateTwiceInTextExercise_sameParticipation() throws Exception {
        var participation1 = request.post("/api/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.CREATED);
        var participation2 = request.post("/api/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.CREATED);
        assertThat(participation1).isEqualTo(participation2);
    }

    @Test
    @WithMockUser(username = "student2")
    public void participateInTextExercise_releaseDateNotReached() throws Exception {
        textExercise.setReleaseDate(ZonedDateTime.now().plusHours(2));
        exerciseRepo.save(textExercise);
        request.post("/api/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "student3")
    public void participateInTextExercise_notStudentInCourse() throws Exception {
        request.post("/api/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "student1")
    public void participateInProgrammingExercise_featureDisabled() throws Exception {
        featureToggleService.disableFeature(Feature.PROGRAMMING_EXERCISES);
        request.post("/api/exercises/" + programmingExercise.getId() + "/participations", null, HttpStatus.FORBIDDEN);

        // Reset
        featureToggleService.enableFeature(Feature.PROGRAMMING_EXERCISES);
    }

    @Test
    @WithMockUser(username = "student1")
    public void participateInProgrammingExercise_dueDatePassed() throws Exception {
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(2));
        exerciseRepo.save(programmingExercise);
        request.post("/api/exercises/" + programmingExercise.getId() + "/participations", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "student1")
    public void participateInProgrammingTeamExercise_withoutAssignedTeam() throws Exception {
        programmingExercise.setMode(ExerciseMode.TEAM);
        exerciseRepo.save(programmingExercise);
        request.post("/api/exercises/" + programmingExercise.getId() + "/participations", null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteParticipation() throws Exception {
        Submission submissionWithResult = database.addSubmission(modelingExercise, new ModelingSubmission(), "student1");
        database.addSubmission((StudentParticipation) submissionWithResult.getParticipation(), new ModelingSubmission());
        Long participationId = submissionWithResult.getParticipation().getId();
        database.addResultToSubmission(submissionWithResult, null);

        // Participation should now exist.
        assertThat(participationRepo.existsById(participationId)).isTrue();
        // There should be a submission and result assigned to the participation.
        assertThat(submissionRepository.findAllByParticipationId(participationId)).hasSize(2);
        assertThat(resultRepository.findByParticipationIdOrderByCompletionDateDesc(participationId)).hasSize(1);

        request.delete("/api/participations/" + participationId, HttpStatus.OK);
        Optional<StudentParticipation> participation = participationRepo.findById(participationId);
        // Participation should now be gone.
        assertThat(participation.isPresent()).isFalse();
        // Make sure that also the submission and result were deleted.
        assertThat(submissionRepository.findAllByParticipationId(participationId)).hasSize(0);
        assertThat(resultRepository.findByParticipationIdOrderByCompletionDateDesc(participationId)).hasSize(0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteSubmissionWithoutResult() throws Exception {
        Submission submissionWithoutResult = database.addSubmission(modelingExercise, new ModelingSubmission(), "student1");
        database.addSubmission((StudentParticipation) submissionWithoutResult.getParticipation(), submissionWithoutResult);
        Long participationId = submissionWithoutResult.getParticipation().getId();

        // Participation should now exist.
        assertThat(participationRepo.existsById(participationId)).isTrue();

        // There should be a submission and no result assigned to the participation.
        assertThat(submissionRepository.findAllByParticipationId(participationId)).hasSize(1);
        assertThat(resultRepository.findByParticipationIdOrderByCompletionDateDesc(participationId)).hasSize(0);

        request.delete("/api/participations/" + participationId, HttpStatus.OK);
        Optional<StudentParticipation> participation = participationRepo.findById(participationId);
        // Participation should now be gone.
        assertThat(participation.isPresent()).isFalse();
        // Make sure that the submission is deleted.
        assertThat(submissionRepository.findAllByParticipationId(participationId)).hasSize(0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteResultWithoutSubmission() throws Exception {
        StudentParticipation studentParticipation = database.createAndSaveParticipationForExercise(modelingExercise, "student1");
        database.addResultToParticipation(null, null, studentParticipation);
        Long participationId = studentParticipation.getId();

        // Participation should now exist.
        assertThat(participationRepo.existsById(participationId)).isTrue();

        // There should be a submission and no result assigned to the participation.
        assertThat(submissionRepository.findAllByParticipationId(participationId)).hasSize(0);
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
        StudentParticipation studentParticipation = database.createAndSaveParticipationForExercise(modelingExercise, "student1");
        request.delete("/api/guided-tour/participations/" + studentParticipation.getId(), HttpStatus.OK);

        // Returns forbidden if users do not delete their own participation
        StudentParticipation studentParticipation2 = database.createAndSaveParticipationForExercise(modelingExercise, "student2");
        request.delete("/api/guided-tour/participations/" + studentParticipation2.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void deleteParticipation_tutor() throws Exception {
        // Allow tutors to delete their own participation if it belongs to a guided tour
        StudentParticipation studentParticipation = database.createAndSaveParticipationForExercise(modelingExercise, "tutor1");
        request.delete("/api/guided-tour/participations/" + studentParticipation.getId(), HttpStatus.OK);

        // Returns forbidden if tutors do not delete their own participation
        StudentParticipation studentParticipation2 = database.createAndSaveParticipationForExercise(modelingExercise, "student1");
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
        var participation = ModelFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INACTIVE, programmingExercise, database.getUserByLogin("student1"));
        participationRepo.save(participation);
        var updatedParticipation = request.putWithResponseBody("/api/exercises/" + programmingExercise.getId() + "/resume-programming-participation", null,
                ProgrammingExerciseStudentParticipation.class, HttpStatus.OK);
        assertThat(updatedParticipation.getInitializationState()).isEqualTo(InitializationState.INITIALIZED);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void resumeProgrammingExerciseParticipation_wrongExerciseId() throws Exception {
        var participation = ModelFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INITIALIZED, programmingExercise, database.getUserByLogin("student1"));
        participationRepo.save(participation);
        request.putWithResponseBody("/api/exercises/10000/resume-programming-participation", null, ProgrammingExerciseStudentParticipation.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void resumeProgrammingExerciseParticipation_forbidden() throws Exception {
        var exercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusDays(1), course);
        exercise = exerciseRepo.save(exercise);
        var participation = ModelFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INACTIVE, exercise, database.getUserByLogin("student1"));
        participationRepo.save(participation);
        request.putWithResponseBody("/api/exercises/" + exercise.getId() + "/resume-programming-participation", null, ProgrammingExerciseStudentParticipation.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getAllParticipationsForExercise() throws Exception {
        database.createAndSaveParticipationForExercise(textExercise, "student1");
        database.createAndSaveParticipationForExercise(textExercise, "student2");
        var participations = request.getList("/api/exercises/" + textExercise.getId() + "/participations", HttpStatus.OK, StudentParticipation.class);
        assertThat(participations.size()).as("Exactly 2 participations are returned").isEqualTo(2);
        assertThat(participations.stream().allMatch(participation -> participation.getStudent().isPresent())).as("Only participation that has student are returned").isTrue();
        assertThat(participations.stream().allMatch(participation -> participation.getSubmissionCount() == 0)).as("No submissions should exist for participations").isTrue();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getAllParticipationsForExercise_withLatestResult() throws Exception {
        database.createAndSaveParticipationForExercise(textExercise, "student1");
        var participation = database.createAndSaveParticipationForExercise(textExercise, "student2");
        database.addResultToParticipation(null, null, participation);
        var result = ModelFactory.generateResult(true, 70D).participation(participation);
        resultRepository.save(result);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("withLatestResult", "true");
        var participations = request.getList("/api/exercises/" + textExercise.getId() + "/participations", HttpStatus.OK, StudentParticipation.class, params);
        assertThat(participations.size()).as("Exactly 2 participations are returned").isEqualTo(2);
        assertThat(participations.stream().allMatch(p -> p.getStudent().isPresent())).as("Only participation that has student are returned").isTrue();
        assertThat(participations.stream().allMatch(p -> p.getSubmissionCount() == 0)).as("No submissions should exist for participations").isTrue();
        var participationWithResult = participations.stream().filter(p -> p.getParticipant().equals(database.getUserByLogin("student2"))).findFirst().get();
        assertThat(participationWithResult.getResults().size()).isEqualTo(1);
        assertThat(participationWithResult.getResults().stream().findFirst().get()).isEqualTo(result);
    }

    @Test
    @WithMockUser(username = "tutor3", roles = "TA")
    public void getAllParticipationsForExercise_NotTutorInCourse() throws Exception {
        request.getList("/api/exercises/" + textExercise.getId() + "/participations", HttpStatus.FORBIDDEN, StudentParticipation.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getAllParticipationsForCourse() throws Exception {
        database.createAndSaveParticipationForExercise(programmingExercise, "student1");
        database.createAndSaveParticipationForExercise(textExercise, "student2");
        database.createAndSaveParticipationForExercise(modelingExercise, "student1");
        var quizEx = ModelFactory.generateQuizExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), course);
        exerciseRepo.save(quizEx);
        database.createAndSaveParticipationForExercise(quizEx, "student2");

        var participations = request.getList("/api/courses/" + course.getId() + "/participations", HttpStatus.OK, StudentParticipation.class);
        assertThat(participations.size()).isEqualTo(4);
        participations.forEach(participation -> {
            var exercise = participation.getExercise();
            assertThat(exercise.getCourseViaExerciseGroupOrCourseMember()).isNull();
            assertThat(exercise.getStudentParticipations()).isEmpty();
            assertThat(exercise.getTutorParticipations()).isEmpty();
            assertThat(exercise.getExampleSubmissions()).isEmpty();
            assertThat(exercise.getAttachments()).isEmpty();
            assertThat(exercise.getCategories()).isEmpty();
            assertThat(exercise.getProblemStatement()).isNull();
            assertThat(exercise.getPosts()).isEmpty();
            assertThat(exercise.getGradingInstructions()).isNull();
            assertThat(exercise.getDifficulty()).isNull();
            assertThat(exercise.getMode()).isNull();
            if (exercise instanceof ProgrammingExercise programmingExercise) {
                assertThat(programmingExercise.getSolutionParticipation()).isNull();
                assertThat(programmingExercise.getTemplateParticipation()).isNull();
                assertThat(programmingExercise.getTestRepositoryUrl()).isNull();
                assertThat(programmingExercise.getShortName()).isNull();
                assertThat(programmingExercise.isPublishBuildPlanUrl()).isNull();
                assertThat(programmingExercise.getProgrammingLanguage()).isNull();
                assertThat(programmingExercise.getPackageName()).isNull();
                assertThat(programmingExercise.isAllowOnlineEditor()).isNull();
            }
            else if (exercise instanceof QuizExercise quizExercise) {
                assertThat(quizExercise.getQuizQuestions()).isEmpty();
            }
            else if (exercise instanceof TextExercise textExercise) {
                assertThat(textExercise.getSampleSolution()).isNull();
            }
            else if (exercise instanceof ModelingExercise modelingExercise) {
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
        var actualParticipation = request.putWithResponseBody("/api/exercises/" + textExercise.getId() + "/participations", participation, StudentParticipation.class,
                HttpStatus.OK);
        assertThat(actualParticipation).as("The participation was updated").isNotNull();
        assertThat(actualParticipation.getPresentationScore()).as("Presentation score was set to 0").isEqualTo(0);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void updateParticipation_notStored() throws Exception {
        var participation = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise, database.getUserByLogin("student1"));
        request.putWithResponseBody("/api/exercises/" + textExercise.getId() + "/participations", participation, StudentParticipation.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void updateParticipation_presentationScoreMoreThan0() throws Exception {
        var participation = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise, database.getUserByLogin("student1"));
        participation = participationRepo.save(participation);
        participation.setPresentationScore(2);
        var actualParticipation = request.putWithResponseBody("/api/exercises/" + textExercise.getId() + "/participations", participation, StudentParticipation.class,
                HttpStatus.OK);
        assertThat(actualParticipation).as("The participation was updated").isNotNull();
        assertThat(actualParticipation.getPresentationScore()).as("Presentation score was set to 1").isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "tutor3", roles = "TA")
    public void updateParticipation_notTutorInCourse() throws Exception {
        var participation = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise, database.getUserByLogin("student1"));
        participation = participationRepo.save(participation);
        request.putWithResponseBody("/api/exercises/" + textExercise.getId() + "/participations", participation, StudentParticipation.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateIndividualDueDateExamExercise() throws Exception {
        final FileUploadExercise exercise = database.addCourseExamExerciseGroupWithOneFileUploadExercise();
        StudentParticipation participation = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, exercise, database.getUserByLogin("student1"));
        participation = participationRepo.save(participation);
        participation.setIndividualDueDate(ZonedDateTime.now().plusDays(3));

        final var participationsToUpdate = new StudentParticipationList(participation);
        request.putAndExpectError(String.format("/api/exercises/%d/participations/update-individual-due-date", exercise.getId()), participationsToUpdate, HttpStatus.BAD_REQUEST,
                "examexercise");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateIndividualDueDateQuizExercise() throws Exception {
        final Course course = database.addCourseWithOneQuizExercise();
        final QuizExercise exercise = (QuizExercise) course.getExercises().stream().findFirst().get();
        StudentParticipation participation = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, exercise, database.getUserByLogin("student1"));
        participation = participationRepo.save(participation);
        participation.setIndividualDueDate(ZonedDateTime.now().plusDays(3));

        final var participationsToUpdate = new StudentParticipationList(participation);
        request.putAndExpectError(String.format("/api/exercises/%d/participations/update-individual-due-date", exercise.getId()), participationsToUpdate, HttpStatus.BAD_REQUEST,
                "quizexercise");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateIndividualDueDateOk() throws Exception {
        final var course = database.addCourseWithFileUploadExercise();
        var exercise = (FileUploadExercise) course.getExercises().stream().findAny().get();
        exercise.setDueDate(ZonedDateTime.now().plusHours(2));
        exercise = exerciseRepo.save(exercise);

        var submission = database.addFileUploadSubmission(exercise, ModelFactory.generateFileUploadSubmission(true), "student1");
        submission.getParticipation().setIndividualDueDate(ZonedDateTime.now().plusDays(1));

        final var participationsToUpdate = new StudentParticipationList((StudentParticipation) submission.getParticipation());
        final var response = request.putWithResponseBodyList(String.format("/api/exercises/%d/participations/update-individual-due-date", exercise.getId()), participationsToUpdate,
                StudentParticipation.class, HttpStatus.OK);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getIndividualDueDate()).isEqualToIgnoringNanos(submission.getParticipation().getIndividualDueDate());

        verify(programmingExerciseScheduleService, never()).updateScheduling(any());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateIndividualDueDateProgrammingExercise() throws Exception {
        final var course = database.addCourseWithOneProgrammingExercise();
        var exercise = (ProgrammingExercise) course.getExercises().stream().findAny().get();
        exercise.setDueDate(ZonedDateTime.now().plusHours(2));
        exercise = exerciseRepo.save(exercise);

        final var participation = database.addStudentParticipationForProgrammingExercise(exercise, "student1");
        participation.setIndividualDueDate(ZonedDateTime.now().plusHours(20));

        // due date before exercise due date ⇒ should be ignored
        final var participation2 = database.addStudentParticipationForProgrammingExercise(exercise, "student2");
        participation2.setIndividualDueDate(ZonedDateTime.now().plusHours(1));

        doNothing().when(programmingExerciseParticipationService).unlockStudentRepository(exercise, participation);

        final var participationsToUpdate = new StudentParticipationList(participation, participation2);
        final var response = request.putWithResponseBodyList(String.format("/api/exercises/%d/participations/update-individual-due-date", exercise.getId()), participationsToUpdate,
                StudentParticipation.class, HttpStatus.OK);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getIndividualDueDate()).isEqualToIgnoringNanos(participation.getIndividualDueDate());

        verify(programmingExerciseScheduleService, times(1)).updateScheduling(exercise);
        verify(programmingExerciseParticipationService, times(1)).unlockStudentRepository(exercise, participation);
        verify(programmingExerciseParticipationService, never()).unlockStudentRepository(exercise, participation2);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateIndividualDueDateUnchanged() throws Exception {
        final var course = database.addCourseWithOneProgrammingExercise();
        var exercise = (ProgrammingExercise) course.getExercises().stream().findAny().get();
        exercise.setDueDate(ZonedDateTime.now().plusHours(2));
        exercise = exerciseRepo.save(exercise);

        final var participation = database.addStudentParticipationForProgrammingExercise(exercise, "student1");
        final var participationsToUpdate = new StudentParticipationList(participation);
        final var response = request.putWithResponseBodyList(String.format("/api/exercises/%d/participations/update-individual-due-date", exercise.getId()), participationsToUpdate,
                StudentParticipation.class, HttpStatus.OK);

        assertThat(response).isEmpty();
        verify(programmingExerciseScheduleService, never()).updateScheduling(exercise);
        verify(programmingExerciseParticipationService, never()).unlockStudentRepository(exercise, participation);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateIndividualDueDateNoExerciseDueDate() throws Exception {
        final var course = database.addCourseWithOneProgrammingExercise();
        var exercise = (ProgrammingExercise) course.getExercises().stream().findAny().get();
        exercise.setDueDate(null);
        exercise = exerciseRepo.save(exercise);

        var participation = database.addStudentParticipationForProgrammingExercise(exercise, "student1");
        participation.setIndividualDueDate(ZonedDateTime.now().plusHours(4));

        final var participationsToUpdate = new StudentParticipationList(participation);
        final var response = request.putWithResponseBodyList(String.format("/api/exercises/%d/participations/update-individual-due-date", exercise.getId()), participationsToUpdate,
                StudentParticipation.class, HttpStatus.OK);

        assertThat(response).isEmpty(); // individual due date should remain null
        verify(programmingExerciseScheduleService, never()).updateScheduling(exercise);
        verify(programmingExerciseParticipationService, never()).unlockStudentRepository(exercise, participation);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateProgrammingExerciseIndividualDueDateInFuture() throws Exception {
        final var course = database.addCourseWithOneProgrammingExercise();
        var exercise = (ProgrammingExercise) course.getExercises().stream().findAny().get();
        exercise.setDueDate(ZonedDateTime.now().minusHours(4));
        exercise = exerciseRepo.save(exercise);

        var participation = database.addStudentParticipationForProgrammingExercise(exercise, "student1");
        participation.setIndividualDueDate(ZonedDateTime.now().plusHours(6));
        participation = participationRepo.save(participation);

        participation.setIndividualDueDate(ZonedDateTime.now().plusHours(2));

        doNothing().when(programmingExerciseParticipationService).unlockStudentRepository(exercise, participation);

        final var participationsToUpdate = new StudentParticipationList(participation);
        final var response = request.putWithResponseBodyList(String.format("/api/exercises/%d/participations/update-individual-due-date", exercise.getId()), participationsToUpdate,
                StudentParticipation.class, HttpStatus.OK);

        assertThat(response).hasSize(1);
        verify(programmingExerciseScheduleService, times(1)).updateScheduling(exercise);
        // make sure the student repo is unlocked as the due date is in the future
        verify(programmingExerciseParticipationService, times(1)).unlockStudentRepository(exercise, participation);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateProgrammingExerciseIndividualDueDateInPast() throws Exception {
        final var course = database.addCourseWithOneProgrammingExercise();
        var exercise = (ProgrammingExercise) course.getExercises().stream().findAny().get();
        exercise.setDueDate(ZonedDateTime.now().minusHours(4));
        exercise = exerciseRepo.save(exercise);

        var participation = database.addStudentParticipationForProgrammingExercise(exercise, "student1");
        participation.setIndividualDueDate(ZonedDateTime.now().plusHours(4));
        participation = participationRepo.save(participation);

        participation.setIndividualDueDate(ZonedDateTime.now().minusHours(2));

        doNothing().when(programmingExerciseParticipationService).lockStudentRepository(exercise, participation);

        final var participationsToUpdate = new StudentParticipationList(participation);
        final var response = request.putWithResponseBodyList(String.format("/api/exercises/%d/participations/update-individual-due-date", exercise.getId()), participationsToUpdate,
                StudentParticipation.class, HttpStatus.OK);

        assertThat(response).hasSize(1);
        verify(programmingExerciseScheduleService, times(1)).updateScheduling(exercise);
        // student repo should be locked as due date is in the past
        verify(programmingExerciseParticipationService, times(1)).lockStudentRepository(exercise, participation);
    }

    /**
     * When using {@code List<StudentParticipation>} directly as body in the unit tests, the deserialization fails as
     * there no longer is a {@code type} attribute due to type erasure. Therefore, Jackson does not know which subtype
     * of {@link Participation} is stored in the list.
     *
     * Using this wrapper-class avoids this issue.
     */
    private static class StudentParticipationList extends ArrayList<StudentParticipation> {

        public StudentParticipationList(StudentParticipation... participations) {
            super();
            this.addAll(Arrays.asList(participations));
        }
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getParticipationWithLatestResult() throws Exception {
        var participation = database.createAndSaveParticipationForExercise(textExercise, "student1");
        database.addResultToParticipation(null, null, participation);
        var result = ModelFactory.generateResult(true, 70D);
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
        var participation = database.createAndSaveParticipationForExercise(textExercise, "student1");
        var submission1 = database.addSubmission(participation, ModelFactory.generateTextSubmission("text", Language.ENGLISH, true));
        var submission2 = database.addSubmission(participation, ModelFactory.generateTextSubmission("text2", Language.ENGLISH, true));
        var submissions = request.getList("/api/participations/" + participation.getId() + "/submissions", HttpStatus.OK, Submission.class);
        assertThat(submissions).contains(submission1, submission2);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void cleanupBuildPlan() throws Exception {
        var participation = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        bambooRequestMockProvider.enableMockingOfRequests();
        bambooRequestMockProvider.mockDeleteBambooBuildPlan(participation.getBuildPlanId(), false);
        var actualParticipation = request.putWithResponseBody("/api/participations/" + participation.getId() + "/cleanupBuildPlan", null, Participation.class, HttpStatus.OK);
        assertThat(actualParticipation).isEqualTo(participation);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getParticipation() throws Exception {
        var participation = database.createAndSaveParticipationForExercise(textExercise, "student1");
        var actualParticipation = request.get("/api/exercises/" + textExercise.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getParticipationForTeamExercise() throws Exception {
        var now = ZonedDateTime.now();
        var exercise = ModelFactory.generateTextExercise(now.minusDays(2), now.plusDays(2), now.plusDays(4), course);
        exercise.setMode(ExerciseMode.TEAM);
        exercise = exerciseRepo.save(exercise);

        var student = database.getUserByLogin("student1");

        var team = new Team();
        team.addStudents(student);
        team.setExercise(exercise);
        team = teamRepository.save(team);

        var teams = new HashSet<Team>();
        teams.add(team);
        exercise.setTeams(teams);
        exercise = exerciseRepo.save(exercise);

        var participation = database.addTeamParticipationForExercise(exercise, team.getId());
        var actualParticipation = request.get("/api/exercises/" + exercise.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getParticipationByExerciseAndStudentIdWithEagerSubmissionsForTeam() throws Exception {
        var exercise = createTextExerciseForTeam();
        var student = database.getUserByLogin("student1");
        var team = createTeamForExercise(student, exercise);
        exercise = addTeamToExercise(team, exercise);

        var participation = database.addTeamParticipationForExercise(exercise, team.getId());
        var actualParticipation = request.get("/api/exercises/" + exercise.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);

        var participations = participationService.findByExerciseAndStudentIdWithEagerSubmissions(exercise, student.getId());
        assertThat(participations.size()).isEqualTo(1);
        assertThat(participations.get(0).getId()).isEqualTo(participation.getId());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getParticipationByExerciseAndStudentIdForTeam() throws Exception {
        var exercise = createTextExerciseForTeam();
        var student = database.getUserByLogin("student1");
        var team = createTeamForExercise(student, exercise);
        exercise = addTeamToExercise(team, exercise);

        var participation = database.addTeamParticipationForExercise(exercise, team.getId());
        var actualParticipation = request.get("/api/exercises/" + exercise.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);

        var participations = participationService.findByExerciseAndStudentId(exercise, student.getId());
        assertThat(participations.size()).isEqualTo(1);
        assertThat(participations.get(0).getId()).isEqualTo(participation.getId());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getParticipationByExerciseAndStudentLoginAnyStateWithEagerResultsForTeam() throws Exception {
        var exercise = createTextExerciseForTeam();
        var student = database.getUserByLogin("student1");
        var team = createTeamForExercise(student, exercise);
        exercise = addTeamToExercise(team, exercise);

        var participation = database.addTeamParticipationForExercise(exercise, team.getId());
        var actualParticipation = request.get("/api/exercises/" + exercise.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);

        participation = participationService.findOneByExerciseAndStudentLoginAnyStateWithEagerResultsElseThrow(exercise, student.getLogin());
        assertThat(participation).isNotNull();
        assertThat(participation.getId()).isEqualTo(participation.getId());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getParticipationByExerciseAndStudentLoginAnyStateForTeam() throws Exception {
        var exercise = createTextExerciseForTeam();
        var student = database.getUserByLogin("student1");
        var team = createTeamForExercise(student, exercise);
        exercise = addTeamToExercise(team, exercise);

        var participation = database.addTeamParticipationForExercise(exercise, team.getId());
        var actualParticipation = request.get("/api/exercises/" + exercise.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);

        var participations = participationService.findOneByExerciseAndStudentLoginAnyState(exercise, student.getLogin());
        assertThat(participations).isPresent();
        assertThat(participations.get().getId()).isEqualTo(participation.getId());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void deleteAllByTeamId() throws Exception {
        var exercise = createTextExerciseForTeam();
        var student = database.getUserByLogin("student1");
        var team = createTeamForExercise(student, exercise);
        exercise = addTeamToExercise(team, exercise);

        var participation = database.addTeamParticipationForExercise(exercise, team.getId());
        var actualParticipation = request.get("/api/exercises/" + exercise.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);

        participationService.deleteAllByTeamId(team.getId(), false, false);

        var participations = participationRepo.findByTeamId(team.getId());
        assertThat(participations).isEmpty();
    }

    private Exercise createTextExerciseForTeam() {
        var now = ZonedDateTime.now();
        var exercise = ModelFactory.generateTextExercise(now.minusDays(2), now.plusDays(2), now.plusDays(4), course);
        exercise.setMode(ExerciseMode.TEAM);
        return exerciseRepo.save(exercise);
    }

    private Team createTeamForExercise(User student, Exercise exercise) {
        var team = new Team();
        team.addStudents(student);
        team.setExercise(exercise);
        return teamRepository.save(team);
    }

    private Exercise addTeamToExercise(Team team, Exercise exercise) {
        var teams = new HashSet<Team>();
        teams.add(team);
        exercise.setTeams(teams);
        return exerciseRepo.save(exercise);
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
        var participation = database.createAndSaveParticipationForExercise(quizEx, "student1");
        var submission = database.addSubmission(participation, new QuizSubmission().scoreInPoints(11D).submitted(true));
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
