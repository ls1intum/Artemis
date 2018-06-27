package de.tum.in.www1.artemis.repository;

import com.google.gson.JsonObject;
import org.junit.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AssessmentRepositoryTest {

    @Test
    public void saveAndReadMultiple() {
        JsonAssessmentRepository repository = new JsonAssessmentRepository();

        String assessment1 = "{\"Test\":1}";
        String assessment2 = "{\"Test\":2}";

        repository.writeAssessment(2,23,1,true, assessment1);
        repository.writeAssessment(2,123,2,true, assessment2);
        repository.writeAssessment(2,123,3,false, assessment2);

        Map<Long, JsonObject> assessments = repository.readAssessmentsForExercise(2, true);

        assertThat(assessments.size()).isEqualTo(2);
        assertThat(assessments.get(1L).toString()).isEqualTo(assessment1);
        assertThat(assessments.get(2L).toString()).isEqualTo(assessment2);

        repository.deleteAssessment(2,23,1,true);
        repository.deleteAssessment(2,123,2,true);
        repository.deleteAssessment(2,123,3,false);

        assessments = repository.readAssessmentsForExercise(2, true);
        assertThat(assessments.size()).isEqualTo(0);
    }
}
