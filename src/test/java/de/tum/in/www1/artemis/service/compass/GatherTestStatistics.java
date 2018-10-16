package de.tum.in.www1.artemis.service.compass;

import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.repository.JsonAssessmentRepository;
import de.tum.in.www1.artemis.repository.JsonModelRepository;
import de.tum.in.www1.artemis.service.compass.assessment.Score;
import de.tum.in.www1.artemis.service.compass.controller.JSONParser;
import de.tum.in.www1.artemis.service.compass.grade.Grade;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLModel;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;

/**
 * Prints statistics about automatic assessment quality and coverage
 * !! Make sure to back up your compass folder before running this Test !!
 */
@Ignore
public class GatherTestStatistics {

    private CompassCalculationEngine compassCalculationEngine;
    private JsonAssessmentRepository assessmentRepository;
    private JsonModelRepository modelRepository;

    private static double CONFIDENCE_THRESHOLD = 0.75;
    private final static double COVERAGE_THRESHOLD = 0.8;

    public GatherTestStatistics() {
        modelRepository = new JsonModelRepository();
        assessmentRepository = new JsonAssessmentRepository();
        compassCalculationEngine = new CompassCalculationEngine(new HashMap<>(), new HashMap<>());
    }

    @BeforeClass
    public static void createLink() {
        PrepareData.createLink();
    }

    @AfterClass
    public static void deleteLink() {
        PrepareData.deleteLink();
    }

    private void loadExercise(int exerciseId) {
        Map<Long, JsonObject> models = modelRepository.readModelsForExercise(exerciseId);
        // Do not load manual assessments
        Map<Long, JsonObject> assessments = new HashMap<>();
        compassCalculationEngine = new CompassCalculationEngine(models, assessments);
    }

    private void loadExerciseComplete(int exerciseId) {
        Set<Long> toRemove = new HashSet<>();
        Map<Long, JsonObject> models = modelRepository.readModelsForExercise(exerciseId);
        // Load manual assessments
        Map<Long, JsonObject> assessments = assessmentRepository.readAssessmentsForExercise(exerciseId, true);
        for (long modelId: models.keySet()) {
            if (!assessments.containsKey(modelId)) {
                toRemove.add(modelId);
            }
        }
        for (long remove: toRemove) {
            models.remove(remove);
        }
        compassCalculationEngine = new CompassCalculationEngine(models, assessments);
    }

    /**
     * Ignore conflicts from manual assessments
     */
    @Test
    public void runCompassPerfect() {
        CONFIDENCE_THRESHOLD = 0;
        this.runCompass(false);
    }

    /**
     * Run compass with standard configuration
     */
    @Test
    public void runCompassDefault() {
        CONFIDENCE_THRESHOLD = 0.75;
        this.runCompass(false);
    }

    /**
     * Run compass and bulk load all available manual assessments on initialisation
     */
    @Test
    public void runCompassManual() {
        CONFIDENCE_THRESHOLD = 0.75;
        this.runCompass(true);
    }

    private void runCompass(boolean manualAssessmentsLoaded) {
        // root folder of the exercise
        int exerciseId = 455; //256, 455

        if (manualAssessmentsLoaded) {
            this.loadExerciseComplete(exerciseId);
        } else {
            this.loadExercise(exerciseId);
        }

        Set<Long> gradedModels = new HashSet<>();
        Set<Long> automaticModels = new HashSet<>();
        Collection<UMLModel> models = compassCalculationEngine.getUmlModelCollection();
        Map<Long, Long> modelToStudentIdMapping = PrepareData.modelToStudentIdMapping(Paths.get(Constants.FILEPATH_COMPASS + File.separator + exerciseId), "model");

        int manualAssessments = manualAssessmentsLoaded ? models.size() : 0;

        for (int i = 0; i < models.size(); i++) {
            Map.Entry<Long, Grade> nextOptimal = this.compassCalculationEngine.getNextOptimalModel();
            if (nextOptimal == null || manualAssessmentsLoaded) {
                break;
            }

            if (!this.assessmentRepository.exists(exerciseId, modelToStudentIdMapping.get(nextOptimal.getKey()), nextOptimal.getKey(), true)) {
                System.err.println("Next optimal Model " + nextOptimal.getKey() + " not found!");
                continue;
            }

            JsonObject nextAssessment = this.assessmentRepository.readAssessment(exerciseId, modelToStudentIdMapping.get(nextOptimal.getKey()), nextOptimal.getKey(), true);

            manualAssessments++;
            this.compassCalculationEngine.notifyNewAssessment(nextAssessment.toString(), nextOptimal.getKey());
            for (UMLModel model : models) {
                if (!gradedModels.contains(model.getModelID())) {
                    Grade grade = this.compassCalculationEngine.getResultForModel(model.getModelID());
                    if (grade.getConfidence() >= CONFIDENCE_THRESHOLD && grade.getCoverage() >= COVERAGE_THRESHOLD) {
                        gradedModels.add(model.getModelID());
                        if (model.getModelID() != nextOptimal.getKey()) {
                            automaticModels.add(model.getModelID());
                        }
                        // Necessary because we use the calculation engine directly and not via compassService
                        compassCalculationEngine.removeModelWaitingForAssessment(model.getModelID(), true);
                    }
                }
            }
            // System.out.println("manual assessments: " + (i + 1));
            // System.out.println("graded models " + gradedModels.size() + "\n");
            // System.out.println("automatic graded models " + (gradedModels.size() - i - 1) + "\n");
            System.out.println(" ---- processing model " + i + " out of " + models.size() + " ---- \n");
        }
        // Automatic assessments
        System.out.println("total models " + models.size() + "\n");
        System.out.println("automatic graded models " + automaticModels.size() + "\n");
        System.out.println("manual graded models " + manualAssessments + "\n");
        System.out.println("graded models " + gradedModels.size() + "\n");

        // Other statistics
        compassCalculationEngine.printStatistic();

        // Compare manual with automatic assessments
        double scoreDifference = 0;
        int scoreDifferenceCount = 0;
        int elementCount = 0;
        double totalDifference = 0;
        double totalModelDifference = 0;
        int notFound = 0;
        Map<Long, JsonObject> allManualAssessments = assessmentRepository.readAssessmentsForExercise(exerciseId, true);
        Map<Long, UMLModel> umlModelMap = compassCalculationEngine.getModelMap();

        for (Long automaticModel : automaticModels) {
            Map<String, Score> manualScoreMap = JSONParser.getScoresFromJSON(allManualAssessments.get(automaticModel), umlModelMap.get(automaticModel));
            if (manualScoreMap.isEmpty()) {
                System.err.println("Unable to read score for model: " + automaticModel);
                notFound ++;
                continue;
            }
            Grade result = compassCalculationEngine.getResultForModel(automaticModel);
            double modelDifference = 0;
            for (Map.Entry<String, Double> entry: result.getJsonIdPointsMapping().entrySet()) {
               totalDifference += entry.getValue() - manualScoreMap.get(entry.getKey()).getPoints();
               modelDifference += entry.getValue() - manualScoreMap.get(entry.getKey()).getPoints();
               double difference = Math.abs(entry.getValue() - manualScoreMap.get(entry.getKey()).getPoints());
               if (difference != 0) {
                   scoreDifferenceCount ++;
               }
               scoreDifference += difference;
               elementCount ++;
            }
            totalModelDifference += Math.abs(modelDifference);
        }

        System.out.println("difference / element (abs): " + scoreDifference / elementCount + "\n");
        System.out.println("difference / model (abs): " + totalModelDifference / (automaticModels.size() - notFound) + "\n");
        System.out.println("models not found: " + notFound + "\n");
        System.out.println("different elements: " + scoreDifferenceCount + "\n");
        System.out.println("elements: " + elementCount + "\n");
        System.out.println("total difference (without abs): " + totalDifference + "\n");
    }
}
