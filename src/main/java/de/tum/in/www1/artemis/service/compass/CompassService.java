package de.tum.in.www1.artemis.service.compass;

import static de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.google.gson.JsonObject;
import com.hazelcast.core.HazelcastInstance;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.compass.grade.CompassGrade;
import de.tum.in.www1.artemis.service.compass.grade.Grade;

@Service
public class CompassService {

    private final Logger log = LoggerFactory.getLogger(CompassService.class);

    private final ResultRepository resultRepository;

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final ModelingSubmissionRepository modelingSubmissionRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    /**
     * Map that contains a Map for different modeling exercises, i.e. the outer map maps exerciseId -> Map. The inner maps map submissionIds to automatic results.
     * Automatic results generated by Compass are not stored in the database, instead they are stored in these maps. As soon as a submission is locked for assessment, its automatic
     * result will be retrieved from the corresponding map and stored to the database.
     */
    private Map<Long, Result> semiAutomaticResults;

    /**
     * Map exerciseId to compass CalculationEngines
     */
    private static Map<Long, CompassCalculationEngine> compassCalculationEngines = new ConcurrentHashMap<>();

    private final HazelcastInstance hazelcastInstance;

    public CompassService(ResultRepository resultRepository, ModelingExerciseRepository modelingExerciseRepository, ModelingSubmissionRepository modelingSubmissionRepository,
            StudentParticipationRepository studentParticipationRepository, HazelcastInstance hazelcastInstance) {
        this.resultRepository = resultRepository;
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.hazelcastInstance = hazelcastInstance;
        this.semiAutomaticResults = hazelcastInstance.getMap("semi_automatic_results");
    }

    /**
     * Indicates if the given diagram type is supported by Compass. At the moment Compass only support class diagrams.
     *
     * @param modelingExercise the modelingExercise that should be checked if automatic assessment is supported
     * @return true if the given diagram type is supported by Compass, false otherwise
     */
    public boolean isSupported(ModelingExercise modelingExercise) {

        // only use compass for course exercises, in exam exercises the additional delay is too much so it is currently deactivated
        // TODO: we should support compass also for the exam mode
        if (modelingExercise.isExamExercise()) {
            return false;
        }

        // In case the instructor specifies in the UI whether the semi-automatic assessment is possible or not.
        // NOTE: Currently, this is only possible for for exercises with class or activity diagrams
        DiagramType diagramType = modelingExercise.getDiagramType();
        if (modelingExercise.getAssessmentType() != null) {
            return (modelingExercise.getAssessmentType() == AssessmentType.SEMI_AUTOMATIC)
                    && (diagramType == DiagramType.ClassDiagram || diagramType == DiagramType.ActivityDiagram);
        }
        // if the assessment mode is not specified (e.g. for legacy exercises), team exercises are not supported
        if (modelingExercise.isTeamMode()) {
            return false;
        }
        return diagramType == DiagramType.ClassDiagram || diagramType == DiagramType.ActivityDiagram;
    }

    /**
     * Indicates if the diagram type of the given exercise is supported by Compass. At the moment Compass only support class diagrams.
     *
     * @param exerciseId the id of the exercise that should be checked
     * @return true if the diagram type of the given exercise is supported by Compass, false otherwise
     */
    private boolean isSupported(long exerciseId) {
        ModelingExercise modelingExercise = findModelingExerciseById(exerciseId);
        return modelingExercise != null && isSupported(modelingExercise);
    }

    /**
     * Get a configured number of ids of the next optimal modeling submissions for the given exercise. Optimal means that an assessment for these models result in the biggest
     * knowledge gain for Compass which can be used for automatic assessments. The number of new optimal models that should be load is defined by NUMBER_OF_NEW_OPTIMAL_MODELS.
     *
     * @param exerciseId the id of the exercise the modeling submissions should belong to
     * @return Ids of the next optimal models, an empty list if all models have been assessed for the given exercise
     */
    private List<Long> getNextOptimalModels(long exerciseId) {
        return compassCalculationEngines.get(exerciseId).getNextOptimalModels(NUMBER_OF_NEW_OPTIMAL_MODELS);
    }

    /**
     * Remove a model from the waiting list of models which should be assessed next
     *
     * @param exerciseId        the exerciseId
     * @param modelSubmissionId the id of the model submission which can be removed
     */
    public void removeModelWaitingForAssessment(long exerciseId, long modelSubmissionId) {
        if (!isSupported(exerciseId) || !loadExerciseIfSuspended(exerciseId)) {
            return;
        }
        compassCalculationEngines.get(exerciseId).removeModelWaitingForAssessment(modelSubmissionId, true);
    }

    /**
     * Get the (cached) list of optimal models that need to be assessed next. The models in the list are optimal in the sense of knowledge gain for Compass, helping to
     * automatically assess as many other models as possible. If the number of models in the list is smaller than the configured OPTIMAL_MODEL_THRESHOLD, new "optimal" models will
     * be added to the list.
     *
     * @param exerciseId the id of the exercise the models should belong to
     * @return a list of optimal model Ids waiting for an assessment by an assessor
     */
    public List<Long> getModelsWaitingForAssessment(long exerciseId) {
        if (!isSupported(exerciseId) || !loadExerciseIfSuspended(exerciseId)) {
            return new ArrayList<>();
        }

        List<Long> optimalModelIds = getCalculationEngineModelsWaitingForAssessment(exerciseId);

        if (optimalModelIds.size() < OPTIMAL_MODEL_THRESHOLD) {
            List<Long> nextOptimalModelIds = getNextOptimalModels(exerciseId);
            if (nextOptimalModelIds != null) {
                optimalModelIds.addAll(nextOptimalModelIds);
            }
        }

        removeManuallyAssessedModels(optimalModelIds, exerciseId);
        return new ArrayList<>(optimalModelIds);
    }

    /**
     * Check for every model in the given list of optimal models if it is locked by another user (assessor) or if there is a manually saved or finished assessment for the
     * corresponding modeling submission. If there is, the model gets removed from the list of optimal models. This check should not be necessary as there should only be models in
     * the list that have no or only an automatic assessment. We better double check here as we want to make sure that no models with finished or manual assessments get sent to
     * other users than the assessor.
     *
     * @param optimalModelIds the list of ids of optimal models
     * @param exerciseId      the id of the exercise the optimal models belong to
     */
    private void removeManuallyAssessedModels(List<Long> optimalModelIds, long exerciseId) {
        Iterator<Long> iterator = optimalModelIds.iterator();
        while (iterator.hasNext()) {
            Long modelId = iterator.next();
            Optional<Result> result = resultRepository.findDistinctWithAssessorBySubmissionId(modelId);
            if (result.isPresent()
                    && (result.get().getAssessor() != null || result.get().getCompletionDate() != null || AssessmentType.MANUAL.equals(result.get().getAssessmentType()))) {
                removeModelWaitingForAssessment(exerciseId, modelId);
                iterator.remove();
            }
        }
    }

    /**
     * Mark a model as unassessed, i.e. indicate that it (still) needs to be assessed. By that it is not locked anymore and can be returned for assessment by Compass again.
     * Afterwards, the automatic assessment is triggered for the submission of the cancelled assessment so that the next tutor might get a partially assessed model.
     *
     * @param modelingExercise  the corresponding exercise
     * @param modelSubmissionId the id of the model submission which should be marked as unassessed
     */
    public void cancelAssessmentForSubmission(ModelingExercise modelingExercise, long modelSubmissionId) {
        if (!isSupported(modelingExercise) || !loadExerciseIfSuspended(modelingExercise.getId())) {
            return;
        }
        Long exerciseId = modelingExercise.getId();
        compassCalculationEngines.get(exerciseId).markModelAsUnassessed(modelSubmissionId);
        semiAutomaticResults.remove(modelSubmissionId);

        generateAutomaticFeedbackSuggestions(modelSubmissionId, modelingExercise.getId());
    }

    /**
     * Empty the waiting list
     *
     * @param exerciseId the exerciseId
     */
    public void resetModelsWaitingForAssessment(long exerciseId) {
        if (!isSupported(exerciseId) || !loadExerciseIfSuspended(exerciseId)) {
            return;
        }
        List<Long> optimalModelIds = getCalculationEngineModelsWaitingForAssessment(exerciseId);
        for (long modelSubmissionId : optimalModelIds) {
            compassCalculationEngines.get(exerciseId).removeModelWaitingForAssessment(modelSubmissionId, false);
        }
    }

    /**
     * Update the engine for the given exercise with a new manual assessment. Check for every model if new automatic assessments could be created with the new information.
     *
     * @param exerciseId         the id of the exercise to which the assessed submission belongs
     * @param submissionId       the id of the submission for which a new assessment is added
     * @param modelingAssessment the new assessment as a list of Feedback
     */
    public void addAssessment(long exerciseId, long submissionId, List<Feedback> modelingAssessment) {
        log.info("Add assessment for exercise {} and model {}", exerciseId, submissionId);
        if (!isSupported(exerciseId) || !loadExerciseIfSuspended(exerciseId)) { // TODO rework after distinguishing between saved and submitted assessments
            return;
        }

        CompassCalculationEngine engine = compassCalculationEngines.get(exerciseId);

        List<ModelingSubmission> modelingSubmissions = modelingSubmissionRepository.findSubmittedByExerciseIdWithEagerResultsAndFeedback(exerciseId);
        engine.notifyNewModels(modelingSubmissions);

        engine.notifyNewAssessment(modelingAssessment, submissionId);

        // Check all models for new automatic assessments
        assessAllAutomatically(engine.getModelIds(), exerciseId);
    }

    /**
     * Get the semi automatic result generated by Compass from the semi automatic result map for the submission with the given id.
     *
     * @param submissionId the id of the submission for which to get the semi automatic result
     * @return the semi automatic result for the submission with the given id
     */
    public Result getResultWithFeedbackSuggestionsForSubmission(long submissionId) {
        return semiAutomaticResults.get(submissionId);
    }

    /**
     * Remove the semi automatic result generated by Compass from the semi automatic result map for the submission with the given id.
     *
     * @param submissionId the id of the submission for which to remove the semi automatic result from the map
     * @param exerciseId the exercise the given submission belongs to
     */
    public void removeSemiAutomaticResultForSubmission(long submissionId, long exerciseId) {
        semiAutomaticResults.remove(submissionId);
    }

    /**
     * Update the (existing) semi automatic result for the given submission with automatic feedback generated by Compass. If there is no existing result, a new one is created
     * first. The updated result gets added to the hash map containing all automatic results. Note, that Compass tries to automatically assess every model as much as possible,
     * but does not submit any semi automatic assessment to the student. A tutor has to review the automatic feedback before completing and submitting the assessment manually,
     * even if Compass could assess 100% of the model automatically.
     *
     * @param submissionId the id of the modeling submission for which an semi automatic result should be generated/updated
     * @param exerciseId   the id of the corresponding exercise
     */
    private void generateAutomaticFeedbackSuggestions(long submissionId, long exerciseId) {
        CompassCalculationEngine engine = compassCalculationEngines.get(exerciseId);
        ModelingSubmission modelingSubmission = findModelingSubmissionById(submissionId);

        if (engine == null || modelingSubmission == null) {
            log.error("No calculation engine or submission - submission with ID {} could not be assessed automatically", submissionId);
            return;
        }

        Result result = provideResultForSubmission(modelingSubmission);
        generateSemiAutomaticResult(submissionId, result, engine);
    }

    /**
     * Update the (existing) semi automatic result for each of the given submissions with automatic feedback generated by Compass. If there is no existing result for a submission, a new
     * one is created first. The updated results get added to the hash map containing all automatic results. Note, that Compass tries to automatically assess every model as much as
     * possible, but does not submit any automatic assessment to the student. A user has to review every(!) automatic assessment before completing and submitting the assessment
     * manually, even if Compass could assess 100% of the model automatically.
     *
     * @param submissionIds a collection of modeling submission ids for which the automatic results should be generated/updated
     * @param exerciseId    the id of the corresponding exercise
     */
    private void assessAllAutomatically(Collection<Long> submissionIds, long exerciseId) {
        CompassCalculationEngine engine = compassCalculationEngines.get(exerciseId);
        List<ModelingSubmission> modelingSubmissions = modelingSubmissionRepository.findWithEagerResultsFeedbacksAssessorAndParticipationResultsByIdIn(submissionIds);

        if (engine == null) {
            log.error("No calculation engine - submissions of exercise with ID {} could not be assessed automatically", exerciseId);
            return;
        }

        for (ModelingSubmission modelingSubmission : modelingSubmissions) {
            Result result = provideResultForSubmission(modelingSubmission);
            generateSemiAutomaticResult(modelingSubmission.getId(), result, engine);
        }
    }

    /**
     * Generate a semi automatic result of the given submission with automatic feedback. It gets the automatic assessment for the given submission from the calculation engine,
     * generates automatic feedback items from it and updates the given result with the feedback. Afterwards, the semi automatic result is added to the hash map.
     * All of this is done only if the corresponding submission is not manually assessed already, i.e. the assessment type of the result is not MANUAL and the assessor is
     * not set.
     *
     * @param submissionId the id of the submission for which the semi automatic result should be generated
     * @param result the result of the submission that is updated with the automatic feedback generated by Compass
     * @param engine the calculation engine for the corresponding exercise
     */
    private void generateSemiAutomaticResult(long submissionId, Result result, CompassCalculationEngine engine) {
        if (result.getAssessmentType() != AssessmentType.MANUAL && result.getAssessor() == null) {
            // Round compass grades to avoid machine precision errors, make the grades more readable and give a slight advantage.
            Grade grade = roundGrades(engine.getGradeForModel(submissionId));

            List<Feedback> automaticFeedback = engine.convertToFeedback(grade, submissionId, result);
            result.getFeedbacks().clear(); // Note, that a result is always initialized with an empty list -> no NPE here
            result.getFeedbacks().addAll(automaticFeedback);
            result.setHasFeedback(false);
            result.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
            semiAutomaticResults.put(submissionId, result);
        }
        else {
            // Make sure next optimal model is in a valid state
            engine.removeModelWaitingForAssessment(submissionId, true);
        }
    }

    /**
     * Get the modeling exercise with the given id from the database.
     *
     * @param exerciseId the id of the modeling exercise that should be loaded
     * @return the modeling exercise with the given id, or null if no exercise could be found
     */
    private ModelingExercise findModelingExerciseById(long exerciseId) {
        Optional<ModelingExercise> optionalModelingExercise = modelingExerciseRepository.findById(exerciseId);
        if (!optionalModelingExercise.isPresent()) {
            log.error("Exercise with ID {} could not be found", exerciseId);
            return null;
        }
        return optionalModelingExercise.get();
    }

    /**
     * Get the modeling submission with the given id from the database.
     *
     * @param submissionId the id of the modeling submission that should be loaded
     * @return the modeling submission with the given id, or null if no submission could be found
     */
    private ModelingSubmission findModelingSubmissionById(long submissionId) {
        Optional<ModelingSubmission> optionalModelingSubmission = modelingSubmissionRepository.findWithResultsFeedbacksAssessorAndParticipationResultsById(submissionId);
        if (!optionalModelingSubmission.isPresent()) {
            log.error("Modeling submission with ID {} could not be found.", submissionId);
            return null;
        }
        return optionalModelingSubmission.get();
    }

    /**
     * Get the result of the given modeling submission. If the given submission already contains a manual result, this result is returned. Otherwise, it tries to load and return
     * the result for the submission from the hash map containing all automatic results. If no result could be found in the hash map, a new result is created for the given
     * submission.
     *
     * @param modelingSubmission the submission for which the result should be obtained
     * @return the result of the given submission either obtained from the submission or the semi automatic result map, or a newly created one if it does not exist already
     */
    private Result provideResultForSubmission(ModelingSubmission modelingSubmission) {
        Result result = modelingSubmission.getLatestResult();

        if (result == null || !AssessmentType.MANUAL.equals(result.getAssessmentType())) {
            result = semiAutomaticResults.get(modelingSubmission.getId());

            if (result == null) {
                StudentParticipation studentParticipation = (StudentParticipation) modelingSubmission.getParticipation();
                result = new Result().submission(modelingSubmission).participation(studentParticipation);
            }
        }

        return result;
    }

    /**
     * Round compass grades to avoid machine precision errors, make the grades more readable and give a slight advantage which makes 100% scores easier reachable.
     * <p>
     * Positive values > [x.0, x.15[ gets rounded to x.0 > [x.15, x.65[ gets rounded to x.5 > [x.65, x + 1[ gets rounded to x + 1
     * <p>
     * Negative values > [-x - 1, -x.85[ gets rounded to -x - 1 > [-x.85, -x.35[ gets rounded to -x.5 > [-x.35, -x.0[ gets rounded to -x.0
     *
     * @param grade the grade for which the points should be rounded
     * @return the rounded compass grade
     */
    private Grade roundGrades(Grade grade) {
        if (grade == null) {
            return null;
        }

        Map<String, Double> jsonIdPointsMapping = grade.getJsonIdPointsMapping();
        BigDecimal pointsSum = new BigDecimal(0);
        for (Map.Entry<String, Double> entry : jsonIdPointsMapping.entrySet()) {
            BigDecimal point = new BigDecimal(entry.getValue());
            boolean isNegative = point.doubleValue() < 0;
            // get the fractional part of the entry score and subtract 0.15 (e.g. 1.5 -> 0.35 or -1.5 -> -0.65)
            double fractionalPart = point.remainder(BigDecimal.ONE).subtract(new BigDecimal(0.15)).doubleValue();
            // remove the fractional part of the entry score (e.g. 1.5 -> 1 or -1.5 -> -1)
            point = point.setScale(0, RoundingMode.DOWN);

            if (isNegative) {
                // for negative values subtract 1 to get the lower integer value (e.g. -1.5 -> -1 -> -2)
                point = point.subtract(BigDecimal.ONE);
                // and add 1 to the fractional part to get it into the same positive range as we have for positive values (e.g. -1.5 -> -0.5 -> 0.5)
                fractionalPart += 1;
            }

            if (fractionalPart >= 0.5) {
                point = point.add(new BigDecimal(1));
            }
            else if (fractionalPart >= 0) {
                point = point.add(new BigDecimal(0.5));
            }

            jsonIdPointsMapping.put(entry.getKey(), point.doubleValue());
            pointsSum = pointsSum.add(point);
        }
        return new CompassGrade(grade.getCoverage(), grade.getConfidence(), pointsSum.doubleValue(), grade.getJsonIdCommentsMapping(), jsonIdPointsMapping);
    }

    /**
     * Add a model to an engine
     *
     * @param exerciseId the exerciseId
     * @param modelId    the modelId
     * @param model      the new model as raw string
     */
    public void addModel(long exerciseId, long modelId, String model) {
        if (!isSupported(exerciseId) || !loadExerciseIfSuspended(exerciseId)) {
            return;
        }
        compassCalculationEngines.get(exerciseId).notifyNewModel(model, modelId);
    }

    /**
     * Checks if a calculation engine for the given exerciseId already exists. If not, it tries to load a new engine.
     *
     * @param exerciseId the id of the exercise for which the calculation engine is checked/loaded
     * @return true if a calculation engine for the exercise exists or could be loaded successfully, false otherwise
     */
    private boolean loadExerciseIfSuspended(long exerciseId) {
        if (compassCalculationEngines.containsKey(exerciseId)) {
            return true;
        }
        if (studentParticipationRepository.existsByExerciseId(exerciseId)) {
            this.loadCalculationEngineForExercise(exerciseId);
            return true;
        }
        return false;
    }

    /**
     * Loads all the submissions of the given exercise from the database, creates a new calculation engine from the submissions and adds it to the list of calculation engines.
     * Afterwards, trigger the automatic assessment attempt for every submission.
     *
     * @param exerciseId the exerciseId of the exercise for which the calculation engine should be loaded
     */
    private void loadCalculationEngineForExercise(long exerciseId) {
        if (compassCalculationEngines.containsKey(exerciseId)) {
            return;
        }
        log.info("Loading Compass calculation engine for exercise {}", exerciseId);

        Set<ModelingSubmission> modelingSubmissions = getSubmissionsForExercise(exerciseId);
        CompassCalculationEngine calculationEngine = new CompassCalculationEngine(exerciseId, modelingSubmissions, hazelcastInstance);
        compassCalculationEngines.put(exerciseId, calculationEngine);
    }

    /**
     * Get all the modeling submissions with result and feedback of the given exercise
     *
     * @param exerciseId the id of the exercise for
     * @return the list of modeling submissions
     */
    private Set<ModelingSubmission> getSubmissionsForExercise(long exerciseId) {
        List<ModelingSubmission> submissions = modelingSubmissionRepository.findSubmittedByExerciseIdWithEagerResultsAndFeedback(exerciseId);
        return new HashSet<>(submissions);
    }

    /**
     * format: uniqueElements [{id} name apollonId conflicts] numberModels numberConflicts totalConfidence totalCoverage models [{id} confidence coverage conflicts]
     *
     * @param exerciseId The ID of the exercise for which to collect statistics
     * @return statistics about the UML model
     */
    public JsonObject getStatistics(long exerciseId) {
        if (!isSupported(exerciseId) || !loadExerciseIfSuspended(exerciseId)) {
            return new JsonObject();
        }
        return compassCalculationEngines.get(exerciseId).getStatistics();
    }

    // Call every night at 2:00 am to free memory for unused calculation engines (older than 1 day)
    @Scheduled(cron = "0 0 2 * * *") // execute this every night at 2:00:00 am
    private static void cleanUpCalculationEngines() {
        LoggerFactory.getLogger(CompassService.class).info("Compass evaluates the need of keeping " + compassCalculationEngines.size() + " calculation engines in memory");
        compassCalculationEngines = compassCalculationEngines.entrySet().stream()
                .filter(map -> Duration.between(map.getValue().getLastUsedAt(), LocalDateTime.now()).toDays() < DAYS_TO_KEEP_UNUSED_ENGINE)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        LoggerFactory.getLogger(CompassService.class).info("After evaluation, there are still " + compassCalculationEngines.size() + " calculation engines in memory");
    }

    /**
     * Print statistics of the modeling exercise with the given id for internal analysis.
     *
     * @param exerciseId the id of the modeling exercise for which the statistic should be printed
     */
    public void printStatistic(Long exerciseId) {
        if (!loadExerciseIfSuspended(exerciseId)) {
            return;
        }
        compassCalculationEngines.get(exerciseId).printStatistic(exerciseId,
                resultRepository.findAllWithEagerFeedbackByAssessorIsNotNullAndParticipation_ExerciseIdAndCompletionDateIsNotNull(exerciseId));
    }

    /**
     * Method to access to the compass calculation engine getModelsWaitingForAssessment() method
     * @param exerciseId the id of the exercise the models should belong to
     * @return a list of modelIds that should be assessed next
     */
    public List<Long> getCalculationEngineModelsWaitingForAssessment(Long exerciseId) {
        List<ModelingSubmission> modelingSubmissions = modelingSubmissionRepository.findSubmittedByExerciseIdWithEagerResultsAndFeedback(exerciseId);

        CompassCalculationEngine engine = compassCalculationEngines.get(exerciseId);
        engine.notifyNewModels(modelingSubmissions);

        assessAllAutomatically(modelingSubmissions.stream().map(Submission::getId).collect(Collectors.toList()), exerciseId);

        return engine.getModelsWaitingForAssessment();
    }
}
