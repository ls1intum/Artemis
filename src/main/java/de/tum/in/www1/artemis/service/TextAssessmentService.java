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
    // TODO: refactor this to use the submission instead of the resultId and combine with method in ModelingAssessmentService and saveExampleAssessment() method below
    public Result saveAssessment(Long resultId, List<Feedback> textAssessment) throws BadRequestAlertException {
        checkGeneralFeedback(textAssessment);

        final boolean hasAssessmentWithTooLongReference = textAssessment.stream().filter(Feedback::hasReference)
                .anyMatch(f -> f.getReference().length() > Feedback.MAX_REFERENCE_LENGTH);
        if (hasAssessmentWithTooLongReference) {
            throw new BadRequestAlertException("Please select a text block shorter than " + Feedback.MAX_REFERENCE_LENGTH + " characters.", "textAssessment",
                    "feedbackReferenceTooLong");
        }

        // TODO: load submission and result eagerly here
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

        return resultRepository.save(result);
    }

    /**
     * This function is used for saving an example assessment. It sets the assessment type to MANUAL and sets the assessor attribute. Furthermore, it saves the result in the
     * database.
     *
     * @param textSubmission the text submission to which the feedback belongs to
     * @param textAssessment the assessment as a feedback list that should be added to the result of the corresponding submission
     */
    @Transactional
    public Result saveExampleAssessment(TextSubmission textSubmission, List<Feedback> textAssessment) {
        Result result = textSubmission.getResult();
        if (result == null) {
            result = new Result();
        }
        checkGeneralFeedback(textAssessment);

        result.setHasComplaint(false);
        result.setExampleResult(textSubmission.isExampleSubmission());
        result.setAssessmentType(AssessmentType.MANUAL);
        User user = userService.getUser();
        result.setAssessor(user);
        result.setNewFeedback(textAssessment);
        // Note: this boolean flag is only used for programming exercises
        result.setHasFeedback(false);

        if (result.getSubmission() == null) {
            result.setSubmission(textSubmission);
            textSubmission.setResult(result);
            textSubmissionRepository.save(textSubmission);
        }
        // Note: This also saves the feedback objects in the database because of the 'cascade =
        // CascadeType.ALL' option.
        return resultRepository.save(result);
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
     * Given a courseId, return the number of assessments for that course
     *
     * @param courseId - the course we are interested in
     * @return a number of assessments for the course
     */
    public long countNumberOfAssessments(Long courseId) {
        return resultRepository.countByAssessorIsNotNullAndParticipation_Exercise_CourseId(courseId);
    }

    /**
     * Given a courseId and a tutorId, return the number of assessments for that course written by that tutor
     *
     * @param courseId - the course we are interested in
     * @param tutorId  - the tutor we are interested in
     * @return a number of assessments for the course
     */
    public long countNumberOfAssessmentsForTutor(Long courseId, Long tutorId) {
        return resultRepository.countByAssessor_IdAndParticipation_Exercise_CourseId(tutorId, courseId);
    }

    /**
     * Given an exerciseId, return the number of assessments for that exerciseId
     *
     * @param exerciseId - the exercise we are interested in
     * @return a number of assessments for the exercise
     */
    public long countNumberOfAssessmentsForExercise(Long exerciseId) {
        return resultRepository.countByAssessorIsNotNullAndParticipation_ExerciseId(exerciseId);
    }

    /**
     * Given a exerciseId and a tutorId, return the number of assessments for that exercise written by that tutor
     *
     * @param exerciseId - the exercise we are interested in
     * @param tutorId    - the tutor we are interested in
     * @return a number of assessments for the exercise
     */
    public long countNumberOfAssessmentsForTutorInExercise(Long exerciseId, Long tutorId) {
        return resultRepository.countByAssessor_IdAndParticipation_ExerciseId(tutorId, exerciseId);
    }
}
