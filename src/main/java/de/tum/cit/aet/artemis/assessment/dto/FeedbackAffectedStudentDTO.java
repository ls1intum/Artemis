package de.tum.cit.aet.artemis.assessment.dto;

public record FeedbackAffectedStudentDTO(long courseId, long participationId, String firstName, String lastName, String login, String repositoryURI) {
}
