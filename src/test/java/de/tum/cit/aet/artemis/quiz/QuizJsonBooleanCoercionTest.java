package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.DropLocation;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerTextSelection;

/**
 * Guards the boolean encoding contract of the quiz JSON columns across database dialects.
 * <p>
 * The one-time quiz-JSON migration backfills the {@code content} / {@code selection} columns with dialect-specific SQL: PostgreSQL emits real JSON booleans ({@code true}/
 * {@code false}), but MySQL's {@code col = TRUE} expression emits integer {@code 1}/{@code 0} (e.g. {@code "invalid":1}, {@code "isCorrect":1}, {@code "correct":1}). Reading
 * either shape back into the {@code Boolean} POJO fields relies on Jackson's default scalar coercion ({@code 1 -> true}, {@code 0 -> false}), which is what Hibernate uses for
 * {@code @JdbcTypeCode(SqlTypes.JSON)} columns. This test pins that behavior so a Jackson upgrade or a custom {@code JsonMapper}/{@code CoercionConfig} that disables it fails
 * loudly here instead of silently corrupting every MySQL-migrated {@code invalid}/{@code isCorrect}/{@code correct} flag. See
 * documentation/docs/developer/guidelines/quiz-json-persistence-migration.mdx.
 */
class QuizJsonBooleanCoercionTest {

    // A default JsonMapper mirrors the mapper Hibernate uses for @JdbcTypeCode(SqlTypes.JSON) columns: ALLOW_COERCION_OF_SCALARS is enabled by default.
    private final JsonMapper objectMapper = new JsonMapper();

    @Test
    void shouldCoerceMySqlIntegerBooleanOnDropLocationInvalid() throws Exception {
        assertThat(objectMapper.readValue("{\"id\":1,\"invalid\":1}", DropLocation.class).isInvalid()).isTrue();
        assertThat(objectMapper.readValue("{\"id\":2,\"invalid\":0}", DropLocation.class).isInvalid()).isFalse();
    }

    @Test
    void shouldCoerceMySqlIntegerBooleansOnAnswerOption() throws Exception {
        AnswerOption option = objectMapper.readValue("{\"id\":1,\"text\":\"a\",\"isCorrect\":1,\"invalid\":0}", AnswerOption.class);
        assertThat(option.isIsCorrect()).isTrue();
        assertThat(option.isInvalid()).isFalse();
    }

    @Test
    void shouldCoerceMySqlIntegerBooleanOnSubmittedTextSelection() throws Exception {
        assertThat(objectMapper.readValue("{\"spotId\":1,\"text\":\"a\",\"correct\":1}", ShortAnswerTextSelection.class).getIsCorrect()).isTrue();
    }
}
