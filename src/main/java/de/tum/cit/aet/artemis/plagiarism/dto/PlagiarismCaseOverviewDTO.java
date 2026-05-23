package de.tum.cit.aet.artemis.plagiarism.dto;

import java.time.ZonedDateTime;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismCaseOverviewDTO(Long id, PlagiarismCaseExerciseDTO exercise, PlagiarismCaseUserDTO student, PlagiarismCasePostSummaryDTO post, PlagiarismVerdict verdict,
        ZonedDateTime verdictDate, PlagiarismCaseUserDTO verdictBy, int plagiarismSubmissionCount, boolean createdByContinuousPlagiarismControl) {

    public static PlagiarismCaseOverviewDTO ofOverview(PlagiarismCase plagiarismCase) {
        if (plagiarismCase == null) {
            return null;
        }

        int plagiarismSubmissionCount = 0;
        if (plagiarismCase.getPlagiarismSubmissions() != null && Hibernate.isInitialized(plagiarismCase.getPlagiarismSubmissions())) {
            plagiarismSubmissionCount = plagiarismCase.getPlagiarismSubmissions().size();
        }

        return new PlagiarismCaseOverviewDTO(plagiarismCase.getId(), PlagiarismCaseExerciseDTO.fromExercise(plagiarismCase.getExercise()),
                PlagiarismCaseUserDTO.fromUser(plagiarismCase.getStudent()), PlagiarismCasePostSummaryDTO.fromPost(plagiarismCase.getPost()), plagiarismCase.getVerdict(),
                plagiarismCase.getVerdictDate(), PlagiarismCaseUserDTO.fromUser(plagiarismCase.getVerdictBy()), plagiarismSubmissionCount,
                plagiarismCase.isCreatedByContinuousPlagiarismControl());
    }
}
