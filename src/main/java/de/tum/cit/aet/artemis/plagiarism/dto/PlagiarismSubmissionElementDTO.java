package de.tum.cit.aet.artemis.plagiarism.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmissionElement;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
/**
 * @param length    where the token ends, counted in characters from its start and without line breaks. Only correct
 *                      for a token that stays on one line, which is why {@code endLine} and {@code endColumn} exist;
 *                      it is still sent because results computed before those have nothing else.
 * @param endLine   where the token ends, as reported by JPlag, or null for a result computed before this was recorded
 * @param endColumn the column the token ends at, or null for such a result
 */
public record PlagiarismSubmissionElementDTO(Long id, int column, int line, @Nullable String file, int length, @Nullable Integer endLine, @Nullable Integer endColumn) {

    /**
     * @param element the element to convert, may be null
     * @return the DTO for the given element, or null if there is none
     */
    @SuppressWarnings("deprecation")
    public static @Nullable PlagiarismSubmissionElementDTO fromElement(@Nullable PlagiarismSubmissionElement element) {
        if (element == null) {
            return null;
        }
        return new PlagiarismSubmissionElementDTO(element.getId(), element.getColumn(), element.getLine(), element.getFile(), element.getLength(), element.getEndLine(),
                element.getEndColumn());
    }
}
