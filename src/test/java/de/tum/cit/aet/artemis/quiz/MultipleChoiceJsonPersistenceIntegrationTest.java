package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;

class MultipleChoiceJsonPersistenceIntegrationTest extends AbstractQuizExerciseIntegrationTest {

    private static final String TEST_PREFIX = "mcjsonpersistence";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testMultipleChoiceAnswerOptionsArePersistedAsOrderedJsonComponents() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        MultipleChoiceQuestion question = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().stream().filter(MultipleChoiceQuestion.class::isInstance).findFirst()
                .orElseThrow();

        Object rawJson = jdbcTemplate.queryForObject("SELECT answer_options FROM quiz_question WHERE id = ?", Object.class, question.getId());
        JsonNode answerOptions = objectMapper.readTree(asJson(rawJson));

        assertThat(answerOptions).hasSize(2);
        assertThat(answerOptions.get(0).get("id").asLong()).isEqualTo(question.getAnswerOptions().getFirst().getId());
        assertThat(answerOptions.get(0).get("text").asText()).isEqualTo("A");
        assertThat(answerOptions.get(0).get("hint").asText()).isEqualTo("H1");
        assertThat(answerOptions.get(0).get("explanation").asText()).isEqualTo("E1");
        assertThat(answerOptions.get(0).get("isCorrect").asBoolean()).isTrue();
        assertThat(answerOptions.get(0).get("invalid").asBoolean()).isFalse();

        assertThat(answerOptions.get(1).get("id").asLong()).isEqualTo(question.getAnswerOptions().get(1).getId());
        assertThat(answerOptions.get(1).get("text").asText()).isEqualTo("B");
        assertThat(answerOptions.get(1).get("isCorrect").asBoolean()).isFalse();
        assertThat(answerOptions.get(1).get("invalid").asBoolean()).isFalse();

        Long nextComponentId = jdbcTemplate.queryForObject("SELECT next_component_id FROM quiz_question WHERE id = ?", Long.class, question.getId());
        assertThat(nextComponentId).isEqualTo(3L);

        assertThat(tableExists("answer_option")).isFalse();
        assertThat(columnExists("quiz_question", "answer_options")).isTrue();
        assertThat(columnExists("quiz_question", "next_component_id")).isTrue();
        assertThat(columnExists("quiz_question", "version")).isTrue();
    }

    private static String asJson(Object rawJson) {
        if (rawJson instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return rawJson.toString();
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE lower(table_name) = lower(?)", Integer.class, tableName);
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE lower(table_name) = lower(?) AND lower(column_name) = lower(?)",
                Integer.class, tableName, columnName);
        return count != null && count > 0;
    }
}
