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
public class JsonAssessmentRepository extends JsonFileSystemRepository {

    private Path getPath(long exerciseId, long studentId, long modelId, boolean manual) {
        return Paths.get(Constants.FILEPATH_COMPASS + File.separator + exerciseId + File.separator + studentId +
            File.separator + "assessment_" + (manual ? "manual" : "automatic") + "." + modelId + ".json");
    }

    public boolean writeAssessment(long exerciseId, long studentId, long modelId, boolean manual, String assessment) {
        return this.write(this.getPath(exerciseId, studentId, modelId, manual), assessment);
    }

    public boolean deleteAssessment(long exerciseId, long studentId, long modelId, boolean manual) {
        return this.delete(this.getPath(exerciseId, studentId, modelId, manual));
    }

    public JsonObject readAssessment(long exerciseId, long studentId, long modelId, boolean manual) {
        return this.read(this.getPath(exerciseId, studentId, modelId, manual));
    }

    public Map<Long, JsonObject> readAssessmentsForExerciseAndStudent(long exerciseId, long studentId, boolean manual) {
        return this.readInFolder(Paths.get(Constants.FILEPATH_COMPASS + File.separator + exerciseId +
            File.separator + studentId), "assessment_" + (manual ? "manual" : "automatic"));
    }

    public Map<Long, JsonObject> readAssessmentsForExercise(long exerciseId, boolean manual) {
        return this.readInFolder(Paths.get(Constants.FILEPATH_COMPASS + File.separator + exerciseId),
            "assessment_" + (manual ? "manual" : "automatic"));
    }

    public boolean exists(long exerciseId, long studentId, long modelId, boolean manual) {
        return this.exists(this.getPath(exerciseId, studentId, modelId, manual));
    }
}
