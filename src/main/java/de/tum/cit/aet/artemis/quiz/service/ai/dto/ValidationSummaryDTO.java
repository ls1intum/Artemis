package de.tum.cit.aet.artemis.quiz.service.ai.dto;

import java.util.List;

public record ValidationSummaryDTO(boolean hasSolution, boolean hasHint, boolean difficultyValid, List<String> issues) {
}
