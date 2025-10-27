package de.tum.cit.aet.artemis.hyperion.service.quiz.ai.dto;

import java.util.List;

public record ValidationSummaryDTO(boolean hasSolution, boolean hasHint, boolean difficultyValid, List<String> issues) {
}
