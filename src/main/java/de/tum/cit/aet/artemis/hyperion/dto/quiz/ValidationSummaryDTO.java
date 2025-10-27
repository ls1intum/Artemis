package de.tum.cit.aet.artemis.hyperion.dto.quiz;

import java.util.List;

public record ValidationSummaryDTO(boolean hasSolution, boolean hasHint, boolean difficultyValid, List<String> issues) {
}
