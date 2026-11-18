package de.tum.cit.aet.artemis.plagiarism.dto;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;

/**
 * A DTO with a subset of Plagiarism Case fields for displaying relevant info to a student.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismCaseDTO(Long id, PlagiarismVerdict verdict, Long studentId) {

    /**
     * Creates a {@link PlagiarismCaseDTO} from a {@link PlagiarismCase} entity.
     *
     * @param plagiarismCase the plagiarism case entity
     * @return a DTO containing the relevant plagiarism case information
     */
    public static PlagiarismCaseDTO of(@NonNull PlagiarismCase plagiarismCase) {
        return new PlagiarismCaseDTO(plagiarismCase.getId(), plagiarismCase.getVerdict(), plagiarismCase.getStudent() != null ? plagiarismCase.getStudent().getId() : null);
    }
}
