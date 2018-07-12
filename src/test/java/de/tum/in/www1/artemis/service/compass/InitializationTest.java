package de.tum.in.www1.artemis.service.compass;

import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.repository.JsonAssessmentRepository;
import de.tum.in.www1.artemis.repository.JsonModelRepository;
import de.tum.in.www1.artemis.service.compass.controller.SimilarityDetector;
import de.tum.in.www1.artemis.service.compass.grade.Grade;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLModel;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Make sure to back up your compass folder before running this Test!
 */
@Deprecated
@Ignore
public class InitializationTest {

    private CompassCalculationEngine compassCalculationEngine;
    private JsonAssessmentRepository assessmentRepository;
    private JsonModelRepository modelRepository;

    public InitializationTest() {
        modelRepository = new JsonModelRepository();
        assessmentRepository = new JsonAssessmentRepository();
        compassCalculationEngine = new CompassCalculationEngine(new HashMap<>(), new HashMap<>());
    }

    @BeforeClass
    public static void createLink() {
        // If test has been interrupted make sure to not delete the test folder
        if (Files.exists(Paths.get(Constants.FILEPATH_COMPASS))) {
            deleteLink();
        }

        Path newLink = Paths.get(Constants.FILEPATH_COMPASS);
        // Clean directory
        FileSystemUtils.deleteRecursively(newLink.toFile());

        try {
            Files.createSymbolicLink(newLink, Paths.get("src/test/resources/compass"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @AfterClass
    public static void deleteLink() {
        try {
            Files.delete(Paths.get(Constants.FILEPATH_COMPASS));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadExercise(int exerciseId) {
        Map<Long, JsonObject> models = modelRepository.readModelsForExercise(exerciseId);
        Map<Long, JsonObject> assessments = assessmentRepository.readAssessmentsForExercise(exerciseId, true);
        compassCalculationEngine = new CompassCalculationEngine(models, assessments);
    }

    @Test
    public void loadExercise() {
        loadExercise(1);
        assessModel(0.6150094212962961, 0.6449579831932772, 1, 5L, 1);
    }

    @Test
    public void testNewJsonFormat() {
        loadExercise(7);
    }

    private void assessModel(double diversity, double coverage, double confidence, Long optimalModel, int entirelyAssessed) {

        assertThat(SimilarityDetector.diversity(compassCalculationEngine.getUmlModelCollection())).isCloseTo(diversity, offset(0.000001));

        compassCalculationEngine.assessModelsAutomatically();

        assertThat(compassCalculationEngine.getTotalCoverage()).isCloseTo(coverage, offset(0.000001));
        assertThat(compassCalculationEngine.getTotalConfidence()).isCloseTo(confidence, offset(0.000001));

        assertThat(compassCalculationEngine.getNextOptimalModel().getKey()).isEqualTo(optimalModel);
        assertThat(compassCalculationEngine.getModelsWaitingForAssessment().keySet()).contains(optimalModel);

        int entirely = 0;
        for (UMLModel model : compassCalculationEngine.getUmlModelCollection()) {
            if (model.isEntirelyAssessed()) {
                entirely++;
            }
        }
        assertThat(entirely).isEqualTo(entirelyAssessed);
    }

    // TODO new JSON format
    @Test
    public void simulation() {
        loadExercise(3);
        assessModel(0.3253532453199714,0,0, 0L, 0);
    }

    @Test
    public void performanceTest() {
        int exerciseId = 99;
        if (!Paths.get(Constants.FILEPATH_COMPASS + File.separator + exerciseId).toFile().exists()) {
            return;
        }

        // System.gc();
        /*try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long beforeUsedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();*/
        long startTime = System.currentTimeMillis();

        loadExercise(exerciseId);
        System.out.println("Time passed: " + (System.currentTimeMillis() - startTime));

        //System.out.println(compassService.getNextOptimalModel(99));
        //System.out.println("Time passed: " + (System.currentTimeMillis() - startTime));

         /*System.out.println(compassService.getNextOptimalModel(99));
         System.out.println(compassService.getNextOptimalModel(99));
         System.out.println(compassService.getNextOptimalModel(99));
         System.out.println(compassService.getNextOptimalModel(99));
         System.out.println(compassService.getNextOptimalModel(99));
         System.out.println(compassService.getNextOptimalModel(99));
         System.out.println(compassService.getNextOptimalModel(99));
         System.out.println(compassService.getNextOptimalModel(99));*/

        //compassService.getResultForModel(99,2,1);
        //System.out.println("Time passed: " + (System.currentTimeMillis() - startTime));

        JsonObject model = modelRepository.readModel(exerciseId, 2, 1);
        //System.out.println("Time passed: " + (System.currentTimeMillis() - startTime));
        compassCalculationEngine.notifyNewAssessment(model.toString(), 9999);
        //System.out.println("Time passed: " + (System.currentTimeMillis() - startTime));

        JsonObject assessment = assessmentRepository.readAssessment(exerciseId, 2, 1,true);
        //System.out.println("Time passed: " + (System.currentTimeMillis() - startTime));
        compassCalculationEngine.notifyNewAssessment(assessment.toString(), 9999);
        //System.out.println("Time passed: " + (System.currentTimeMillis() - startTime));

        /* System.gc();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } */

        // long afterUsedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        // System.out.println("Memory usage: " + (afterUsedMem - beforeUsedMem));
    }

    // TODO new JSON format
    @Test
    public void simulationAndAssessments() {
        final long optimalModel1 = 3;
        final long optimalModel2 = 41;
        final int exerciseId = 3;

        loadExercise(exerciseId);

        Map<Long, JsonObject> assessments = assessmentRepository.readAssessmentsForExercise(4, true);
        compassCalculationEngine.notifyNewAssessment(assessments.get(optimalModel1).toString(), optimalModel1);
        assessmentRepository.deleteAssessment(exerciseId, 2, optimalModel1, true);

        assessModel(0.3253532453199714,0.5220506706486194,1, optimalModel2, 4);


        int entirely = -1;
        while (entirely < 42 || compassCalculationEngine.getTotalConfidence() < 0.92) {

            long optimalModel = entirely == -1 ? optimalModel2 : compassCalculationEngine.getNextOptimalModel().getKey();
            System.out.println("next optimal model: " + optimalModel);

            compassCalculationEngine.notifyNewAssessment(assessments.get(optimalModel).toString(), optimalModel);
            assessmentRepository.deleteAssessment(exerciseId, 2, optimalModel, true);

            System.out.println("confidence: " + compassCalculationEngine.getTotalConfidence());
            System.out.println("coverage: " + compassCalculationEngine.getTotalCoverage());

            entirely = 0;
            for (UMLModel model : compassCalculationEngine.getUmlModelCollection()) {
                if (model.isEntirelyAssessed()) {
                    entirely++;
                }
            }
            System.out.println("entirely assessed: " + entirely + "\n");
        }

        //assessModel(0.3446899165623320,1,0.8714481506468282, exerciseId, 44L, 45);
    }

    @Test
    public void analyse() throws InterruptedException {
        int modelId1 = 1;
        int modelId2 = 3;
        int studentId = 2;
        int exerciseId = 6;

        CompassService compassService = new CompassService(assessmentRepository, modelRepository, null, null);
        compassService.loadExercise(exerciseId);

        compassCalculationEngine = (CompassCalculationEngine) compassService.getEngine(exerciseId);

        try {
            FileSystemUtils.copyRecursively(Paths.get(Constants.FILEPATH_COMPASS + File.separator + "5").toFile(),
                Paths.get(Constants.FILEPATH_COMPASS + File.separator + exerciseId).toFile());
        } catch (IOException e) {
            e.printStackTrace();
        }

        JsonObject model = modelRepository.readModel(5, studentId, modelId1);
        compassCalculationEngine.notifyNewModel(model.toString(), modelId1);

        System.out.println(" - - - \nwaiting: ");
        for (long key: compassService.getModelsWaitingForAssessment(exerciseId)) {
            System.out.println(key);
        }
        Thread.sleep(50);

        model = modelRepository.readModel(5, studentId, modelId2);
        compassCalculationEngine.notifyNewModel(model.toString(), modelId2);

        System.out.println(" - - - \nwaiting: ");
        for (long key: compassService.getModelsWaitingForAssessment(exerciseId)) {
            System.out.println(key);
        }
        Thread.sleep(50);
        System.out.println(" - - - \nwaiting: ");
        for (long key: compassService.getModelsWaitingForAssessment(exerciseId)) {
            System.out.println(key);
        }

        JsonObject assessment = assessmentRepository.readAssessment(5, studentId, modelId1,true);
        compassCalculationEngine.notifyNewAssessment(assessment.toString(), modelId1);
        assessment = assessmentRepository.readAssessment(5, studentId, modelId2,true);
        compassCalculationEngine.notifyNewAssessment(assessment.toString(), modelId2);

        System.out.println(" - - - \nwaiting: ");
        for (long key: compassService.getModelsWaitingForAssessment(exerciseId)) {
            System.out.println(key);
        }

        Grade grade1 = compassCalculationEngine.getResultForModel(modelId1);
        Grade grade2 = compassCalculationEngine.getResultForModel(modelId2);

        FileSystemUtils.deleteRecursively(Paths.get(Constants.FILEPATH_COMPASS + File.separator + exerciseId).toFile());
    }

    @Test
    public void realWorld() {
        int modelId = 1490;
        int exerciseIdAssessments = 127;
        int studentId = 2;
        int exerciseId = 126;

        loadExercise(exerciseId);

        for (int i=0; i < 5; i++) {
            JsonObject assessment = assessmentRepository.readAssessment(exerciseIdAssessments, studentId, modelId + i,true);
            compassCalculationEngine.notifyNewAssessment(assessment.toString(), modelId + i);
            System.out.println(compassCalculationEngine.getTotalCoverage());
            System.out.println(compassCalculationEngine.getTotalConfidence());
            for (int j=0; j < 5; j++) {
                Grade grade = compassCalculationEngine.getResultForModel(modelId + j);
                if (grade != null) {
                    System.out.println("Model: " + (modelId + j) + " - Points: " + grade.getPoints());
                }
            }
            Object optimalModel = compassCalculationEngine.getNextOptimalModel();
            System.out.println(" ---------- \n");
        }
    }

}
