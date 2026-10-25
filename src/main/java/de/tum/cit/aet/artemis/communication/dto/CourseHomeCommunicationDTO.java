package de.tum.cit.aet.artemis.communication.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.dto.home.CourseHomeDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseHomeCommunicationDTO(CourseHomeQuestionsDTO questions, CourseHomeDTO.@Nullable Item announcement, List<CourseHomeDTO.Item> faqs) {
}
