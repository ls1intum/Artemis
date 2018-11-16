package de.tum.in.www1.artemis.service.compass;

import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.repository.JsonAssessmentRepository;
import de.tum.in.www1.artemis.repository.JsonModelRepository;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Used to clean compass backup data
 */
@Ignore
public class PrepareData {
    private JsonAssessmentRepository assessmentRepository;
    private JsonModelRepository modelRepository;

    public PrepareData() {
        modelRepository = new JsonModelRepository();
        assessmentRepository = new JsonAssessmentRepository();
    }

    /**
     * Setup compass file link for testing
     */
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

    /**
     * Delete compass link
     */
    @AfterClass
    public static void deleteLink() {
        try {
            Files.delete(Paths.get(Constants.FILEPATH_COMPASS));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Delete models for which a student to model mapping cannot be established
     */
    @Test
    public void cleanData() {
        final long exerciseId = 256;
        Map<Long, Long> modelToStudentIdMapping = modelToStudentIdMapping(Paths.get(Constants.FILEPATH_COMPASS + File.separator + exerciseId), "model");

        Map<Long, JsonObject> models = modelRepository.readModelsForExercise(exerciseId);
        Map<Long, JsonObject> assessmentsManual = assessmentRepository.readAssessmentsForExercise(exerciseId, true);
        Map<Long, JsonObject> assessmentsAutomatic = assessmentRepository.readAssessmentsForExercise(exerciseId, false);

        for (long modelId: models.keySet()) {
            if (!assessmentsManual.containsKey(modelId) && !assessmentsAutomatic.containsKey(modelId)) {
                this.modelRepository.deleteModel(exerciseId, modelToStudentIdMapping.get(modelId), modelId);
                System.out.println("Deleted model: " + modelId + " for student: " + modelToStudentIdMapping.get(modelId));
            }
        }
    }

    /**
     *
     * @param exercisePath path of compass exercise files
     * @param filenameContains string to be matched by the file name
     * @return mapping of model Ids to studentsIds
     */
    static Map<Long, Long> modelToStudentIdMapping(Path exercisePath, String filenameContains) {
        if (Files.notExists(exercisePath)) {
            return new HashMap<>();
        }
        Map <Long, Long> modelToStudentIdMapping = new HashMap<>();
        try {
            Files.walk(exercisePath).filter(p -> Files.isRegularFile(p) && p.toString().contains(filenameContains))
                .forEach(p -> {
                    try {
                        FileReader fileReader = new FileReader(p.toFile());
                        modelToStudentIdMapping.put(Long.valueOf(p.getFileName().toString().split("\\.")[1]), Long.valueOf(p.getName(2).toString()));
                        fileReader.close();
                    } catch (IOException | NumberFormatException e) {
                        e.printStackTrace();
                    }
                });
            return modelToStudentIdMapping;
        } catch (IOException e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }
}
