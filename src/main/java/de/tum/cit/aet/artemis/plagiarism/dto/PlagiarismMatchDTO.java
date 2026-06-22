package de.tum.cit.aet.artemis.plagiarism.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismMatch;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismMatchDTO(int startA, int startB, int length) {

    public static @Nullable PlagiarismMatchDTO fromMatch(@Nullable PlagiarismMatch match) {
        if (match == null) {
            return null;
        }
        return new PlagiarismMatchDTO(match.getStartA(), match.getStartB(), match.getLength());
    }
}
