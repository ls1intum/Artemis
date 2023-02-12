package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

/**
 * A DTO representing information about exam users that were not found and number of saved images.
 */
public record ExamUsersNotFoundDTO(int numberOfUsersNotFound, int numberOfImagesSaved, List<String> listOfExamUserRegistrationNumbers) {
}
