package de.tum.in.www1.artemis.service.compass;

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

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.enumeration.EscalationState;
import de.tum.in.www1.artemis.domain.modeling.ModelAssessmentConflict;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.ModelingExerciseRepository;
import de.tum.in.www1.artemis.repository.ModelingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.ConflictingResultService;
import de.tum.in.www1.artemis.service.ModelAssessmentConflictService;
import de.tum.in.www1.artemis.service.compass.grade.CompassGrade;
import de.tum.in.www1.artemis.service.compass.grade.Grade;

@Service
public class CompassService {

    private final Logger log = LoggerFactory.getLogger(CompassService.class);

    private final ResultRepository resultRepository;

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final ModelingSubmissionRepository modelingSubmissionRepository;

    private final ParticipationRepository participationRepository;

    private final ModelAssessmentConflictService conflictService;

    private final ConflictingResultService conflictingResultService;

    /**
     * Map exerciseId to compass CalculationEngines
     */
    private static Map<Long, CalculationEngine> compassCalculationEngines = new ConcurrentHashMap<>();

    /**
     * Remove an engine from memory after it has been unused for this number of days
     */
    private static final int DAYS_TO_KEEP_UNUSED_ENGINE = 1;

    /**
     * Time to check for unused engines
     */
    private static final int TIME_TO_CHECK_FOR_UNUSED_ENGINES = 3600000;

    /**
     * Confidence and coverage parameters to accept an automatic assessment
     */
    private static final double CONFIDENCE_THRESHOLD = 0.75;

    private static final double COVERAGE_THRESHOLD = 0.8;

    /**
     * Number of optimal models to keep in cache
     */
    private static final int NUMBER_OF_OPTIMAL_MODELS = 10;

    public CompassService(ResultRepository resultRepository, ModelingExerciseRepository modelingExerciseRepository, ModelingSubmissionRepository modelingSubmissionRepository,
            ParticipationRepository participationRepository, ModelAssessmentConflictService conflictService, ConflictingResultService conflictingResultService) {
        this.resultRepository = resultRepository;
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.participationRepository = participationRepository;
        this.conflictService = conflictService;
        this.conflictingResultService = conflictingResultService;
    }

    public boolean isSupported(DiagramType diagramType) {
        // at the moment, we only support class diagrams
        return diagramType == DiagramType.ClassDiagram;
    }

    /**
     * This method will return a new Entry with a new Id for every call
     *
     * @return new Id and partial grade of the optimalModel for next manual assessment, null if all models have been assessed
     */
    // TODO CZ: do we need the Grade of the model? shouldn't it be enough to just return the model id as we do not even use the Grade after calling this method?
    private Map.Entry<Long, Grade> getNextOptimalModel(long exerciseId) {
        if (!loadExerciseIfSuspended(exerciseId)) { // TODO MJ why null?
            return null;
        }
        return compassCalculationEngines.get(exerciseId).getNextOptimalModel();
    }

    /**
     * Remove a model from the waiting list of models which should be assessed next
     *
     * @param exerciseId        the exerciseId
     * @param modelSubmissionId the id of the model submission which can be removed
     */
    public void removeModelWaitingForAssessment(long exerciseId, long modelSubmissionId) {
        if (!loadExerciseIfSuspended(exerciseId)) {
            return;
        }
        compassCalculationEngines.get(exerciseId).removeModelWaitingForAssessment(modelSubmissionId, true);
    }

    /**
     * Get the (cached) list of models that need to be assessed next. If the number of models in the list is smaller than the configured NUMBER_OF_OPTIMAL_MODELS a new "optimal"
     * model is added to the list. The models in the list are optimal in the sense of knowledge gain for Compass, helping to automatically assess as many other models as possible.
     *
     * @param exerciseId the exerciseId
     * @return List of model Ids waiting for an assessment by an assessor
     */
    public List<Long> getModelsWaitingForAssessment(long exerciseId) {
        if (!loadExerciseIfSuspended(exerciseId)) {
            return new ArrayList<>();
        }

        Map<Long, Grade> optimalModels = compassCalculationEngines.get(exerciseId).getModelsWaitingForAssessment();
        if (optimalModels.size() < NUMBER_OF_OPTIMAL_MODELS) {
            Map.Entry<Long, Grade> optimalModel = this.getNextOptimalModel(exerciseId);
            if (optimalModel != null) {
                optimalModels.put(optimalModel.getKey(), optimalModel.getValue());
            }
        }
        removeManuallyAssessedModels(optimalModels, exerciseId);
        return new ArrayList<>(optimalModels.keySet());
    }

    /**
     * Check for every model in the given list of optimal models if it is locked by another user (assessor) or if there is a manually saved or finished assessment for the
     * corresponding modeling submission. If there is, the model gets removed from the list of optimal models. This check should not be necessary as there should only be models in
     * the list that have no or only an automatic assessment. We better double check here as we want to make sure that no models with finished or manual assessments get sent to
     * other users than the assessor.
     */
    private void removeManuallyAssessedModels(Map<Long, Grade> optimalModels, long exerciseId) {
        Iterator<Long> iterator = optimalModels.keySet().iterator();
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
        if (!isSupported(modelingExercise.getDiagramType()) || !loadExerciseIfSuspended(modelingExercise.getId())) {
            return;
        }
        compassCalculationEngines.get(modelingExercise.getId()).markModelAsUnassessed(modelSubmissionId);
        assessAutomatically(modelSubmissionId, modelingExercise.getId());
    }

    /**
     * Empty the waiting list
     *
     * @param exerciseId the exerciseId
     */
    public void resetModelsWaitingForAssessment(long exerciseId) {
        if (!loadExerciseIfSuspended(exerciseId)) {
            return;
        }
        Map<Long, Grade> optimalModels = compassCalculationEngines.get(exerciseId).getModelsWaitingForAssessment();
        for (long modelSubmissionId : optimalModels.keySet()) {
            compassCalculationEngines.get(exerciseId).removeModelWaitingForAssessment(modelSubmissionId, false);
        }
    }

    /**
     * Use this if you want to reduce the effort of manual assessments
     *
     * @param exerciseId the exerciseId
     * @param submission the submission
     * @return an partial assessment for model elements of the given submission where an automatic assessment is already possible, other model elements have to be assessed by the
     *         assessor
     */
    public List<Feedback> getPartialAssessment(long exerciseId, Submission submission) {
        if (!loadExerciseIfSuspended(exerciseId)) {
            return null;
        }
        CalculationEngine engine = compassCalculationEngines.get(exerciseId);
        long modelId = submission.getId();
        return engine.convertToFeedback(engine.getGradeForModel(modelId), modelId, submission.getResult());
    }

    /**
     * Update the engine for the given exercise with a new manual assessment. Check for every model if new automatic assessments could be created with the new information.
     *
     * @param exerciseId         the id of the exercise to which the assessed submission belongs
     * @param submissionId       the id of the submission for which a new assessment is added
     * @param modelingAssessment the new assessment as a list of Feedback
     */
    public void addAssessment(long exerciseId, long submissionId, List<Feedback> modelingAssessment) {
        log.info("Add assessment for exercise " + exerciseId + " and model " + submissionId);
        if (!loadExerciseIfSuspended(exerciseId)) { // TODO rework after distinguishing between saved and submitted assessments
            return;
        }
        CalculationEngine engine = compassCalculationEngines.get(exerciseId);
        engine.notifyNewAssessment(modelingAssessment, submissionId);
        // Check all models for new assessments
        for (long id : engine.getModelIds()) {
            assessAutomatically(id, exerciseId);
        }
    }

    public List<ModelAssessmentConflict> getConflicts(ModelingSubmission modelingSubmission, long exerciseId, Result result, List<Feedback> modelingAssessment) {
        CompassCalculationEngine engine = getCalculationEngine(exerciseId);
        List<Feedback> assessmentWithoutGeneralFeedback = filterOutGeneralFeedback(modelingAssessment);
        Map<String, List<Feedback>> conflictingFeedbacks = engine.getConflictingFeedbacks(modelingSubmission, assessmentWithoutGeneralFeedback);
        List<ModelAssessmentConflict> existingUnresolvedConflicts = conflictService.getUnresolvedConflictsForResult(result);
        conflictService.updateExistingConflicts(existingUnresolvedConflicts, conflictingFeedbacks);
        conflictService.addMissingConflicts(result, existingUnresolvedConflicts, conflictingFeedbacks);
        conflictService.saveConflicts(existingUnresolvedConflicts);
        if (conflictingFeedbacks.isEmpty()) {
            return Collections.EMPTY_LIST;
        }
        else {
            return existingUnresolvedConflicts.stream().filter(conflict -> conflict.getState().equals(EscalationState.UNHANDLED)).collect(Collectors.toList());
        }
    }

    // TODO: cleanup + adjust documentation
    /**
     * Get the assessment for a given model from the calculation engine. If the confidence and coverage is high enough the assessment is added it to the corresponding result and
     * the result is saved in the database. This is done only if the submission is not assessed already (check for result.getAssessmentType() == null).
     *
     * @param modelId    the id of the model/submission that should be updated with an automatic assessment
     * @param exerciseId the id of the corresponding exercise
     */
    private void assessAutomatically(long modelId, long exerciseId) {
        CalculationEngine engine = compassCalculationEngines.get(exerciseId);

        Optional<ModelingSubmission> optionalModelingSubmission = modelingSubmissionRepository.findByIdWithEagerResultAndParticipationResults(modelId);
        if (!optionalModelingSubmission.isPresent()) {
            log.error("No modeling submission with ID {} could be found.", modelId);
            return;
        }
        ModelingSubmission modelingSubmission = optionalModelingSubmission.get();

        Result result = resultRepository.findDistinctWithFeedbackBySubmissionId(modelId)
                .orElse(new Result().submission(modelingSubmission).participation(modelingSubmission.getParticipation()));
        // only assess automatically when there is no manual assessment yet
        if (result.getAssessmentType() != AssessmentType.MANUAL && result.getAssessor() == null) {
            ModelingExercise modelingExercise = modelingExerciseRepository.findById(result.getParticipation().getExercise().getId()).get();

            // Workaround for ignoring automatic assessments of unsupported modeling exercise types TODO remove this after adapting compass
            if (!isSupported(modelingExercise.getDiagramType())) {
                return;
            }
            // Round compass grades to avoid machine precision errors, make the grades more readable and give a slight advantage which makes 100% scores easier reachable
            // see: https://confluencebruegge.in.tum.de/display/ArTEMiS/Feature+suggestions for more information
            Grade grade = roundGrades(engine.getGradeForModel(modelId)); // TODO: should we still round the grades?

            // Save to database
            List<Feedback> automaticFeedbackAssessments = engine.convertToFeedback(grade, modelId, result);
            result.getFeedbacks().clear();
            result.getFeedbacks().addAll(automaticFeedbackAssessments);
            result.setHasFeedback(false);

            // result.setRatedIfNotExceeded(modelingExercise.getDueDate(), modelingSubmission.get().getSubmissionDate());
            result.setAssessmentType(AssessmentType.AUTOMATIC);
            double maxPoints = modelingExercise.getMaxScore();
            // biased points
            double points = Math.max(Math.min(grade.getPoints(), maxPoints), 0);
            result.setScore((long) (points * 100 / maxPoints));
            // result.setCompletionDate(ZonedDateTime.now());
            result.setResultString(points, modelingExercise.getMaxScore());

            saveResult(result, modelingSubmission);
            // engine.removeModelWaitingForAssessment(modelId, true);
        }
        else {
            // Make sure next optimal model is in a valid state
            engine.removeModelWaitingForAssessment(modelId, true);
        }
    }

    /**
     * Saves the given result to the database. If the result is new (i.e. no ID), the result is additionally assigned to the given submission which is then saved to the database as
     * well.
     *
     * @param result             the result that should be saved
     * @param modelingSubmission the corresponding modeling submission
     */
    private void saveResult(Result result, ModelingSubmission modelingSubmission) {
        boolean isNewResult = result.getId() == null;

        resultRepository.save(result);

        if (isNewResult) {
            modelingSubmission.setResult(result);
            modelingSubmission.getParticipation().addResult(result);
            modelingSubmissionRepository.save(modelingSubmission);
        }
    }

    /**
     * Round compass grades to avoid machine precision errors, make the grades more readable and give a slight advantage which makes 100% scores easier reachable. Also see
     * https://confluencebruegge.in.tum.de/display/ArTEMiS/Feature+suggestions for more information.
     * <p>
     * Positive values > [x.0, x.15[ gets rounded to x.0 > [x.15, x.65[ gets rounded to x.5 > [x.65, x + 1[ gets rounded to x + 1
     * <p>
     * Negative values > [-x - 1, -x.85[ gets rounded to -x - 1 > [-x.85, -x.35[ gets rounded to -x.5 > [-x.35, -x.0[ gets rounded to -x.0
     *
     * @param grade the grade for which the points should be rounded
     * @return the rounded compass grade
     */
    private Grade roundGrades(Grade grade) {
        Map<String, Double> jsonIdPointsMapping = grade.getJsonIdPointsMapping();
        BigDecimal pointsSum = new BigDecimal(0);
        for (Map.Entry<String, Double> entry : jsonIdPointsMapping.entrySet()) {
            BigDecimal point = new BigDecimal(entry.getValue());
            boolean isNegative = point.doubleValue() < 0;
            // get the fractional part of the entry score and subtract 0.15 (e.g. 1.5 ->
            // 0.35 or -1.5 ->
            // -0.65)
            double fractionalPart = point.remainder(BigDecimal.ONE).subtract(new BigDecimal(0.15)).doubleValue();
            // remove the fractional part of the entry score (e.g. 1.5 -> 1 or -1.5 -> -1)
            point = point.setScale(0, RoundingMode.DOWN);

            if (isNegative) {
                // for negative values subtract 1 to get the lower integer value (e.g. -1.5 ->
                // -1 -> -2)
                point = point.subtract(BigDecimal.ONE);
                // and add 1 to the fractional part to get it into the same positive range as we
                // have for
                // positive values (e.g. -1.5 -> -0.5 -> 0.5)
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
        if (!loadExerciseIfSuspended(exerciseId)) {
            return;
        }
        compassCalculationEngines.get(exerciseId).notifyNewModel(model, modelId);
        assessAutomatically(modelId, exerciseId);
    }

    private CompassCalculationEngine getCalculationEngine(long exerciseId) { // TODO throw exception if exerciseId not existing
        loadExerciseIfSuspended(exerciseId);
        return (CompassCalculationEngine) compassCalculationEngines.get(exerciseId);
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
        if (participationRepository.existsByExerciseId(exerciseId)) {
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
        log.info("Loading Compass calculation engine for exercise " + exerciseId);

        Set<ModelingSubmission> modelingSubmissions = getSubmissionsForExercise(exerciseId);
        CalculationEngine calculationEngine = new CompassCalculationEngine(modelingSubmissions);
        compassCalculationEngines.put(exerciseId, calculationEngine);

        for (long id : calculationEngine.getModelIds()) {
            assessAutomatically(id, exerciseId);
        }
    }

    /**
     * Get all the modeling submissions of the given exercise
     *
     * @param exerciseId the id of the exercise for
     * @return the list of modeling submissions
     */
    private Set<ModelingSubmission> getSubmissionsForExercise(long exerciseId) {
        List<ModelingSubmission> submissions = modelingSubmissionRepository.findByExerciseIdWithEagerResultsAndFeedback(exerciseId);
        return new HashSet<>(submissions);
    }

    /**
     * format: uniqueElements [{id} name apollonId conflicts] numberModels numberConflicts totalConfidence totalCoverage models [{id} confidence coverage conflicts]
     *
     * @return statistics about the UML model
     */
    public JsonObject getStatistics(long exerciseId) {
        if (!loadExerciseIfSuspended(exerciseId)) {
            return new JsonObject();
        }
        return compassCalculationEngines.get(exerciseId).getStatistics();
    }

    /**
     * @param modelingAssessment List of feedback that gets filtered
     * @return new list with all feedbacks handed over except the ones without a reference which therefore are considered general feedback
     */
    private List<Feedback> filterOutGeneralFeedback(List<Feedback> modelingAssessment) {
        return modelingAssessment.stream().filter(feedback -> feedback.hasReference()).collect(Collectors.toList());
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
}
