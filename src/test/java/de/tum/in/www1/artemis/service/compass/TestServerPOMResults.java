package de.tum.in.www1.artemis.service.compass;

import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.repository.JsonAssessmentRepository;
import de.tum.in.www1.artemis.repository.JsonModelRepository;
import de.tum.in.www1.artemis.service.compass.grade.Grade;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLModel;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Make sure to back up your compass folder before running this Test!
 */
@Ignore
public class TestServerPOMResults {

    private CompassCalculationEngine compassCalculationEngine;
    private JsonAssessmentRepository assessmentRepository;
    private JsonModelRepository modelRepository;

    private final static double CONFIDENCE_THRESHOLD = 0.75;
    private final static double COVERAGE_THRESHOLD = 0.85;

    public TestServerPOMResults() {
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
        // Do not load manual assessments
        Map<Long, JsonObject> assessments = new HashMap<>();
        compassCalculationEngine = new CompassCalculationEngine(models, assessments);
    }

    @Test
    public void RunCompass() throws IOException{
        // root folder
        int exerciseId = 455;
        int minStudentNumber = 10;
        int maxStudentNumber = 193;
        this.loadExercise(455);
        Set<Long> gradedModels = new HashSet<>();

        for (int i = 0; i < compassCalculationEngine.getUmlModelCollection().size(); i++) {
            Map.Entry<Long, Grade> nextOptimal = this.compassCalculationEngine.getNextOptimalModel();
            if (nextOptimal == null) {
                break;
            }
            JsonObject nextAssessment = null;
            for (int studentId = minStudentNumber; studentId <= maxStudentNumber; studentId ++) {
                if (this.assessmentRepository.exists(exerciseId, studentId, nextOptimal.getKey(), true)) {
                    nextAssessment = this.assessmentRepository.readAssessment(exerciseId, studentId, nextOptimal.getKey(), true);
                    break;
                }
            }
            if (nextAssessment == null) {
                throw new IOException("Next optimal Model " + nextOptimal.getKey() + " not found!");
            }
            this.compassCalculationEngine.notifyNewAssessment(nextAssessment.toString(), nextOptimal.getKey());
            for (UMLModel model: this.compassCalculationEngine.getUmlModelCollection()) {
                if (!gradedModels.contains(model.getModelID())) {
                    Grade grade = this.compassCalculationEngine.getResultForModel(model.getModelID());
                    if (grade.getConfidence() >= CONFIDENCE_THRESHOLD && grade.getCoverage() >= COVERAGE_THRESHOLD) {
                        gradedModels.add(model.getModelID());
                        // Necessary because we use the calculation engine directly and not via compassService
                        compassCalculationEngine.removeModelWaitingForAssessment(model.getModelID(), true);
                    }
                }
            }
            // System.out.println("manual assessments: " + (i + 1));
            // System.out.println("graded models " + gradedModels.size() + "\n");
            System.out.println("automatic graded models " + (gradedModels.size() - i - 1) + "\n");
        }
    }
}
