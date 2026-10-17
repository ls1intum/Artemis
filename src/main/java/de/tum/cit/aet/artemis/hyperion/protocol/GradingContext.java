package de.tum.cit.aet.artemis.hyperion.protocol;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Core-captured grading facts needed for hidden-test validation and adaptation's total-wipe guard. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GradingContext(boolean hasDueDate, @JsonInclude Set<String> baselineGradedTestNames) {

    public GradingContext {
        baselineGradedTestNames = Set.copyOf(baselineGradedTestNames);
        if (baselineGradedTestNames.size() > WorkspaceSnapshot.MAX_FILES || baselineGradedTestNames.stream().anyMatch(name -> name.isBlank() || name.length() > 1_024)) {
            throw new IllegalArgumentException("Grading baseline exceeds its bounds");
        }
    }
}
