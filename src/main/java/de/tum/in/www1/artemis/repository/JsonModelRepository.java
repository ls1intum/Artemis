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

    public boolean writeModel(long exerciseId, long studentId, long modelId, String model) {
        return this.write(this.getPath(exerciseId, studentId, modelId), model);
    }

    public boolean deleteModel(long exerciseId, long studentId, long modelId) {
        return this.delete(this.getPath(exerciseId, studentId, modelId));
    }

    public JsonObject readModel(long exerciseId, long studentId, long modelId) {
        return this.read(this.getPath(exerciseId, studentId, modelId));
    }

    public Map<Long, JsonObject> readModelsForExerciseAndStudent(long exerciseId, long studentId) {
        return this.readInFolder(Paths.get(Constants.FILEPATH_COMPASS + File.separator + exerciseId +
            File.separator + studentId), "model");
    }

    public Map<Long, JsonObject> readModelsForExercise(long exerciseId) {
        return this.readInFolder(Paths.get(Constants.FILEPATH_COMPASS + File.separator + exerciseId), "model");
    }

    public boolean exists(long exerciseId, long studentId, long modelId) {
        return this.exists(this.getPath(exerciseId, studentId, modelId));
    }

    public boolean exerciseExists(long exerciseId) {
        return this.exists(Paths.get(Constants.FILEPATH_COMPASS + File.separator + exerciseId));
    }
}
