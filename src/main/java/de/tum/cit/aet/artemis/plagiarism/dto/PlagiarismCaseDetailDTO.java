package de.tum.cit.aet.artemis.plagiarism.dto;

import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismCaseDetailDTO(Long id, PlagiarismCaseExerciseDTO exercise, PlagiarismCaseUserDTO student, PlagiarismCasePostSummaryDTO post, PlagiarismVerdict verdict,
        ZonedDateTime verdictDate, PlagiarismCaseUserDTO verdictBy, int plagiarismSubmissionCount, boolean createdByContinuousPlagiarismControl, String verdictMessage,
        int verdictPointDeduction, List<PlagiarismSubmissionForCaseDTO> plagiarismSubmissions) {

    public static PlagiarismCaseDetailDTO ofForInstructor(PlagiarismCase plagiarismCase) {
        return of(plagiarismCase);
    }

    public static PlagiarismCaseDetailDTO ofForStudent(PlagiarismCase plagiarismCase) {
        return of(plagiarismCase);
    }

    private static PlagiarismCaseDetailDTO of(PlagiarismCase plagiarismCase) {
        if (plagiarismCase == null) {
            return null;
        }

        List<PlagiarismSubmissionForCaseDTO> plagiarismSubmissions = null;
        if (plagiarismCase.getPlagiarismSubmissions() != null && Hibernate.isInitialized(plagiarismCase.getPlagiarismSubmissions())) {
            plagiarismSubmissions = plagiarismCase.getPlagiarismSubmissions().stream().map(PlagiarismSubmissionForCaseDTO::fromSubmissionForCase).toList();
        }
        int plagiarismSubmissionCount = plagiarismSubmissions != null ? plagiarismSubmissions.size() : 0;

        return new PlagiarismCaseDetailDTO(plagiarismCase.getId(), PlagiarismCaseExerciseDTO.fromExercise(plagiarismCase.getExercise()),
                PlagiarismCaseUserDTO.fromUser(plagiarismCase.getStudent()), PlagiarismCasePostSummaryDTO.fromPost(plagiarismCase.getPost()), plagiarismCase.getVerdict(),
                plagiarismCase.getVerdictDate(), PlagiarismCaseUserDTO.fromUser(plagiarismCase.getVerdictBy()), plagiarismSubmissionCount,
                plagiarismCase.isCreatedByContinuousPlagiarismControl(), plagiarismCase.getVerdictMessage(), plagiarismCase.getVerdictPointDeduction(), plagiarismSubmissions);
    }
}
