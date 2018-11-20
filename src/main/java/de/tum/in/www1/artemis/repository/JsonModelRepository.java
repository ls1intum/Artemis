package de.tum.in.www1.artemis.repository;

import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.config.Constants;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@SuppressWarnings("unused")
@Repository
public class JsonModelRepository extends JsonFileSystemRepository {

    private Path getPath(long exerciseId, long studentId, long modelId) {
        return Paths.get(Constants.FILEPATH_COMPASS + File.separator + exerciseId + File.separator + studentId +
            File.separator + "model." + modelId + ".json");
    }

    /**
     * Write a model with the following attributes
     *
     * @param exerciseId exerciseId
     * @param studentId studentId
     * @param modelId modelId
     * @param model the model as string in a JSON format
     * @return success / failure
     */
    public boolean writeModel(long exerciseId, long studentId, long modelId, String model) {
        return this.write(this.getPath(exerciseId, studentId, modelId), model);
    }

    /**
     * Delete a model with the following attributes
     *
     * @param exerciseId exerciseId
     * @param studentId studentId
     * @param modelId modelId
     * @return success / failure
     */
    public boolean deleteModel(long exerciseId, long studentId, long modelId) {
        return this.delete(this.getPath(exerciseId, studentId, modelId));
    }

    /**
     * Check a model with the following attributes
     *
     * @param exerciseId exerciseId
     * @param studentId studentId
     * @param modelId modelId
     * @return the model
     */
    public JsonObject readModel(long exerciseId, long studentId, long modelId) {
        return this.read(this.getPath(exerciseId, studentId, modelId));
    }

    /**
     * Read all models for this exercise for a specific student
     *
     * @param exerciseId exerciseId
     * @param studentId studentId
     * @return a map of modelIds to models
     */
    public Map<Long, JsonObject> readModelsForExerciseAndStudent(long exerciseId, long studentId) {
        return this.readInFolder(Paths.get(Constants.FILEPATH_COMPASS + File.separator + exerciseId +
            File.separator + studentId), "model");
    }

    /**
     * Read all models for this exercise
     *
     * @param exerciseId exerciseId
     * @return a map of modelIds to models
     */
    public Map<Long, JsonObject> readModelsForExercise(long exerciseId) {
        return this.readInFolder(Paths.get(Constants.FILEPATH_COMPASS + File.separator + exerciseId), "model");
    }

    /**
     * Check if the model with the following attributes exist
     *
     * @param exerciseId exerciseId
     * @param studentId studentId
     * @param modelId modelId
     *
     * @return exists / does not exist
     */
    public boolean exists(long exerciseId, long studentId, long modelId) {
        return this.exists(this.getPath(exerciseId, studentId, modelId));
    }

    /**
     * check if the exercise exists
     *
     * @param exerciseId The id of the exercise to check
     * @return exists / does not exist
     */
    public boolean exerciseExists(long exerciseId) {
        return this.exists(Paths.get(Constants.FILEPATH_COMPASS + File.separator + exerciseId));
    }
}
