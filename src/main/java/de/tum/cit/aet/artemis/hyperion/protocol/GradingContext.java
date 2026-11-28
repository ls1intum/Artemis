package de.tum.cit.aet.artemis.hyperion.protocol;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Core-captured grading facts needed for hidden-test validation and adaptation's total-wipe guard. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GradingContext(boolean hasDueDate, @JsonInclude Set<String> baselineGradedTestNames, @JsonInclude Set<String> baselineHiddenTestNames) {

    public GradingContext {
        baselineGradedTestNames = Set.copyOf(baselineGradedTestNames);
        baselineHiddenTestNames = baselineHiddenTestNames == null ? Set.of() : Set.copyOf(baselineHiddenTestNames);
        if (baselineGradedTestNames.size() > WorkspaceSnapshot.MAX_FILES || baselineGradedTestNames.stream().anyMatch(name -> name.isBlank() || name.length() > 1_024)
                || baselineHiddenTestNames.size() > WorkspaceSnapshot.MAX_FILES || baselineHiddenTestNames.stream().anyMatch(name -> name.isBlank() || name.length() > 1_024)) {
            throw new IllegalArgumentException("Grading baseline exceeds its bounds");
        }
    }
}
