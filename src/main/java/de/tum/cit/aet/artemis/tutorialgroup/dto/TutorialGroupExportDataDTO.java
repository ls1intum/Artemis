package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupService;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupExportDataDTO(Long id, String title, String dayOfWeek, String startTime, String endTime, String location, String campus, String language,
        String additionalInformation, Integer capacity, Boolean isOnline, List<TutorialGroupService.StudentExportDTO> students /* optional, only set if selected */) {
}
