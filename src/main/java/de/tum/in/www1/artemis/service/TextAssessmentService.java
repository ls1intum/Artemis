package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Service
public class TextAssessmentService extends AssessmentService {

    private final FeedbackRepository feedbackRepository;

    private final TextSubmissionRepository textSubmissionRepository;

    private final UserService userService;

    public TextAssessmentService(UserService userService, ComplaintResponseService complaintResponseService, FeedbackRepository feedbackRepository,
            ComplaintRepository complaintRepository, ResultRepository resultRepository, TextSubmissionRepository textSubmissionRepository,
            ParticipationRepository participationRepository, ResultService resultService) {
        super(complaintResponseService, complaintRepository, resultRepository, participationRepository, resultService);
        this.feedbackRepository = feedbackRepository;
        this.textSubmissionRepository = textSubmissionRepository;
        this.userService = userService;
    }

    /**
     * This function is used for manually assessed results. It updates the completion date, sets the assessment type to MANUAL and sets the assessor attribute. Furthermore, it
     * saves the assessment in the file system the total score is calculated and set in the result.
     *
     * @param resultId       the resultId the assessment belongs to
     * @param textExercise   the text exercise the assessment belongs to
     * @param textAssessment the assessments as a list
     * @return the ResponseEntity with result as body
     * @throws BadRequestAlertException on invalid feedback input
     */
    @Transactional
    public Result submitAssessment(Long resultId, TextExercise textExercise, List<Feedback> textAssessment) throws BadRequestAlertException {
        Result result = saveAssessment(resultId, textAssessment);
        Double calculatedScore = calculateTotalScore(textAssessment);

        return submitResult(result, textExercise, calculatedScore);
    }

    /**
     * This function is used for manually assessed results. It updates the completion date, sets the assessment type to MANUAL and sets the assessor attribute. Furthermore, it
     * saves the assessment in the file system the total score is calculated and set in the result.
     *
     * @param resultId       the resultId the assessment belongs to
     * @param textAssessment the assessments as string
     * @return the ResponseEntity with result as body
     * @throws BadRequestAlertException on invalid feedback input
     */
    @Transactional
    public Result saveAssessment(Long resultId, List<Feedback> textAssessment) throws BadRequestAlertException {
        checkGeneralFeedback(textAssessment);

        final boolean hasAssessmentWithTooLongReference = textAssessment.stream().filter(Feedback::hasReference)
                .anyMatch(f -> f.getReference().length() > Feedback.MAX_REFERENCE_LENGTH);
        if (hasAssessmentWithTooLongReference) {
            throw new BadRequestAlertException("Please select a text block shorter than " + Feedback.MAX_REFERENCE_LENGTH + " characters.", "textAssessment",
                    "feedbackReferenceTooLong");
        }

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

        // Note: If there is old feedback that gets removed here and not added again in the for-loop, it will also be
        // deleted in the database because of the 'orphanRemoval = true' flag.
        result.getFeedbacks().clear();
        for (Feedback feedback : textAssessment) {
            feedback.setPositive(feedback.getCredits() >= 0);
            feedback.setType(FeedbackType.MANUAL);
            result.addFeedback(feedback);
        }
        result.setHasFeedback(false);

        resultRepository.save(result);
        return result;
    }

    public List<Feedback> getAssessmentsForResult(Result result) {
        return this.feedbackRepository.findByResult(result);
    }

    /**
     * Helper function to calculate the total score of a feedback list. It loops through all assessed model elements and sums the credits up.
     *
     * @param assessments the List of Feedback
     * @return the total score
     */
    // TODO CZ: move to AssessmentService class, as it's the same for modeling and text exercises (i.e. total score is sum of feedback credits) apart from rounding, but maybe also
    // good for text exercises?
    private Double calculateTotalScore(List<Feedback> assessments) {
        return assessments.stream().mapToDouble(Feedback::getCredits).sum();
    }

    /**
     * Given a courseId, return the number of assessments for that course that have been completed (e.g. no draft!)
     *
     * @param courseId - the course we are interested in
     * @return a number of assessments for the course
     */
    public long countNumberOfAssessments(Long courseId) {
        return resultRepository.countByAssessorIsNotNullAndParticipation_Exercise_CourseIdAndRatedAndCompletionDateIsNotNull(courseId, true);
    }

    /**
     * Given a courseId and a tutorId, return the number of assessments for that course written by that tutor that have been completed (e.g. no draft!)
     *
     * @param courseId - the course we are interested in
     * @param tutorId  - the tutor we are interested in
     * @return a number of assessments for the course
     */
    public long countNumberOfAssessmentsForTutor(Long courseId, Long tutorId) {
        return resultRepository.countByAssessor_IdAndParticipation_Exercise_CourseIdAndRatedAndCompletionDateIsNotNull(tutorId, courseId, true);
    }

    /**
     * Given an exerciseId, return the number of assessments for that exerciseId that have been completed (e.g. no draft!)
     *
     * @param exerciseId - the exercise we are interested in
     * @return a number of assessments for the exercise
     */
    public long countNumberOfAssessmentsForExercise(Long exerciseId) {
        return resultRepository.countByAssessorIsNotNullAndParticipation_ExerciseIdAndRatedAndCompletionDateIsNotNull(exerciseId, true);
    }

    /**
     * Given a exerciseId and a tutorId, return the number of assessments for that exercise written by that tutor that have been completed (e.g. no draft!)
     *
     * @param exerciseId - the exercise we are interested in
     * @param tutorId    - the tutor we are interested in
     * @return a number of assessments for the exercise
     */
    public long countNumberOfAssessmentsForTutorInExercise(Long exerciseId, Long tutorId) {
        return resultRepository.countByAssessor_IdAndParticipation_ExerciseIdAndRatedAndCompletionDateIsNotNull(tutorId, exerciseId, true);
    }
}
