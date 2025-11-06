package de.tum.cit.aet.artemis.hyperion.dto.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;

public class AiQuizGenerationRequestDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldFailValidationForTooFewQuestions() {
        var dto = new AiQuizGenerationRequestDTO("T", 0, Language.ENGLISH, DifficultyLevel.EASY, AiQuestionSubtype.SINGLE_CORRECT, "");
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void shouldFailValidationForTooManyQuestions() {
        var dto = new AiQuizGenerationRequestDTO("T", 15, Language.ENGLISH, DifficultyLevel.EASY, AiQuestionSubtype.SINGLE_CORRECT, "");
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void shouldFailValidationForTooLongPromptHint() {
        String longHint = "x".repeat(600);
        var dto = new AiQuizGenerationRequestDTO("T", 2, Language.ENGLISH, DifficultyLevel.MEDIUM, AiQuestionSubtype.SINGLE_CORRECT, longHint);
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void shouldHandleNullsInToTemplateVariables() {
        var dto = new AiQuizGenerationRequestDTO(null, 3, Language.ENGLISH, DifficultyLevel.MEDIUM, AiQuestionSubtype.MULTI_CORRECT, null);
        Map<String, String> vars = dto.toTemplateVariables();

        assertThat(vars.get("topic")).isEqualTo("");
        assertThat(vars.get("promptHint")).isEqualTo("");
    }
}
