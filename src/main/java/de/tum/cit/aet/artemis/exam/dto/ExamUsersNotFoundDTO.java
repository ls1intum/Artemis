package de.tum.cit.aet.artemis.exam.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A DTO representing information about exam users that were not found and number of saved images.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamUsersNotFoundDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int numberOfUsersNotFound,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int numberOfImagesSaved, List<String> listOfExamUserRegistrationNumbers) {
}
