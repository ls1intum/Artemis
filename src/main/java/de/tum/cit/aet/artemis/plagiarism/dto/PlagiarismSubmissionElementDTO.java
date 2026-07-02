package de.tum.cit.aet.artemis.plagiarism.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmissionElement;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismSubmissionElementDTO(Long id, int column, int line, @Nullable String file, int length) {

    public static @Nullable PlagiarismSubmissionElementDTO fromElement(@Nullable PlagiarismSubmissionElement element) {
        if (element == null) {
            return null;
        }
        return new PlagiarismSubmissionElementDTO(element.getId(), element.getColumn(), element.getLine(), element.getFile(), element.getLength());
    }
}
