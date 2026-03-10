package de.tum.cit.aet.artemis.tutorialgroup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;

/**
 * DTO for updating tutorial groups. Builds on the create DTO data with schedule information.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupUpdateDataDTO(@NotNull Long id, @NotBlank @Size(max = 19) String title, TutorialGroupDTO.TeachingAssistantDTO teachingAssistant,
        @Nullable String additionalInformation, @Nullable Integer capacity, @NotNull Boolean isOnline, @Nullable String language, @Nullable String campus,
        @Nullable TutorialGroupScheduleDTO tutorialGroupSchedule) {

    public TutorialGroupDTO toTutorialGroupDTO() {
        return new TutorialGroupDTO(id, title, teachingAssistant, additionalInformation, capacity, isOnline, language, campus);
    }

    public static TutorialGroupUpdateDataDTO from(TutorialGroup tutorialGroup) {
        return new TutorialGroupUpdateDataDTO(tutorialGroup.getId(), tutorialGroup.getTitle(), teachingAssistantFrom(tutorialGroup), tutorialGroup.getAdditionalInformation(),
                tutorialGroup.getCapacity(), tutorialGroup.getIsOnline(), tutorialGroup.getLanguage(), tutorialGroup.getCampus(),
                TutorialGroupScheduleDTO.from(tutorialGroup.getTutorialGroupSchedule()));
    }

    private static TutorialGroupDTO.TeachingAssistantDTO teachingAssistantFrom(TutorialGroup tutorialGroup) {
        if (tutorialGroup.getTeachingAssistant() == null) {
            return null;
        }
        return new TutorialGroupDTO.TeachingAssistantDTO(tutorialGroup.getTeachingAssistant().getLogin());
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorialGroupScheduleDTO(@Nullable Long id, @Nullable Integer dayOfWeek, @Nullable String startTime, @Nullable String endTime,
            @Nullable Integer repetitionFrequency, @Nullable String validFromInclusive, @Nullable String validToInclusive, @Nullable String location) {

        private static TutorialGroupScheduleDTO from(@Nullable TutorialGroupSchedule tutorialGroupSchedule) {
            if (tutorialGroupSchedule == null) {
                return null;
            }
            return new TutorialGroupScheduleDTO(tutorialGroupSchedule.getId(), tutorialGroupSchedule.getDayOfWeek(), tutorialGroupSchedule.getStartTime(),
                    tutorialGroupSchedule.getEndTime(), tutorialGroupSchedule.getRepetitionFrequency(), tutorialGroupSchedule.getValidFromInclusive(),
                    tutorialGroupSchedule.getValidToInclusive(), tutorialGroupSchedule.getLocation());
        }
    }
}
