package de.tum.in.www1.artemis.service.dto.athena;

import javax.validation.constraints.NotNull;

import de.tum.in.www1.artemis.domain.TextSubmission;

public record TextSubmissionDTO(long id, long exerciseId, String text, String language) {

    public static TextSubmissionDTO of(long exerciseId, @NotNull TextSubmission submission) {
        String language = null;
        if (submission.getLanguage() != null) {
            language = submission.getLanguage().toString();
        }
        return new TextSubmissionDTO(submission.getId(), exerciseId, submission.getText(), language);
    }
}
