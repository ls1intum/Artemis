package de.tum.in.www1.artemis.service.plagiarism;

import static java.util.Comparator.naturalOrder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.jplag.Submission;
import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmission;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;
import de.tum.in.www1.artemis.repository.SubmissionRepository;

@Component
class JPlagSubmissionDataExtractor {

    private static final Logger log = LoggerFactory.getLogger(JPlagSubmissionDataExtractor.class);

    private static final long DEFAULT_SUBMISSION_ID = 0;

    private static final String DEFAULT_STUDENT_LOGIN = "unknown";

    private final SubmissionRepository submissionRepository;

    JPlagSubmissionDataExtractor(SubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    void retrieveAndSetSubmissionIdAndStudentLogin(PlagiarismSubmission<TextSubmissionElement> submission, Submission jplagSubmission, Exercise exercise) {
        switch (exercise.getExerciseType()) {
            case TEXT -> retrieveAndSetSubmissionIdAndStudentLoginForTextExercise(submission, jplagSubmission);
            case PROGRAMMING -> retrieveAndSetSubmissionIdAndStudentLoginForProgramingExercise(submission, jplagSubmission);
            default -> throw new IllegalStateException("Unexpected exercise type: " + exercise.getExerciseType());
        }
    }

    private static void retrieveAndSetSubmissionIdAndStudentLoginForTextExercise(PlagiarismSubmission<TextSubmissionElement> submission, Submission jplagSubmission) {
        var jplagSubmissionNameData = extractJPlagSubmissionNameData(jplagSubmission);
        long submissionId = DEFAULT_SUBMISSION_ID;
        var studentLogin = DEFAULT_STUDENT_LOGIN;
        if (jplagSubmissionNameData.length >= 2) {
            try {
                submissionId = Long.parseLong(jplagSubmissionNameData[0]);
            }
            catch (NumberFormatException e) {
                log.error("Invalid participationId: {}", e.getMessage());
            }

            studentLogin = jplagSubmissionNameData[1];
        }

        submission.setSubmissionId(submissionId);
        submission.setStudentLogin(studentLogin);
    }

    private void retrieveAndSetSubmissionIdAndStudentLoginForProgramingExercise(PlagiarismSubmission<TextSubmissionElement> submission, Submission jplagSubmission) {
        var jplagSubmissionNameData = extractJPlagSubmissionNameData(jplagSubmission);
        long submissionId = DEFAULT_SUBMISSION_ID;
        var studentLogin = DEFAULT_STUDENT_LOGIN;
        if (hasParticipationIdAndSubmissionId(jplagSubmissionNameData)) {
            try {
                submissionId = Long.parseLong(jplagSubmissionNameData[1]);
            }
            catch (NumberFormatException e) {
                log.error("Invalid submissionId: {}", e.getMessage());
            }
            studentLogin = jplagSubmissionNameData[2];
        }
        else if (jplagSubmissionNameData.length >= 2) {
            // path for text exercises and legacy programing repositories where only participationId is stored in result name
            // needs to be here to handle old submissions
            try {
                var participationId = Long.parseLong(jplagSubmissionNameData[0]);
                submissionId = submissionRepository.findAllByParticipationId(participationId).stream().max(naturalOrder()).map(DomainObject::getId).orElseThrow();
            }
            catch (NumberFormatException e) {
                log.error("Invalid participationId: {}", e.getMessage());
            }

            studentLogin = jplagSubmissionNameData[1];
        }

        submission.setSubmissionId(submissionId);
        submission.setStudentLogin(studentLogin);
    }

    private static String[] extractJPlagSubmissionNameData(Submission jplagSubmission) {
        return jplagSubmission.getName().split("[-.]");
    }

    private static boolean hasParticipationIdAndSubmissionId(String[] jplagSubmissionNameData) {
        return jplagSubmissionNameData.length == 3;
    }
}
