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

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.ModelingExerciseRepository;
import de.tum.in.www1.artemis.repository.ModelingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.compass.conflict.Conflict;
import de.tum.in.www1.artemis.service.compass.conflict.ConflictingResult;
import de.tum.in.www1.artemis.service.compass.grade.CompassGrade;
import de.tum.in.www1.artemis.service.compass.grade.Grade;

@Service
public class CompassService {

    private final Logger log = LoggerFactory.getLogger(CompassService.class);

    private final ResultRepository resultRepository;

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final ModelingSubmissionRepository modelingSubmissionRepository;

    private final ParticipationRepository participationRepository;

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
            ParticipationRepository participationRepository) {
        this.resultRepository = resultRepository;
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.participationRepository = participationRepository;
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
     * @param exerciseId the exerciseId
     * @return List of model Ids waiting for an assessment by an assessor
     */
    public Set<Long> getModelsWaitingForAssessment(long exerciseId) {
        if (!loadExerciseIfSuspended(exerciseId)) {
            return new HashSet<>();
        }

        // TODO: double check that the returned modelSubmissions (respectively their ids) do not have a result yet

        Map<Long, Grade> optimalModels = compassCalculationEngines.get(exerciseId).getModelsWaitingForAssessment();
        if (optimalModels.size() < NUMBER_OF_OPTIMAL_MODELS) {
            Map.Entry<Long, Grade> optimalModel = this.getNextOptimalModel(exerciseId);
            if (optimalModel != null) {
                optimalModels.put(optimalModel.getKey(), optimalModel.getValue());
            }
        }
        return optimalModels.keySet();
    }

    /**
     * Mark a model as unassessed, i.e. indicating that it (still) needs to be assessed. By that it is not locked anymore and can be returned for assessment by Compass again.
     *
     * @param modelingExercise  the corresponding exercise
     * @param modelSubmissionId the id of the model submission which should be marked as unassessed
     */
    public void markModelAsUnassessed(ModelingExercise modelingExercise, long modelSubmissionId) {
        if (!isSupported(modelingExercise.getDiagramType()) || !loadExerciseIfSuspended(modelingExercise.getId())) {
            return;
        }
        compassCalculationEngines.get(modelingExercise.getId()).markModelAsUnassessed(modelSubmissionId);
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

    public List<Conflict> getConflicts(ModelingSubmission modelingSubmission, long exerciseId, Result result, List<Feedback> modelingAssessment) {
        CompassCalculationEngine engine = getCalculationEngine(exerciseId);
        Map<String, List<Feedback>> elementConflictingFeedbackMapping = engine.getConflictingFeedbacks(modelingSubmission, modelingAssessment);
        List<Conflict> conflicts = new LinkedList<>();
        elementConflictingFeedbackMapping.forEach((elementID, feedbacks) -> {
            Set<ConflictingResult> elementResultMap = new HashSet<>();
            feedbacks.forEach(feedback -> elementResultMap.add(new ConflictingResult(feedback.getReferenceElementId(), feedback.getResult())));
            Conflict conflict = new Conflict();
            conflict.setModelElementId(elementID);
            conflict.setResult(result);
            conflict.setConflictingResults(elementResultMap);
            conflicts.add(conflict);
        });
        return conflicts;
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
        Optional<ModelingSubmission> modelingSubmission = modelingSubmissionRepository.findById(modelId);
        if (!modelingSubmission.isPresent()) {
            log.error("No modeling submission with ID {} could be found.", modelId);
            return;
        }
        Result result = resultRepository.findDistinctWithFeedbackBySubmissionId(modelId)
                .orElse(new Result().submission(modelingSubmission.get()).participation(modelingSubmission.get().getParticipation()));
        // only automatically assess when there is not yet an assessment.
        if (result.getAssessmentType() != AssessmentType.MANUAL && result.getAssessor() == null) {
            Grade grade = engine.getGradeForModel(modelId);
            if (grade.getCoverage() >= 1) {
                return;
            }
            ModelingExercise modelingExercise = modelingExerciseRepository.findById(result.getParticipation().getExercise().getId()).get();

            // Workaround for ignoring automatic assessments of unsupported modeling exercise types TODO remove this after adapting compass
            if (!isSupported(modelingExercise.getDiagramType())) {
                return;
            }
            // Round compass grades to avoid machine precision errors, make the grades more readable and give a slight advantage which makes 100% scores easier reachable
            // see: https://confluencebruegge.in.tum.de/display/ArTEMiS/Feature+suggestions for more information
            grade = roundGrades(grade); // TODO: should we still round the grades?

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

            // TODO: do we have to set the submission before saving? when the result is loaded from the DB above, the corresponding submission is not contained (as it is lazy)
            // or do we have to save the submission additionally?
            resultRepository.save(result);
            // engine.removeModelWaitingForAssessment(modelId, true);
        }
        else {
            // Make sure next optimal model is in a valid state
            engine.removeModelWaitingForAssessment(modelId, true);
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
