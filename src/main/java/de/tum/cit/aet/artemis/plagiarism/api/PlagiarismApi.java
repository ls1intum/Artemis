package de.tum.cit.aet.artemis.plagiarism.api;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismService;

@Profile(PROFILE_CORE)
@Controller
public class PlagiarismApi extends AbstractPlagiarismApi {

    private final PlagiarismService plagiarismService;

    public PlagiarismApi(PlagiarismService plagiarismService) {
        this.plagiarismService = plagiarismService;
    }

    public void checkAccessAndAnonymizeSubmissionForStudent(Submission submission, String userLogin, Participation participation) {
        plagiarismService.checkAccessAndAnonymizeSubmissionForStudent(submission, userLogin, participation);
    }

    public boolean hasAccessToSubmission(Long submissionId, String userLogin, Participation participation) {
        return plagiarismService.hasAccessToSubmission(submissionId, userLogin, participation);
    }
}
