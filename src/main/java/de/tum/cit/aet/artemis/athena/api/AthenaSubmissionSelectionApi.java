package de.tum.cit.aet.artemis.athena.api;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.athena.service.AthenaSubmissionSelectionService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

@Controller
public class AthenaSubmissionSelectionApi {

    private final Optional<AthenaSubmissionSelectionService> athenaSubmissionSelectionService;

    public AthenaSubmissionSelectionApi(Optional<AthenaSubmissionSelectionService> athenaSubmissionSelectionService) {
        this.athenaSubmissionSelectionService = athenaSubmissionSelectionService;
    }

    public Optional<Long> getProposedSubmissionId(Exercise exercise, List<Long> submissionIds) {
        return getOrThrow().getProposedSubmissionId(exercise, submissionIds);
    }

    public boolean isActive() {
        return athenaSubmissionSelectionService.isPresent();
    }

    private AthenaSubmissionSelectionService getOrThrow() {
        if (athenaSubmissionSelectionService.isEmpty()) {
            throw new IllegalStateException("AthenaSubmissionSelectionService is not available");
        }

        return athenaSubmissionSelectionService.get();
    }
}
