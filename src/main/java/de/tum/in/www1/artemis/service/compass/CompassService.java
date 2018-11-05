package de.tum.in.www1.artemis.service.compass;

import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.domain.ModelingExercise;
import de.tum.in.www1.artemis.domain.ModelingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.repository.*;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    /**
     * Map exerciseId to compass CalculationEngines
     */
    private static Map<Long, CalculationEngine> compassCalculationEngines = new ConcurrentHashMap<>();

    private final static int DAYS_TO_KEEP_UNUSED_ENGINE = 1;
    private final static int TIME_TO_CHECK_FOR_UNUSED_ENGINES = 3600000;

    private final static double CONFIDENCE_THRESHOLD = 0.75;
    private final static double COVERAGE_THRESHOLD = 0.8;

    private final static int NUMBER_OF_OPTIMAL_MODELS = 10;
    private static Map<Long, Thread> optimalModelThreads = new ConcurrentHashMap<>();

    public CompassService (JsonAssessmentRepository assessmentRepository, JsonModelRepository modelRepository,
                           ResultRepository resultRepository, ModelingExerciseRepository modelingExerciseRepository,
                           ModelingSubmissionRepository modelingSubmissionRepository) {
        this.assessmentRepository = assessmentRepository;
        this.modelRepository = modelRepository;
        this.resultRepository = resultRepository;
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
    }

    /**
     *
     * This method will return a new Entry with a new Id for every call
     *
     * @return new Id and partial grade of the optimalModel for next manual assessment, null if all models have been assessed
     */
    public Map.Entry<Long, Grade> getNextOptimalModel(long exerciseId) {
        if (!loadExerciseIfSuspended(exerciseId)) {
            return null;
        }
        return compassCalculationEngines.get(exerciseId).getNextOptimalModel();
    }

    public void removeModelWaitingForAssessment(long exerciseId, long modelId) {
        if (!loadExerciseIfSuspended(exerciseId)) {
            return;
        }
        compassCalculationEngines.get(exerciseId).removeModelWaitingForAssessment(modelId, true);
    }

    public Set<Long> getModelsWaitingForAssessment(long exerciseId) {
        if (!loadExerciseIfSuspended(exerciseId)) {
            return new HashSet<>();
        }

        Map<Long, Grade> optimalModels = compassCalculationEngines.get(exerciseId).getModelsWaitingForAssessment();
        Thread optimalModelThread = optimalModelThreads.get(exerciseId);
        if (optimalModels.size() < NUMBER_OF_OPTIMAL_MODELS && (optimalModelThread == null || !optimalModelThread.isAlive())) {
            // Spawn a new thread for populating optimalModels
            optimalModelThread = new Thread(
                () -> this.getNextOptimalModel(exerciseId)
            );
            optimalModelThreads.put(exerciseId, optimalModelThread);
            optimalModelThread.start();
        }
        return optimalModels.keySet();
    }

    public void resetModelsWaitingForAssessment(long exerciseId) {
        if (!loadExerciseIfSuspended(exerciseId)) {
            return;
        }
        Map<Long, Grade> optimalModels = compassCalculationEngines.get(exerciseId).getModelsWaitingForAssessment();
        for (long modelId: optimalModels.keySet()) {
            compassCalculationEngines.get(exerciseId).removeModelWaitingForAssessment(modelId, false);
        }
    }

    public JsonObject getPartialAssessment(long exerciseId, long modelId) {
        if (!loadExerciseIfSuspended(exerciseId)) {
            return null;
        }

        CalculationEngine engine = compassCalculationEngines.get(exerciseId);

        return engine.exportToJson(engine.getResultForModel(modelId), modelId);
    }

    /**
     * If a valid result has already produced in the past load it, otherwise calculate a new result
     *
     * Useful for testing as it does not involve the database
     *
     * @return Result object for the specific model or null if not found, or the coverage or confidence is not high enough
     */
    @SuppressWarnings("unused")
    public Grade getResultForModel(long exerciseId, long studentId, long modelId) {
        if (!loadExerciseIfSuspended(exerciseId) || !modelRepository.exists(exerciseId, studentId, modelId)) {
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

    public void addAssessment(long exerciseId, long modelId, String assessment) {
        log.info("Add assessment for exercise" + exerciseId + " and model " + modelId);
        if (!loadExerciseIfSuspended(exerciseId)) {
            return;
        }
        CalculationEngine engine = compassCalculationEngines.get(exerciseId);
        engine.notifyNewAssessment(assessment, modelId);
        // Check all models for new assessments
        for (long id: engine.getModelIds()) {
            assessAutomatically(id, exerciseId);
        }
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
        //TODO: the following line is clearly wrong because isRated was used in the wrong way :-( This should be invoked if there was not manual assessment before
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

                result.setRated(true);
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
        for (Map.Entry<String, Double> entry: jsonIdPointsMapping.entrySet()) {
            BigDecimal bd = new BigDecimal(entry.getValue());
            double fractionalPart = bd.remainder(BigDecimal.ONE).subtract(new BigDecimal(0.15)).doubleValue();
            bd = bd.setScale(0, RoundingMode.DOWN);
            if (fractionalPart >= 0.5) {
                bd = bd.add(new BigDecimal(1));
            } else if (fractionalPart >= 0) {
                bd = bd.add(new BigDecimal(0.5));
            }
            jsonIdPointsMapping.put(entry.getKey(), bd.doubleValue());
            pointsSum = pointsSum.add(bd);
        }
        return new CompassGrade(grade.getCoverage(), grade.getConfidence(), pointsSum.doubleValue(), grade.getJsonIdCommentsMapping(), jsonIdPointsMapping);
    }

    public void addModel(long exerciseId, long modelId, String model) {
        if (!loadExerciseIfSuspended(exerciseId)) {
            return;
        }
        compassCalculationEngines.get(exerciseId).notifyNewModel(model, modelId);
        assessAutomatically(modelId, exerciseId);
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

    public void loadExercise(long exerciseId) {
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
        for (long id: calculationEngine.getModelIds()) {
            assessAutomatically(id, exerciseId);
        }
    }

    private CalculationEngine getEngine(long exerciseId) {
        return compassCalculationEngines.get(exerciseId);
    }

    private void suspendEngine(long exerciseId) {
        compassCalculationEngines.remove(exerciseId);
    }

    // Call every hour and free memory for unused calculation engines (older than 1 day)
    @Scheduled(fixedRate=TIME_TO_CHECK_FOR_UNUSED_ENGINES)
    private static void cleanUpCalculationEngines() {
        LoggerFactory.getLogger(CompassService.class).info("Compass evaluates the need of keeping " + compassCalculationEngines.size() + " calculation engines in memory");
        compassCalculationEngines = compassCalculationEngines.entrySet().stream().
            filter(map -> Duration.between(map.getValue().getLastUsedAt(), LocalDateTime.now()).toDays() < DAYS_TO_KEEP_UNUSED_ENGINE)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        LoggerFactory.getLogger(CompassService.class).info("After evaluation, there are still " + compassCalculationEngines.size() + " calculation engines in memory");
    }

}
