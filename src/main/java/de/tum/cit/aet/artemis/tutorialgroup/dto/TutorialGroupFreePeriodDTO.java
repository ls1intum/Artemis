package de.tum.cit.aet.artemis.tutorialgroup.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupFreePeriod;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupFreePeriodDTO(Long id, @Nullable String start, @Nullable String end, @Nullable String reason) {

    public static TutorialGroupFreePeriodDTO from(TutorialGroupFreePeriod freePeriod) {
        if (freePeriod == null) {
            return null;
        }
        return new TutorialGroupFreePeriodDTO(freePeriod.getId(), freePeriod.getStart() == null ? null : freePeriod.getStart().toString(),
                freePeriod.getEnd() == null ? null : freePeriod.getEnd().toString(), freePeriod.getReason());
    }
}
