package de.tum.cit.aet.artemis.hyperion.dto.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

class GeneratedMcQuestionDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldFailValidationForEmptyTitleOrText() {
        var dto = new GeneratedMcQuestionDTO("", "", "ex", "hint", 2, Set.of(), AiQuestionSubtype.SINGLE_CORRECT, Set.of(), List.of());
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void shouldFailValidationForDifficultyTooHigh() {
        var dto = new GeneratedMcQuestionDTO("T", "Text", "ex", "hint", 10, Set.of(), AiQuestionSubtype.SINGLE_CORRECT, Set.of(), List.of());
        assertThat(validator.validate(dto)).isNotEmpty();
    }
}
