package de.tum.cit.aet.artemis.exercise.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;

class ExerciseAthenaConfigDeserializationTest {

    @Test
    void shouldDeserializeAthenaConfig() throws Exception {
        String json = """
                {"type":"modeling","id":3,"title":"t","maxPoints":10,"bonusPoints":0,"assessmentType":"SEMI_AUTOMATIC","mode":"INDIVIDUAL",
                  "allowComplaintsForAutomaticAssessments":false,"allowManualFeedbackRequests":false,
                  "includedInOverallScore":"INCLUDED_COMPLETELY","problemStatement":"statement","presentationScoreEnabled":false,
                  "secondCorrectionEnabled":false,
                  "diagramType":"ClassDiagram","exerciseType":"MODELING","teamMode":false,
                  "athenaConfig":{"feedbackSuggestionModule":"module_modeling_llm","preliminaryFeedbackModule":"module_modeling_llm"}}
                """;

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new Hibernate6Module());

        ModelingExercise exercise = mapper.readValue(json, ModelingExercise.class);
        assertThat(exercise.getAthenaConfig()).isNotNull();
        assertThat(exercise.getAthenaConfig().getFeedbackSuggestionModule()).isEqualTo("module_modeling_llm");
    }
}
