package de.tum.in.www1.artemis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.SubmissionVersion;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.SubmissionVersionRepository;

@Service
public class SubmissionVersionService {

    private final Logger log = LoggerFactory.getLogger(SubmissionVersionService.class);

    protected SubmissionVersionRepository submissionVersionRepository;

    protected UserService userService;

    public SubmissionVersionService(SubmissionVersionRepository submissionVersionRepository, UserService userService) {
        this.submissionVersionRepository = submissionVersionRepository;
        this.userService = userService;
    }

    /**
     * Saves a version for the given submission to track its current content and author
     *
     * If the last version for this submission was made by the same user, update this version.
     * Otherwise, create a new version. This drastically reduces the number of versions that need to be created.
     *
     * @param submission Submission for which to save a version
     * @param username Username of the author of the submission update
     * @return created/updated submission version
     */
    public SubmissionVersion save(Submission submission, String username) {
        User user = userService.getUserByLogin(username).orElseThrow();

        return submissionVersionRepository.findLatestVersion(submission.getId()).map(latestVersion -> {
            if (latestVersion.getAuthor().equals(user)) {
                return updateExistingVersion(latestVersion, submission);
            }
            else {
                return createNewVersion(submission, user);
            }
        }).orElseGet(() -> createNewVersion(submission, user));
    }

    private SubmissionVersion updateExistingVersion(SubmissionVersion version, Submission submission) {
        version.setContent(getSubmissionContent(submission));
        return submissionVersionRepository.save(version);
    }

    private SubmissionVersion createNewVersion(Submission submission, User user) {
        SubmissionVersion version = new SubmissionVersion();
        version.setAuthor(user);
        version.setSubmission(submission);
        version.setContent(getSubmissionContent(submission));
        return submissionVersionRepository.save(version);
    }

    private String getSubmissionContent(Submission submission) {
        if (submission instanceof ModelingSubmission) {
            return ((ModelingSubmission) submission).getModel();
        }
        else if (submission instanceof TextSubmission) {
            return ((TextSubmission) submission).getText();
        }
        else {
            throw new IllegalArgumentException("Versioning for this submission type not supported: " + submission.getType());
        }
    }
}
