package de.tum.cit.aet.artemis.plagiarism.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismCaseVerdictResponseDTO(PlagiarismVerdict verdict, String verdictMessage, int verdictPointDeduction, PlagiarismCaseUserDTO verdictBy,
        ZonedDateTime verdictDate) {

    public static PlagiarismCaseVerdictResponseDTO ofVerdict(PlagiarismCase plagiarismCase) {
        if (plagiarismCase == null) {
            return null;
        }
        return new PlagiarismCaseVerdictResponseDTO(plagiarismCase.getVerdict(), plagiarismCase.getVerdictMessage(), plagiarismCase.getVerdictPointDeduction(),
                PlagiarismCaseUserDTO.fromUser(plagiarismCase.getVerdictBy()), plagiarismCase.getVerdictDate());
    }
}
