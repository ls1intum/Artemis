package de.tum.cit.aet.artemis.exercise.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.AssessmentService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.course.CourseFactory;
import de.tum.cit.aet.artemis.exam.ExamUtilService;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.modeling.repository.ModelingSubmissionRepository;
import de.tum.cit.aet.artemis.modeling.service.ModelingSubmissionService;
import de.tum.cit.aet.artemis.participation.ParticipationFactory;
import de.tum.cit.aet.artemis.participation.ParticipationUtilService;
import de.tum.cit.aet.artemis.plagiarism.domain.modeling.ModelingPlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismResultRepository;
import de.tum.cit.aet.artemis.user.UserUtilService;
import de.tum.cit.aet.artemis.util.TestResourceUtils;

/**
 * Service responsible for initializing the database with specific testdata related to modeling exercises for use in integration tests.
 */
@Service
public class ModelingExerciseUtilService {

    private static final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(1);

    private static final ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(2);

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private ModelingExerciseRepository modelingExerciseRepository;

    @Autowired
    private ResultRepository resultRepo;

    @Autowired
    private StudentParticipationRepository studentParticipationRepo;

    @Autowired
    private ModelingSubmissionRepository modelingSubmissionRepo;

    @Autowired
    private FeedbackRepository feedbackRepo;

    @Autowired
    private PlagiarismResultRepository plagiarismResultRepo;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private AssessmentService assessmentService;

    @Autowired
    private ModelingSubmissionService modelSubmissionService;

    /**
     * Creates and saves a Course with a ModelingExercise. The ModelingExercise's DiagramType is set to ClassDiagram.
     *
     * @param title The title of the ModelingExercise
     * @return The created Course
     */
    public Course addCourseWithOneModelingExercise(String title) {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram,
                course);
        modelingExercise.setTitle(title);
        course.addExercises(modelingExercise);
        course.setMaxComplaintTimeDays(14);
        course = courseRepo.save(course);
        modelingExercise = exerciseRepo.save(modelingExercise);
        assertThat(course.getExercises()).as("course contains the exercise").containsExactlyInAnyOrder(modelingExercise);
        assertThat(modelingExercise.getPresentationScoreEnabled()).as("presentation score is enabled").isTrue();
        return course;
    }

    /**
     * Updates an existing ModelingExercise in the database.
     *
     * @param exercise The ModelingExercise to be updated
     * @return The updated ModelingExercise
     */
    public ModelingExercise updateExercise(ModelingExercise exercise) {
        return modelingExerciseRepository.save(exercise);
    }

    /**
     * Creates and saves a Course with a ModelingExercise. The ModelingExercise's DiagramType is set to ClassDiagram.
     *
     * @return The created Course
     */
    public Course addCourseWithOneModelingExercise() {
        return addCourseWithOneModelingExercise("ClassDiagram");
    }

    /**
     * Creates and saves a ModelingExercise. Also creates an active Course and an Exam with a mandatory ExerciseGroup the Modeling Exercise belongs to.
     *
     * @param title The title of the ModelingExercise
     * @return The created ModelingExercise
     */
    public ModelingExercise addCourseExamExerciseGroupWithOneModelingExercise(String title) {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        ModelingExercise classExercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup);
        classExercise.setTitle(title);
        classExercise = modelingExerciseRepository.save(classExercise);
        return classExercise;
    }

    /**
     * Creates and saves a ModelingExercise. Also creates an active Course and an Exam with a mandatory ExerciseGroup the Modeling Exercise belongs to.
     *
     * @return The created ModelingExercise
     */
    public ModelingExercise addCourseExamExerciseGroupWithOneModelingExercise() {
        return addCourseExamExerciseGroupWithOneModelingExercise("ClassDiagram");
    }

    /**
     * Creates and saves a Course with 11 ModelingExercises, one of each DiagramType and one finished exercise.
     *
     * @return The created Course
     */
    public Course addCourseWithDifferentModelingExercises() {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        ModelingExercise classExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course);
        classExercise.setTitle("ClassDiagram");
        course.addExercises(classExercise);

        ModelingExercise activityExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ActivityDiagram,
                course);
        activityExercise.setTitle("ActivityDiagram");
        course.addExercises(activityExercise);

        ModelingExercise objectExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ObjectDiagram,
                course);
        objectExercise.setTitle("ObjectDiagram");
        course.addExercises(objectExercise);

        ModelingExercise useCaseExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.UseCaseDiagram,
                course);
        useCaseExercise.setTitle("UseCaseDiagram");
        course.addExercises(useCaseExercise);

        ModelingExercise communicationExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp,
                DiagramType.CommunicationDiagram, course);
        communicationExercise.setTitle("CommunicationDiagram");
        course.addExercises(communicationExercise);

        ModelingExercise componentExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ComponentDiagram,
                course);
        componentExercise.setTitle("ComponentDiagram");
        course.addExercises(componentExercise);

        ModelingExercise deploymentExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.DeploymentDiagram,
                course);
        deploymentExercise.setTitle("DeploymentDiagram");
        course.addExercises(deploymentExercise);

        ModelingExercise petriNetExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.PetriNet, course);
        petriNetExercise.setTitle("PetriNet");
        course.addExercises(petriNetExercise);

        ModelingExercise syntaxTreeExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.SyntaxTree,
                course);
        syntaxTreeExercise.setTitle("SyntaxTree");
        course.addExercises(syntaxTreeExercise);

        ModelingExercise flowchartExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.Flowchart, course);
        flowchartExercise.setTitle("Flowchart");
        course.addExercises(flowchartExercise);

        ModelingExercise finishedExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, pastTimestamp, futureTimestamp, DiagramType.ClassDiagram, course);
        finishedExercise.setTitle("finished");
        course.addExercises(finishedExercise);

        course = courseRepo.save(course);
        exerciseRepo.save(classExercise);
        exerciseRepo.save(activityExercise);
        exerciseRepo.save(objectExercise);
        exerciseRepo.save(useCaseExercise);
        exerciseRepo.save(communicationExercise);
        exerciseRepo.save(componentExercise);
        exerciseRepo.save(deploymentExercise);
        exerciseRepo.save(petriNetExercise);
        exerciseRepo.save(syntaxTreeExercise);
        exerciseRepo.save(flowchartExercise);
        exerciseRepo.save(finishedExercise);
        Course storedCourse = courseRepo.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(course.getId());
        Set<Exercise> exercises = storedCourse.getExercises();
        assertThat(exercises).as("eleven exercises got stored").hasSize(11);
        assertThat(exercises).as("Contains all exercises").containsExactlyInAnyOrder(course.getExercises().toArray(new Exercise[] {}));
        return course;
    }

    /**
     * Creates and saves a ModelingSubmission, a Result and a StudentParticipation for the given ModelingExercise.
     *
     * @param exercise The ModelingExercise the submission belongs to
     * @param model    The model of the submission
     * @param login    The login of the user the submission belongs to
     * @return The created ModelingSubmission
     */
    public ModelingSubmission addModelingSubmissionWithEmptyResult(ModelingExercise exercise, String model, String login) {
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, login);
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(model, true);
        var user = userUtilService.getUserByLogin(login);
        submission = modelSubmissionService.handleModelingSubmission(submission, exercise, user);
        Result result = new Result();
        result = resultRepo.save(result);
        result.setSubmission(submission);
        submission.addResult(result);
        participation.addResult(result);
        studentParticipationRepo.save(participation);
        modelingSubmissionRepo.save(submission);
        resultRepo.save(result);
        return submission;
    }

    /**
     * Creates and saves a StudentParticipation for the given ModelingExercise and ModelingSubmission.
     *
     * @param exercise   The ModelingExercise the submission belongs to
     * @param submission The ModelingSubmission that belongs to the StudentParticipation
     * @param login      The login of the user the submission belongs to
     * @return The updated ModelingSubmission
     */
    public ModelingSubmission addModelingSubmission(ModelingExercise exercise, ModelingSubmission submission, String login) {
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        modelingSubmissionRepo.save(submission);
        studentParticipationRepo.save(participation);
        return submission;
    }

    /**
     * Creates and saves a team StudentParticipation for the given ModelingExercise and a team ModelingSubmission.
     *
     * @param exercise   The ModelingExercise the submission belongs to
     * @param submission The ModelingSubmission that belongs to the StudentParticipation
     * @param team       The team the submission belongs to
     * @return The updated ModelingSubmission
     */
    public ModelingSubmission addModelingTeamSubmission(ModelingExercise exercise, ModelingSubmission submission, Team team) {
        StudentParticipation participation = participationUtilService.addTeamParticipationForExercise(exercise, team.getId());
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        modelingSubmissionRepo.save(submission);
        studentParticipationRepo.save(participation);
        return submission;
    }

    /**
     * Creates and saves a StudentParticipation for the given ModelingExercise, the ModelingSubmission, and login. Also creates and saves a Result for the StudentParticipation
     * given the assessorLogin.
     *
     * @param exercise      The ModelingExercise the submission belongs to
     * @param submission    The ModelingSubmission that belongs to the StudentParticipation
     * @param login         The login of the user the submission belongs to
     * @param assessorLogin The login of the assessor the Result belongs to
     * @return The updated ModelingSubmission
     */
    public ModelingSubmission addModelingSubmissionWithResultAndAssessor(ModelingExercise exercise, ModelingSubmission submission, String login, String assessorLogin) {

        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission = modelingSubmissionRepo.save(submission);

        Result result = new Result();

        result.setAssessor(userUtilService.getUserByLogin(assessorLogin));
        result.setAssessmentType(AssessmentType.MANUAL);
        result = resultRepo.save(result);
        submission = modelingSubmissionRepo.save(submission);
        studentParticipationRepo.save(participation);
        result = resultRepo.save(result);

        result.setSubmission(submission);
        submission.setParticipation(participation);
        submission.addResult(result);
        submission.getParticipation().addResult(result);
        submission = modelingSubmissionRepo.save(submission);
        studentParticipationRepo.save(participation);
        return submission;
    }

    /**
     * Creates and saves a StudentParticipation for the given ModelingExercise, the ModelingSubmission, and login. Also creates and saves a Result for the StudentParticipation
     * given the assessorLogin.
     *
     * @param exercise      The ModelingExercise the submission belongs to
     * @param submission    The ModelingSubmission that belongs to the StudentParticipation
     * @param login         The login of the user the submission belongs to
     * @param assessorLogin The login of the assessor the Result belongs to
     * @return The updated Submission
     */
    public Submission addModelingSubmissionWithFinishedResultAndAssessor(ModelingExercise exercise, ModelingSubmission submission, String login, String assessorLogin) {
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, login);
        return participationUtilService.addSubmissionWithFinishedResultsWithAssessor(participation, submission, assessorLogin);
    }

    /**
     * Creates and saves a ModelingSubmission from a file.
     *
     * @param exercise The ModelingExercise the submission belongs to
     * @param path     The path to the file that contains the submission's model
     * @param login    The login of the user the submission belongs to
     * @return The created ModelingSubmission
     * @throws IOException If the file can't be read
     */
    public ModelingSubmission addModelingSubmissionFromResources(ModelingExercise exercise, String path, String login) throws IOException {
        String model = TestResourceUtils.loadFileFromResources(path);
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(model, true);
        submission = addModelingSubmission(exercise, submission, login);
        checkModelingSubmissionCorrectlyStored(submission.getId(), model);
        return submission;
    }

    /**
     * Verifies that a ModelingSubmission with the given id has been stored with the given model. Fails if the submission can't be found or the models don't match.
     *
     * @param submissionId The id of the ModelingSubmission
     * @param sentModel    The model that should have been stored
     */
    public void checkModelingSubmissionCorrectlyStored(Long submissionId, String sentModel) throws JsonProcessingException {
        Optional<ModelingSubmission> modelingSubmission = modelingSubmissionRepo.findById(submissionId);
        assertThat(modelingSubmission).as("submission correctly stored").isPresent();
        checkModelsAreEqual(modelingSubmission.orElseThrow().getModel(), sentModel);
    }

    /**
     * Verifies that the given models are equal. Fails if they are not equal.
     *
     * @param storedModel The model that has been stored
     * @param sentModel   The model that should have been stored
     */
    public void checkModelsAreEqual(String storedModel, String sentModel) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode sentModelNode = objectMapper.readTree(sentModel);
        JsonNode storedModelNode = objectMapper.readTree(storedModel);
        assertThat(storedModelNode).as("model correctly stored").isEqualTo(sentModelNode);
    }

    /**
     * Creates and saves a Result given a ModelingSubmission and a path to a file containing the Feedback for the Result.
     *
     * @param exercise   The ModelingExercise the submission belongs to
     * @param submission The ModelingSubmission the Result belongs to
     * @param path       The path to the file containing the Feedback for the Result
     * @param login      The login of the assessor the Result belongs to
     * @param submit     True, if the Result should be submitted (if the Result needs to be edited before submission, set this to false)
     * @return The created Result
     * @throws Exception If the file can't be read
     */
    public Result addModelingAssessmentForSubmission(ModelingExercise exercise, ModelingSubmission submission, String path, String login, boolean submit) throws Exception {
        List<Feedback> feedbackList = participationUtilService.loadAssessmentFomResources(path);
        Result result = assessmentService.saveAndSubmitManualAssessment(exercise, submission, feedbackList, null, null, submit);
        result.setParticipation(submission.getParticipation().results(null));
        result.setAssessor(userUtilService.getUserByLogin(login));
        resultRepo.save(result);
        return resultRepo.findWithBidirectionalSubmissionAndFeedbackAndAssessorAndAssessmentNoteAndTeamStudentsByIdElseThrow(result.getId());
    }

    /**
     * Creates and saves a Result for the given ModelingSubmission. The Result contains two Feedback elements.
     *
     * @param exercise   The ModelingExercise the submission belongs to
     * @param submission The ModelingSubmission the Result belongs to
     * @param login      The login of the assessor the Result belongs to
     * @param submit     True, if the Result should be submitted (if the Result needs to be edited before submission, set this to false)
     * @return The created Result
     */
    public Result addModelingAssessmentForSubmission(ModelingExercise exercise, ModelingSubmission submission, String login, boolean submit) {
        Feedback feedback1 = feedbackRepo.save(new Feedback().detailText("detail1"));
        Feedback feedback2 = feedbackRepo.save(new Feedback().detailText("detail2"));
        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(feedback1);
        feedbacks.add(feedback2);

        Result result = assessmentService.saveAndSubmitManualAssessment(exercise, submission, feedbacks, null, null, submit);
        result.setParticipation(submission.getParticipation().results(null));
        result.setAssessor(userUtilService.getUserByLogin(login));
        resultRepo.save(result);
        return resultRepo.findWithBidirectionalSubmissionAndFeedbackAndAssessorAndAssessmentNoteAndTeamStudentsByIdElseThrow(result.getId());
    }

    /**
     * Creates and saves a ModelingPlagiarismResult for the given Exercise.
     *
     * @param exercise The Exercise the ModelingPlagiarismResult belongs to
     * @return The created ModelingPlagiarismResult
     */
    public ModelingPlagiarismResult createModelingPlagiarismResultForExercise(Exercise exercise) {
        ModelingPlagiarismResult result = new ModelingPlagiarismResult();
        result.setExercise(exercise);
        result.setSimilarityDistribution(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 });
        result.setDuration(4);
        return plagiarismResultRepo.save(result);
    }
}
