package de.tum.cit.aet.artemis.tutorialgroup.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupDTO.TeachingAssistantDTO;

/**
 * DTO for updating tutorial groups. Builds on the create DTO data with schedule information.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupUpdateDataDTO(@NotNull Long id, @NotBlank @Size(min = 1, max = 19) String title, @Nullable TeachingAssistantDTO teachingAssistant,
        @Nullable String additionalInformation, @Min(1) @Nullable Integer capacity, @NotNull Boolean isOnline, @Size(min = 1, max = 256) @Nullable String language,
        @Size(min = 1, max = 256) @Nullable String campus, @Nullable TutorialGroupScheduleDTO tutorialGroupSchedule) {

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
