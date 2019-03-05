package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TextAssessmentService extends AssessmentService {

    private final FeedbackRepository feedbackRepository;
    private final TextSubmissionRepository textSubmissionRepository;
    private final UserService userService;

    public TextAssessmentService(FeedbackRepository feedbackRepository,
                                 ResultRepository resultRepository,
                                 TextSubmissionRepository textSubmissionRepository,
                                 UserService userService) {
        super(resultRepository);
        this.feedbackRepository = feedbackRepository;
        this.textSubmissionRepository = textSubmissionRepository;
        this.userService = userService;
    }

    /**
     * This function is used for manually assessed results. It updates the completion date, sets the assessment type to MANUAL
     * and sets the assessor attribute. Furthermore, it saves the assessment in the file system the total score is calculated and set in the result.
     *
     * @param resultId       the resultId the assessment belongs to
     * @param textExercise     the text exercise the assessment belongs to
     * @param textAssessment the assessments as a list
     * @return the ResponseEntity with result as body
     */
    @Transactional
    public Result submitAssessment(Long resultId, TextExercise textExercise, List<Feedback> textAssessment) {
        Result result = saveAssessment(resultId, textAssessment);
        Double calculatedScore = calculateTotalScore(textAssessment);

        return submitResult(result, textExercise, calculatedScore);
    }

    /**
     * This function is used for manually assessed results. It updates the completion date, sets the assessment type to MANUAL
     * and sets the assessor attribute. Furthermore, it saves the assessment in the file system the total score is calculated and set in the result.
     *
     * @param resultId       the resultId the assessment belongs to
     * @param textAssessment the assessments as string
     * @return the ResponseEntity with result as body
     */
    @Transactional
    public Result saveAssessment(Long resultId, List<Feedback> textAssessment) {
        Optional<Result> desiredResult = resultRepository.findById(resultId);
        Result result = desiredResult.orElseGet(Result::new);

        result.setAssessmentType(AssessmentType.MANUAL);
        User user = userService.getUser();
        result.setAssessor(user);

        if (result.getSubmission() instanceof TextSubmission && result.getSubmission().getResult() == null) {
            TextSubmission textSubmission = (TextSubmission) result.getSubmission();
            textSubmission.setResult(result);
            textSubmissionRepository.save(textSubmission);
        }

        /*
         * write assessment to file system
         */

        // delete removed feedback
        List<Feedback> deprecatedFeedback = feedbackRepository.findByResult(result).stream()
            .filter(f -> textAssessment.stream().noneMatch(a -> a.referenceEquals(f)))
            .collect(Collectors.toList());
        feedbackRepository.deleteAll(deprecatedFeedback);

        // update existing and save new
        for (Feedback feedback : textAssessment) {
            feedback.setResult(result);
            result.addFeedback(feedback);
            feedback.setType(FeedbackType.MANUAL);
        }
        this.feedbackRepository.saveAll(textAssessment);

        resultRepository.save(result);
        return result;
    }

    public List<Feedback> getAssessmentsForResult(Result result) {
        return this.feedbackRepository.findByResult(result);
    }

    /**
     * Helper function to calculate the total score of a feedback list. It loops through all assessed model elements
     * and sums the credits up.
     *
     * @param assessments    the List of Feedback
     * @return the total score
     */
    // TODO CZ: move to AssessmentService class, as it's the same for modeling and text exercises (i.e. total score is sum of feedback credits) apart from rounding, but maybe also good for text exercises?
    private Double calculateTotalScore(List<Feedback> assessments) {
        return assessments.stream().mapToDouble(Feedback::getCredits).sum();
    }

    /**
     * Given a courseId, return the number of assessments for that course
     * @param courseId - the course we are interested in
     * @return a number of submissions for the course
     */
    public long countNumberOfAssessments(Long courseId) {
        return resultRepository.countByAssessorIsNotNullAndParticipation_Exercise_CourseId(courseId);
    }

    /**
     * Given a courseId and a tutorId, return the number of assessments for that course written by that tutor
     * @param courseId - the course we are interested in
     * @param tutorId - the tutor we are interested in
     * @return a number of submissions for the course
     */
    public long countNumberOfAssessmentsForTutor(Long courseId, Long tutorId) {
        return resultRepository.countByAssessor_IdAndParticipation_Exercise_CourseId(tutorId, courseId);
    }
}
