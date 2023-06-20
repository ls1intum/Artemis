package de.tum.in.www1.artemis.exercise.modelingexercise;

import static com.google.gson.JsonParser.parseString;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.modeling.ModelingPlagiarismResult;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismResultRepository;
import de.tum.in.www1.artemis.service.AssessmentService;
import de.tum.in.www1.artemis.service.ModelingSubmissionService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.FileUtils;

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
     * Create modeling exercise for a given course
     *
     * @param courseId id of the given course
     * @return created modeling exercise
     */
    public ModelingExercise createModelingExercise(Long courseId) {
        return createModelingExercise(courseId, null);
    }

    /**
     * Create modeling exercise with a given id for a given course
     *
     * @param courseId   id of the given course
     * @param exerciseId id of modeling exercise
     * @return created modeling exercise
     */
    public ModelingExercise createModelingExercise(Long courseId, Long exerciseId) {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(8);

        Course course1 = CourseFactory.generateCourse(courseId, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram,
                course1);
        modelingExercise.setGradingInstructions("Grading instructions");
        modelingExercise.getCategories().add("Modeling");
        modelingExercise.setId(exerciseId);
        course1.addExercises(modelingExercise);

        return modelingExercise;
    }

    /**
     * Add example submission to modeling exercise
     *
     * @param modelingExercise modeling exercise for which the example submission should be added
     * @return modeling exercise with example submission
     * @throws Exception if the resources file is not found
     */
    public ModelingExercise addExampleSubmission(ModelingExercise modelingExercise) throws Exception {
        Set<ExampleSubmission> exampleSubmissionSet = new HashSet<>();
        String validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        var exampleSubmission = participationUtilService.generateExampleSubmission(validModel, modelingExercise, true);
        exampleSubmission.assessmentExplanation("explanation");
        exampleSubmissionSet.add(exampleSubmission);
        modelingExercise.setExampleSubmissions(exampleSubmissionSet);
        return modelingExercise;
    }

    /**
     * @param title The title of the to be added modeling exercise
     * @return A course with one specified modeling exercise
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

    public Course addCourseWithOneModelingExercise() {
        return addCourseWithOneModelingExercise("ClassDiagram");
    }

    public Course addCourseWithOneReleasedModelExerciseWithKnowledge(String title) {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram,
                course);
        modelingExercise.setTitle(title);
        course.addExercises(modelingExercise);
        courseRepo.save(course);
        exerciseRepo.save(modelingExercise);
        return course;
    }

    public ModelingExercise addCourseExamExerciseGroupWithOneModelingExercise(String title) {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        ModelingExercise classExercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup);
        classExercise.setTitle(title);
        classExercise = modelingExerciseRepository.save(classExercise);
        return classExercise;
    }

    public ModelingExercise addCourseExamExerciseGroupWithOneModelingExercise() {
        return addCourseExamExerciseGroupWithOneModelingExercise("ClassDiagram");
    }

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
        Course storedCourse = courseRepo.findByIdWithExercisesAndLecturesElseThrow(course.getId());
        Set<Exercise> exercises = storedCourse.getExercises();
        assertThat(exercises).as("eleven exercises got stored").hasSize(11);
        assertThat(exercises).as("Contains all exercises").containsExactlyInAnyOrder(course.getExercises().toArray(new Exercise[] {}));
        return course;
    }

    /**
     * Stores for the given model a submission of the user and initiates the corresponding Result
     *
     * @param exercise exercise the submission belongs to
     * @param model    ModelingSubmission json as string contained in the submission
     * @param login    of the user the submission belongs to
     * @return submission stored in the modelingSubmissionRepository
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

    public ModelingSubmission addModelingSubmission(ModelingExercise exercise, ModelingSubmission submission, String login) {
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        modelingSubmissionRepo.save(submission);
        studentParticipationRepo.save(participation);
        return submission;
    }

    public ModelingSubmission addModelingTeamSubmission(ModelingExercise exercise, ModelingSubmission submission, Team team) {
        StudentParticipation participation = participationUtilService.addTeamParticipationForExercise(exercise, team.getId());
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        modelingSubmissionRepo.save(submission);
        studentParticipationRepo.save(participation);
        return submission;
    }

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

    public Submission addModelingSubmissionWithFinishedResultAndAssessor(ModelingExercise exercise, ModelingSubmission submission, String login, String assessorLogin) {
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, login);
        return participationUtilService.addSubmissionWithFinishedResultsWithAssessor(participation, submission, assessorLogin);
    }

    public ModelingSubmission addModelingSubmissionFromResources(ModelingExercise exercise, String path, String login) throws Exception {
        String model = FileUtils.loadFileFromResources(path);
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(model, true);
        submission = addModelingSubmission(exercise, submission, login);
        checkModelingSubmissionCorrectlyStored(submission.getId(), model);
        return submission;
    }

    public void checkModelingSubmissionCorrectlyStored(Long submissionId, String sentModel) {
        Optional<ModelingSubmission> modelingSubmission = modelingSubmissionRepo.findById(submissionId);
        assertThat(modelingSubmission).as("submission correctly stored").isPresent();
        checkModelsAreEqual(modelingSubmission.get().getModel(), sentModel);
    }

    public void checkModelsAreEqual(String storedModel, String sentModel) {
        JsonObject sentModelObject = parseString(sentModel).getAsJsonObject();
        JsonObject storedModelObject = parseString(storedModel).getAsJsonObject();
        assertThat(storedModelObject).as("model correctly stored").isEqualTo(sentModelObject);
    }

    public Result addModelingAssessmentForSubmission(ModelingExercise exercise, ModelingSubmission submission, String path, String login, boolean submit) throws Exception {
        List<Feedback> feedbackList = participationUtilService.loadAssessmentFomResources(path);
        Result result = assessmentService.saveManualAssessment(submission, feedbackList, null);
        result.setParticipation(submission.getParticipation().results(null));
        result.setAssessor(userUtilService.getUserByLogin(login));
        resultRepo.save(result);
        if (submit) {
            assessmentService.submitManualAssessment(result.getId(), exercise, submission.getSubmissionDate());
        }
        return resultRepo.findWithEagerSubmissionAndFeedbackAndAssessorById(result.getId()).get();
    }

    public Result addModelingAssessmentForSubmission(ModelingExercise exercise, ModelingSubmission submission, String login, boolean submit) {
        Feedback feedback1 = feedbackRepo.save(new Feedback().detailText("detail1"));
        Feedback feedback2 = feedbackRepo.save(new Feedback().detailText("detail2"));
        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(feedback1);
        feedbacks.add(feedback2);

        Result result = assessmentService.saveManualAssessment(submission, feedbacks, null);
        result.setParticipation(submission.getParticipation().results(null));
        result.setAssessor(userUtilService.getUserByLogin(login));
        resultRepo.save(result);
        if (submit) {
            assessmentService.submitManualAssessment(result.getId(), exercise, submission.getSubmissionDate());
        }
        return resultRepo.findWithEagerSubmissionAndFeedbackAndAssessorById(result.getId()).get();
    }

    public ModelingPlagiarismResult createModelingPlagiarismResultForExercise(Exercise exercise) {
        ModelingPlagiarismResult result = new ModelingPlagiarismResult();
        result.setExercise(exercise);
        result.setSimilarityDistribution(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 });
        result.setDuration(4);
        return plagiarismResultRepo.save(result);
    }
}
