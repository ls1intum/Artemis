package de.tum.in.www1.artemis.service.compass;

import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.domain.ModelingExercise;
import de.tum.in.www1.artemis.domain.ModelingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.compass.assessment.ModelElementAssessment;
import de.tum.in.www1.artemis.service.compass.conflict.Conflict;
import de.tum.in.www1.artemis.service.compass.grade.CompassGrade;
import de.tum.in.www1.artemis.service.compass.grade.Grade;
import de.tum.in.www1.artemis.service.compass.grade.GradeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class CompassService {

    private final Logger log = LoggerFactory.getLogger(CompassService.class);
    private final JsonAssessmentRepository assessmentRepository;
    private final JsonModelRepository modelRepository;
    private final ResultRepository resultRepository;
    private final ModelingExerciseRepository modelingExerciseRepository;
    private final ModelingSubmissionRepository modelingSubmissionRepository;
    private final UserService userService;
    /**
     * Map exerciseId to compass CalculationEngines
     */
    private static Map<Long, CalculationEngine> compassCalculationEngines = new ConcurrentHashMap<>();

    /**
     * Remove an engine from memory after it has been unused for this number of days
     */
    private final static int DAYS_TO_KEEP_UNUSED_ENGINE = 1;
    /**
     * Time to check for unused engines
     */
    private final static int TIME_TO_CHECK_FOR_UNUSED_ENGINES = 3600000;

    /**
     * Confidence and coverage parameters to accept an automatic assessment
     */
    private final static double CONFIDENCE_THRESHOLD = 0.75;
    private final static double COVERAGE_THRESHOLD = 0.8;

    /**
     * Number of optimal models to keep in cache
     */
    private final static int NUMBER_OF_OPTIMAL_MODELS = 10;
    private static Map<Long, Thread> optimalModelThreads = new ConcurrentHashMap<>();

    public CompassService(JsonAssessmentRepository assessmentRepository, JsonModelRepository modelRepository, ResultRepository resultRepository, ModelingExerciseRepository modelingExerciseRepository, ModelingSubmissionRepository modelingSubmissionRepository, UserService userService) {
        this.assessmentRepository = assessmentRepository;
        this.modelRepository = modelRepository;
        this.resultRepository = resultRepository;
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.userService = userService;
    }

    /**
     * This method will return a new Entry with a new Id for every call
     *
     * @return new Id and partial grade of the optimalModel for next manual assessment, null if all models have been assessed
     */
    private Map.Entry<Long, Grade> getNextOptimalModel(long exerciseId) {
        if (!loadExerciseIfSuspended(exerciseId)) { //TODO MJ why null?
            return null;
        }
        return compassCalculationEngines.get(exerciseId).getNextOptimalModel();
    }

    /**
     * Remove a model from the waiting list of models which should be assessed next
     *
     * @param exerciseId the exerciseId
     * @param modelId    the modelId which can be removed
     */
    public void removeModelWaitingForAssessment(long exerciseId, long modelId) {
        if (!loadExerciseIfSuspended(exerciseId)) {
            return;
        }
        compassCalculationEngines.get(exerciseId).removeModelWaitingForAssessment(modelId, true);
    }

    /**
     * @param exerciseId the exerciseId
     * @return List of model Ids waiting for an assessment by an assessor
     */
    public Set<Long> getModelsWaitingForAssessment(long exerciseId) {
        if (!loadExerciseIfSuspended(exerciseId)) {
            return new HashSet<>();
        }

        Map<Long, Grade> optimalModels = compassCalculationEngines.get(exerciseId).getModelsWaitingForAssessment();
        Thread optimalModelThread = optimalModelThreads.get(exerciseId);
        if (optimalModels.size() < NUMBER_OF_OPTIMAL_MODELS && (optimalModelThread == null || !optimalModelThread.isAlive())) {
            // Spawn a new thread for populating optimalModels
            optimalModelThread = new Thread(() -> this.getNextOptimalModel(exerciseId));
            optimalModelThreads.put(exerciseId, optimalModelThread);
            optimalModelThread.start();
        }
        return optimalModels.keySet();
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
        for (long modelId : optimalModels.keySet()) {
            compassCalculationEngines.get(exerciseId).removeModelWaitingForAssessment(modelId, false);
        }
    }

    /**
     * Use this if you want to reduce the effort of manual assessments
     *
     * @param exerciseId the exerciseId
     * @param modelId    the model id
     * @return an partial assessment for model elements where an automatic assessment is already possible,
     * other model elements have to be assessed by the assessor
     */
    public JsonObject getPartialAssessment(long exerciseId, long modelId) {
        if (!loadExerciseIfSuspended(exerciseId)) {
            return null;
        }

        CalculationEngine engine = compassCalculationEngines.get(exerciseId);

        return engine.exportToJson(engine.getResultForModel(modelId), modelId);
    }

    /**
     * If a valid result has already produced in the past load it, otherwise calculate a new result
     * <p>
     * Useful for testing as it does not involve the database
     *
     * @return Result object for the specific model or null if not found, or the coverage or confidence is not high enough
     */
    @SuppressWarnings("unused")
    public Grade getResultForModel(long exerciseId, long studentId, long modelId) {
        if (!loadExerciseIfSuspended(exerciseId) || !modelRepository.exists(exerciseId, studentId, modelId)) { //TODO why null?
            return null;
        }

        JsonObject previousAssessment = assessmentRepository.readAssessment(exerciseId, studentId, modelId, false);
        CalculationEngine engine = compassCalculationEngines.get(exerciseId);

        if (previousAssessment != null) {
            return GradeParser.importFromJSON(previousAssessment);
        }

        Grade grade = engine.getResultForModel(modelId);

        if (grade.getConfidence() >= CONFIDENCE_THRESHOLD && grade.getCoverage() >= COVERAGE_THRESHOLD) {
            assessmentRepository.writeAssessment(exerciseId, studentId, modelId, false, engine.exportToJson(grade, modelId).toString());
            return grade;
        }

        return null;
    }

    /**
     * Add an assessment to an engine
     *
     * @param exerciseId         the exerciseId
     * @param submissionId       the corresponding modelId
     * @param modelingAssessment the new assessment as raw string
     */
    public void addAssessment(long exerciseId, long submissionId, List<ModelElementAssessment> modelingAssessment) {
        log.info("Add assessment for exercise " + exerciseId + " and model " + submissionId);
        if (!loadExerciseIfSuspended(exerciseId)) { //TODO rewordk after distinguishing between saved and submitted assessments on filesystem
            return;
        }
        CalculationEngine engine = compassCalculationEngines.get(exerciseId);
        engine.notifyNewAssessment(modelingAssessment, submissionId);
        // Check all models for new assessments
        for (long id : engine.getModelIds()) {
            assessAutomatically(id, exerciseId);
        }
    }

    public List<Conflict> getConflicts(long exerciseId, long submissionId, List<ModelElementAssessment> modelingAssessment) {
        CompassCalculationEngine engine = getCalculationEngine(exerciseId);
        return engine.getConflicts(submissionId, modelingAssessment);
    }

    private void assessAutomatically(long modelId, long exerciseId) {
        CalculationEngine engine = compassCalculationEngines.get(exerciseId);
        Optional<ModelingSubmission> modelingSubmission = modelingSubmissionRepository.findById(modelId);
        if (!modelingSubmission.isPresent()) {
            log.error("No modeling submission with ID {} could be found.", modelId);
            return;
        }
        Result result = resultRepository.findDistinctBySubmissionId(modelId).orElse(new Result().submission(modelingSubmission.get()).participation(modelingSubmission.get().getParticipation()));
        // only automatically assess when there is not yet an assessment.
        if (result.getAssessmentType() == null) {
            Grade grade = engine.getResultForModel(modelId);
            // automatic assessment holds confidence and coverage threshold
            if (grade.getConfidence() >= CONFIDENCE_THRESHOLD && grade.getCoverage() >= COVERAGE_THRESHOLD) {
                ModelingExercise modelingExercise = modelingExerciseRepository.findById(result.getParticipation().getExercise().getId()).get();
                /*
                 * Workaround for ignoring automatic assessments of unsupported modeling exercise types
                 * TODO remove this after adapting compass
                 */
                if (!modelingExercise.getDiagramType().equals(DiagramType.CLASS)) {
                    return;
                }
                // Round compass grades to avoid machine precision errors, make the grades more readable
                // and give a slight advantage which makes 100% scores easier reachable
                // see: https://confluencebruegge.in.tum.de/display/ArTEMiS/Feature+suggestions for more information
                grade = roundGrades(grade);
                // Save to file system + database
                JsonObject json = engine.exportToJson(grade, modelId);
                if (json == null || json.toString().isEmpty()) {
                    log.error("Unable to export automatic assessment to json");
                    return;
                }
                assessmentRepository.writeAssessment(exerciseId, result.getParticipation().getStudent().getId(), modelId,
                    false, json.toString());

                result.setRated(modelingExercise.getDueDate() == null || modelingSubmission.get().getSubmissionDate().isBefore(modelingExercise.getDueDate()));
                result.setAssessmentType(AssessmentType.AUTOMATIC);
                double maxPoints = modelingExercise.getMaxScore();
                // biased points
                double points = Math.max(Math.min(grade.getPoints(), maxPoints), 0);
                result.setScore((long) (points * 100 / maxPoints));
                result.setCompletionDate(ZonedDateTime.now());
                DecimalFormat formatter = new DecimalFormat("#.##"); // limit decimal places to 2
                result.setResultString(formatter.format(points) + " of " + formatter.format(modelingExercise.getMaxScore()) + " points");

                resultRepository.save(result);
                engine.removeModelWaitingForAssessment(modelId, true);
            } else {
                log.info("Model " + modelId + " got a confidence of " + grade.getConfidence() + " and a coverage of " + grade.getCoverage());
            }
        } else {
            // Make sure next optimal model is in a valid state
            engine.removeModelWaitingForAssessment(modelId, true);
        }
    }

    private Grade roundGrades(Grade grade) {
        Map<String, Double> jsonIdPointsMapping = grade.getJsonIdPointsMapping();
        BigDecimal pointsSum = new BigDecimal(0);
        for (Map.Entry<String, Double> entry : jsonIdPointsMapping.entrySet()) {
            BigDecimal point = new BigDecimal(entry.getValue());
            double fractionalPart = point.remainder(BigDecimal.ONE).subtract(new BigDecimal(0.15)).doubleValue();
            point = point.setScale(0, RoundingMode.DOWN);
            if (fractionalPart >= 0.5) {
                point = point.add(new BigDecimal(1));
            } else if (fractionalPart >= 0) {
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

    private CompassCalculationEngine getCalculationEngine(long exerciseId) {//TODO throw exception if exerciseId not existing
        loadExerciseIfSuspended(exerciseId);
        return (CompassCalculationEngine) compassCalculationEngines.get(exerciseId);
    }

    private boolean loadExerciseIfSuspended(long exerciseId) {
        if (compassCalculationEngines.containsKey(exerciseId)) {
            return true;
        }
        if (this.modelRepository.exerciseExists(exerciseId)) {
            this.loadExercise(exerciseId);
            return true;
        }
        return false;
    }

    private void loadExercise(long exerciseId) {
        if (compassCalculationEngines.containsKey(exerciseId)) {
            return;
        }
        log.info("Compass calculation engine for exercise " + exerciseId + " has to be load from file system");
        Map<Long, JsonObject> models = modelRepository.readModelsForExercise(exerciseId);
        models.entrySet().removeIf(entry -> {
            Optional<Result> result = resultRepository.findDistinctBySubmissionId(entry.getKey());
            return !result.isPresent();
        });
        Map<Long, JsonObject> manualAssessments = assessmentRepository.readAssessmentsForExercise(exerciseId, true);
        manualAssessments.entrySet().removeIf(entry -> !models.containsKey(entry.getKey()));
        CalculationEngine calculationEngine = new CompassCalculationEngine(models, manualAssessments);
        compassCalculationEngines.put(exerciseId, calculationEngine);
        // assess models after reload
        for (long id : calculationEngine.getModelIds()) {
            assessAutomatically(id, exerciseId);
        }
    }

    /**
     * format:
     * uniqueElements
     * [{id}
     * name
     * apollonId
     * conflicts]
     * numberModels
     * numberConflicts
     * totalConfidence
     * totalCoverage
     * models
     * [{id}
     * confidence
     * coverage
     * conflicts]
     *
     * @return statistics about the UML model
     */
    public JsonObject getStatistics(long exerciseId) {
        if (!loadExerciseIfSuspended(exerciseId)) {
            return new JsonObject();
        }
        return compassCalculationEngines.get(exerciseId).getStatistics();
    }

    // Call every hour and free memory for unused calculation engines (older than 1 day)
    @Scheduled(fixedRate = TIME_TO_CHECK_FOR_UNUSED_ENGINES)
    private static void cleanUpCalculationEngines() {
        LoggerFactory.getLogger(CompassService.class).info("Compass evaluates the need of keeping " + compassCalculationEngines.size() + " calculation engines in memory");
        compassCalculationEngines = compassCalculationEngines.entrySet().stream().
            filter(map -> Duration.between(map.getValue().getLastUsedAt(), LocalDateTime.now()).toDays() < DAYS_TO_KEEP_UNUSED_ENGINE)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        LoggerFactory.getLogger(CompassService.class).info("After evaluation, there are still " + compassCalculationEngines.size() + " calculation engines in memory");
    }

}
