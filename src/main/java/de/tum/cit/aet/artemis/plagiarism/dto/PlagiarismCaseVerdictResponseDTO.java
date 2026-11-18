package de.tum.cit.aet.artemis.plagiarism.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismCaseVerdictResponseDTO(PlagiarismVerdict verdict, @Nullable String verdictMessage, int verdictPointDeduction, PlagiarismCaseUserDTO verdictBy,
        ZonedDateTime verdictDate) {

    /**
     * Maps the verdict part of a plagiarism case entity to the response DTO returned after saving a verdict.
     *
     * @param plagiarismCase the plagiarism case entity
     * @return the DTO representation
     */
    public static @Nullable PlagiarismCaseVerdictResponseDTO ofVerdict(@Nullable PlagiarismCase plagiarismCase) {
        if (plagiarismCase == null) {
            return null;
        }
        return new PlagiarismCaseVerdictResponseDTO(plagiarismCase.getVerdict(), plagiarismCase.getVerdictMessage(), plagiarismCase.getVerdictPointDeduction(),
                PlagiarismCaseUserDTO.fromUser(plagiarismCase.getVerdictBy()), plagiarismCase.getVerdictDate());
    }
}
