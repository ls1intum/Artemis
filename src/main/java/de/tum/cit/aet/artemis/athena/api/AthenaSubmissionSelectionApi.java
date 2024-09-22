package de.tum.cit.aet.artemis.athena.api;

import java.util.List;
import java.util.Optional;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.athena.service.AthenaSubmissionSelectionService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

@Controller
public class AthenaSubmissionSelectionApi extends AbstractAthenaApi {

    private final Optional<AthenaSubmissionSelectionService> optionalAthenaSubmissionSelectionService;

    public AthenaSubmissionSelectionApi(Environment environment, Optional<AthenaSubmissionSelectionService> optionalAthenaSubmissionSelectionService) {
        super(environment);
        this.optionalAthenaSubmissionSelectionService = optionalAthenaSubmissionSelectionService;
    }

    public Optional<Long> getProposedSubmissionId(Exercise exercise, List<Long> submissionIds) {
        return getOrThrow(optionalAthenaSubmissionSelectionService).getProposedSubmissionId(exercise, submissionIds);
    }
}
