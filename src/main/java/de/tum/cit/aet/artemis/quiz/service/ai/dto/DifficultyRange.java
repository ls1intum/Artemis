package de.tum.cit.aet.artemis.quiz.service.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record DifficultyRange(@Min(1) @Max(5) Integer min, @Min(1) @Max(5) Integer max) {
}
