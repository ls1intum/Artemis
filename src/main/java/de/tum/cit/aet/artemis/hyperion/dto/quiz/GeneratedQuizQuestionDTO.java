package de.tum.cit.aet.artemis.hyperion.dto.quiz;

import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GeneratedQuizQuestionDTO(@NotBlank String title, @NotBlank String text, @Size(max = 500) String explanation, @Size(max = 500) String hint,
        @Min(1) @Max(5) Integer difficulty, Set<String> tags, AiQuestionSubtype subtype, Set<Long> competencyIds, List<McOptionDTO> options) {
}
