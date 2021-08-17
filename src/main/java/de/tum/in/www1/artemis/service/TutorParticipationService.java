package de.tum.in.www1.artemis.service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.cloudfoundry.com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.cloud.cloudfoundry.com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.TutorParticipationStatus;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.repository.TutorParticipationRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service Implementation for managing TutorParticipation.
 */
@Service
public class TutorParticipationService {

    /**
     * Wraps the information of tutor feedback validation (during tutor training).
     */
    class FeedbackCorrectionError {

        /**
         * Possible feedback validation error types.
         */
        enum Type {
            INCORRECT_SCORE, UNNECESSARY_FEEDBACK, MISSING_GRADING_INSTRUCTION, INCORRECT_GRADING_INSTRUCTION,
        }

        public String reference;

        public Type type;

        public FeedbackCorrectionError(String reference, Type type) {
            this.reference = reference;
            this.type = type;
        }
    }

    private static final String ENTITY_NAME = "TutorParticipation";

    private static final float scoreRangePercentage = 10;

    private final Logger log = LoggerFactory.getLogger(TutorParticipationService.class);

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final TutorParticipationRepository tutorParticipationRepository;

    private final ExampleSubmissionService exampleSubmissionService;

    public TutorParticipationService(TutorParticipationRepository tutorParticipationRepository, ExampleSubmissionRepository exampleSubmissionRepository,
            ExampleSubmissionService exampleSubmissionService) {
        this.tutorParticipationRepository = tutorParticipationRepository;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.exampleSubmissionService = exampleSubmissionService;
    }

    /**
     * Get one tutorParticipations by id.
     *
     * @param id the id of the entity
     * @return the entity
     */
    public TutorParticipation findOne(Long id) {
        log.debug("Request to get TutorParticipation : {}", id);
        Optional<TutorParticipation> tutorParticipation = tutorParticipationRepository.findById(id);
        return tutorParticipation.orElse(null);
    }

    /**
     * Given an exercise and a tutor, it retrieves the participation of the tutor for that exercise. If there isn't any participation in the database, it returns a participation
     * with status NOT_PARTICIPATED
     *
     * @param exercise the exercise we want to retrieve the tutor participation
     * @param tutor    the tutor of who we want to retrieve the participation
     * @return a tutor participation object for the pair (exercise, tutor) passed as argument
     */
    public TutorParticipation findByExerciseAndTutor(Exercise exercise, User tutor) {
        TutorParticipation participation = tutorParticipationRepository.findWithEagerExampleSubmissionAndResultsByAssessedExerciseAndTutor(exercise, tutor);

        if (participation == null) {
            participation = new TutorParticipation();
            participation.setStatus(TutorParticipationStatus.NOT_PARTICIPATED);
        }
        return participation;
    }

    /**
     * Given an exercise and a tutor it creates the participation of the tutor to that exercise The tutor starts a participation when she reads the grading instruction. If no
     * grading instructions are available, then she starts her participation clicking on "Start participation". Usually, the first step is `REVIEWED_INSTRUCTIONS`: after that, she
     * has to train reviewing some example submissions, and assessing others. If no example submissions are available, because the instructor hasn't created any, then she goes
     * directly to the next step, that allows her to assess students' participations
     *
     * @param exercise the exercise the tutor is going to participate to
     * @param tutor    the tutor who is going to participate to the exercise
     * @return a TutorParticipation for the exercise
     */
    public TutorParticipation createNewParticipation(Exercise exercise, User tutor) {
        TutorParticipation tutorParticipation = new TutorParticipation();
        Long exampleSubmissionsCount = exampleSubmissionRepository.countAllByExerciseId(exercise.getId());
        tutorParticipation.setStatus(exampleSubmissionsCount == 0 ? TutorParticipationStatus.TRAINED : TutorParticipationStatus.REVIEWED_INSTRUCTIONS);
        tutorParticipation.setTutor(tutor);
        tutorParticipation.setAssessedExercise(exercise);
        return tutorParticipationRepository.saveAndFlush(tutorParticipation);
    }

    /**
     * Validates tutor feedback. Returns empty if correct, error type otherwise.
     * Validation rules:
     * - There should exist a corresponding instructor feedback that references the same object
     * - If instructor feedback has a grading instruction associated with it, so must the tutor feedback
     * - The feedback should have the same creditCount(score)
     */
    private Optional<FeedbackCorrectionError.Type> validateTutorFeedback(Feedback tutorFeedback, List<Feedback> instructorFeedback) {
        Optional<Feedback> maybeMatchingInstructorFeedback = instructorFeedback.stream().filter(feedback -> Objects.equals(tutorFeedback.getReference(), feedback.getReference()))
                .findFirst();

        // In case there is no instructor feedback that is referencing the same element (text/model), then tutor's feedback is unnecessary.
        if (maybeMatchingInstructorFeedback.isEmpty()) {
            return Optional.of(FeedbackCorrectionError.Type.UNNECESSARY_FEEDBACK);
        }

        var matchingInstructorFeedback = maybeMatchingInstructorFeedback.get();

        if (matchingInstructorFeedback.getGradingInstruction() != null) {
            // If instructor used grading instruction while creating the feedback but the tutor didn't use it return missing grading instruction.
            if (tutorFeedback.getGradingInstruction() == null) {
                return Optional.of(FeedbackCorrectionError.Type.MISSING_GRADING_INSTRUCTION);
            }

            if (!Objects.equals(matchingInstructorFeedback.getGradingInstruction().getId(), tutorFeedback.getGradingInstruction().getId())) {
                return Optional.of(FeedbackCorrectionError.Type.INCORRECT_GRADING_INSTRUCTION);
            }
        }

        boolean equalCredits = Double.compare(matchingInstructorFeedback.getCredits(), tutorFeedback.getCredits()) == 0;
        if (!equalCredits) {
            return Optional.of(FeedbackCorrectionError.Type.INCORRECT_SCORE);
        }

        return Optional.empty();
    }

    private boolean isValidTutorialExampleSubmission(List<Feedback> tutorFeedback, List<Feedback> instructorFeedback) {
        boolean equalFeedbackCount = instructorFeedback.size() == tutorFeedback.size();
        boolean allTutorFeedbackAreCorrect = tutorFeedback.stream().map(feedback -> validateTutorFeedback(feedback, instructorFeedback)).allMatch(Optional::isEmpty);
        return equalFeedbackCount && allTutorFeedbackAreCorrect;
    }

    /**
     * Validates the tutor example submission. If invalid, throw bad request exception with information which feedback are incorrect.
     */
    private void validateTutorialExampleSubmission(ExampleSubmission tutorExampleSubmission) {
        boolean isTextSubmission = tutorExampleSubmission.getSubmission() instanceof TextSubmission;
        if (!isTextSubmission) {
            validateTutorialExampleSubmissionUsingTotalScore(tutorExampleSubmission);
            return;
        }

        var tutorFeedback = tutorExampleSubmission.getSubmission().getLatestResult().getFeedbacks();
        var instructorFeedback = exampleSubmissionRepository.getFeedbackForExampleSubmission(tutorExampleSubmission.getId());

        if (isValidTutorialExampleSubmission(tutorFeedback, instructorFeedback)) {
            return;
        }

        // If invalid, get all incorrect feedback and send an array of the corresponding `FeedbackCorrectionError`s to the client.
        // Pack this information into bad request exception.
        var wrongFeedback = tutorFeedback.stream().flatMap(feedback -> {
            var validationError = validateTutorFeedback(feedback, instructorFeedback);
            if (validationError.isEmpty()) {
                return Stream.empty();
            }

            var objectWriter = new ObjectMapper().writer().withDefaultPrettyPrinter();
            try {
                // Build JSON string for the corresponding `FeedbackCorrectionError` object.
                var feedbackCorrectionErrorJSON = objectWriter.writeValueAsString(new FeedbackCorrectionError(feedback.getReference(), validationError.get()));
                return Stream.of(feedbackCorrectionErrorJSON);
            }
            catch (JsonProcessingException e) {
                return Stream.empty();
            }
        }).collect(Collectors.joining(","));
        throw new BadRequestAlertException("{\"errors\": [" + wrongFeedback + "]}", ENTITY_NAME, "invalid_assessment");
    }

    private void validateTutorialExampleSubmissionUsingTotalScore(ExampleSubmission tutorExampleSubmission) {
        // Retrieve the example feedback created by the instructor
        List<Feedback> existingFeedback = exampleSubmissionRepository.getFeedbackForExampleSubmission(tutorExampleSubmission.getId());

        float instructorScore = calculateTotalScore(existingFeedback);
        float lowerInstructorScore = instructorScore - instructorScore / scoreRangePercentage;
        float higherInstructorScore = instructorScore + instructorScore / scoreRangePercentage;

        float tutorScore = calculateTotalScore(tutorExampleSubmission.getSubmission().getLatestResult().getFeedbacks());

        if (lowerInstructorScore > tutorScore) {
            throw new BadRequestAlertException("tooLow", ENTITY_NAME, "tooLow");
        }

        if (tutorScore > higherInstructorScore) {
            throw new BadRequestAlertException("tooHigh", ENTITY_NAME, "tooHigh");
        }
    }

    /**
     * Given an exercise, it adds to the tutor participation of that exercise the example submission passed as argument.
     * If it is valid (e.g: if it is an example submission used for tutorial, we check the result is close enough to the one of the instructor)
     *
     * @param exercise               - the exercise we are referring to
     * @param tutorExampleSubmission - the example submission to add
     * @param user                   - the user who invokes this request
     * @return the updated tutor participation
     * @throws EntityNotFoundException if example submission or tutor participation is not found
     * @throws BadRequestAlertException if tutor didn't review the instructions before assessing example submissions
     */
    public TutorParticipation addExampleSubmission(Exercise exercise, ExampleSubmission tutorExampleSubmission, User user)
            throws EntityNotFoundException, BadRequestAlertException {
        TutorParticipation existingTutorParticipation = this.findByExerciseAndTutor(exercise, user);
        // Do not trust the user input
        Optional<ExampleSubmission> exampleSubmissionFromDatabase = exampleSubmissionRepository.findByIdWithResultsAndTutorParticipations(tutorExampleSubmission.getId());

        if (existingTutorParticipation == null || exampleSubmissionFromDatabase.isEmpty()) {
            throw new EntityNotFoundException("There isn't such example submission, or there isn't any tutor participation for this exercise");
        }

        ExampleSubmission originalExampleSubmission = exampleSubmissionFromDatabase.get();

        // Cannot start an example submission if the tutor hasn't participated to the exercise yet
        if (existingTutorParticipation.getStatus() == TutorParticipationStatus.NOT_PARTICIPATED) {
            throw new BadRequestAlertException("The tutor needs review the instructions before assessing example submissions", ENTITY_NAME, "wrongStatus");
        }

        // Check if it is a tutorial or not
        boolean isTutorial = Boolean.TRUE.equals(originalExampleSubmission.isUsedForTutorial());

        // If it is a tutorial we check the assessment
        if (isTutorial) {
            validateTutorialExampleSubmission(tutorExampleSubmission);
        }

        List<ExampleSubmission> alreadyAssessedSubmissions = new ArrayList<>(existingTutorParticipation.getTrainedExampleSubmissions());

        // If the example submission was already assessed, we do not assess it again, we just return the current participation
        if (alreadyAssessedSubmissions.contains(tutorExampleSubmission)) {
            return existingTutorParticipation;
        }

        long numberOfExampleSubmissionsForTutor = exampleSubmissionRepository.findAllWithResultByExerciseId(exercise.getId()).stream()
                // We are only interested in example submissions with an assessment as these are the ones that can be reviewed/assessed by tutors.
                // Otherwise, the tutor could not reach the total number of example submissions, if there are example submissions without assessment.
                // In this case the tutor could not reach status "TRAINED" in the if statement below and would not be allowed
                // to asses student submissions in the assessment dashboard.
                .filter(exSub -> exSub.getSubmission() != null && exSub.getSubmission().getLatestResult() != null
                        && Boolean.TRUE.equals(exSub.getSubmission().getLatestResult().isExampleResult()))
                .count();
        int numberOfAlreadyAssessedSubmissions = alreadyAssessedSubmissions.size() + 1;  // +1 because we haven't added yet the one we just did

        /*
         * When the tutor has read and assessed all the exercises, the tutor status goes to the next step.
         */
        if (numberOfAlreadyAssessedSubmissions >= numberOfExampleSubmissionsForTutor) {
            existingTutorParticipation.setStatus(TutorParticipationStatus.TRAINED);
        }

        // keep example submission set reference with loaded submission.results to reconnect after save response from DB
        var exampleSubmissionSet = existingTutorParticipation.getTrainedExampleSubmissions();

        existingTutorParticipation = existingTutorParticipation.addTrainedExampleSubmissions(originalExampleSubmission);
        exampleSubmissionService.save(originalExampleSubmission);
        existingTutorParticipation = tutorParticipationRepository.saveAndFlush(existingTutorParticipation);

        existingTutorParticipation.setTrainedExampleSubmissions(exampleSubmissionSet);
        existingTutorParticipation.getTrainedExampleSubmissions().add(originalExampleSubmission);

        return existingTutorParticipation;
    }

    /**
     * This method removes the tutor participation for the example submission of an exercise
     * @param exercise  the exercise to which the example submission and tutor participation are linked to
     * @param user  the user for which the tutor participation should be removed
     */
    public void removeTutorParticipations(Exercise exercise, User user) {
        if (!tutorParticipationRepository.existsByAssessedExerciseIdAndTutorId(exercise.getId(), user.getId())) {
            return;
        }

        Set<ExampleSubmission> exampleSubmissions = exampleSubmissionRepository.findAllByExerciseId(exercise.getId());
        TutorParticipation tutorParticipation = tutorParticipationRepository.findWithEagerExampleSubmissionAndResultsByAssessedExerciseAndTutor(exercise, user);

        for (ExampleSubmission exampleSubmission : exampleSubmissions) {
            Optional<ExampleSubmission> exampleSubmissionWithTutorParticipation = exampleSubmissionRepository.findByIdWithResultsAndTutorParticipations(exampleSubmission.getId());
            if (exampleSubmissionWithTutorParticipation.isPresent()) {
                exampleSubmissionWithTutorParticipation.get().removeTutorParticipations(tutorParticipation);
                tutorParticipationRepository.delete(tutorParticipation);
            }
        }
    }

    private float calculateTotalScore(List<Feedback> feedback) {
        return (float) feedback.stream().mapToDouble(Feedback::getCredits).sum();
    }
}
