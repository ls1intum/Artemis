package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.domain.enumeration.FeedbackType.*;
import static de.tum.in.www1.artemis.domain.enumeration.TutorParticipationStatus.*;
import static de.tum.in.www1.artemis.service.TutorParticipationService.FeedbackCorrectionErrorType.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.cloudfoundry.com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.cloud.cloudfoundry.com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
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
     * Possible feedback validation error types.
     */
    enum FeedbackCorrectionErrorType {
        INCORRECT_SCORE, UNNECESSARY_FEEDBACK, MISSING_GRADING_INSTRUCTION, INCORRECT_GRADING_INSTRUCTION, EMPTY_NEGATIVE_FEEDBACK
    }

    private static final String ENTITY_NAME = "TutorParticipation";

    private final Logger log = LoggerFactory.getLogger(TutorParticipationService.class);

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final TutorParticipationRepository tutorParticipationRepository;

    private final ExampleSubmissionService exampleSubmissionService;

    /**
     * Wraps the information of tutor feedback validation (during tutor training).
     */
    static class FeedbackCorrectionError {

        public String reference;

        public FeedbackCorrectionErrorType type;

        public FeedbackCorrectionError(String reference, FeedbackCorrectionErrorType type) {
            this.reference = reference;
            this.type = type;
        }
    }

    public TutorParticipationService(TutorParticipationRepository tutorParticipationRepository, ExampleSubmissionRepository exampleSubmissionRepository,
            ExampleSubmissionService exampleSubmissionService) {
        this.tutorParticipationRepository = tutorParticipationRepository;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.exampleSubmissionService = exampleSubmissionService;
    }

    /**
     * Given an exercise and a tutor, it retrieves the participation of the tutor for that exercise. If there isn't any participation in the database, it returns a participation
     * with status NOT_PARTICIPATED
     *
     * @param exercise the exercise we want to retrieve the tutor participation
     * @param tutor    the tutor of whom we want to retrieve the participation
     * @return a tutor participation object for the pair (exercise, tutor) passed as argument
     */
    public TutorParticipation findByExerciseAndTutor(Exercise exercise, User tutor) {
        TutorParticipation participation = tutorParticipationRepository.findWithEagerExampleSubmissionAndResultsByAssessedExerciseAndTutor(exercise, tutor);

        if (participation == null) {
            participation = new TutorParticipation();
            participation.setStatus(NOT_PARTICIPATED);
        }
        return participation;
    }

    /**
     * Given an exercise and a tutor it creates the participation of the tutor to that exercise The tutor starts a participation when she reads the grading instruction. If no
     * grading instructions are available, then she starts her participation clicking on "Start participation". Usually, the first step is `REVIEWED_INSTRUCTIONS`: after that, she
     * has to train reviewing some example submissions, and assessing others. If no example submissions are available, because the instructor hasn't created any, then she goes
     * directly to the next step, that allows her to assess students' participations
     *
     * @param exercise the exercise the tutor is going to participate in
     * @param tutor    the tutor who is going to participate in the exercise
     * @return a TutorParticipation for the exercise
     */
    public TutorParticipation createNewParticipation(Exercise exercise, User tutor) {
        TutorParticipation tutorParticipation = new TutorParticipation();
        Long exampleSubmissionsCount = exampleSubmissionRepository.countAllByExerciseId(exercise.getId());
        tutorParticipation.setStatus(exampleSubmissionsCount == 0 ? TRAINED : REVIEWED_INSTRUCTIONS);
        tutorParticipation.setTutor(tutor);
        tutorParticipation.setAssessedExercise(exercise);
        return tutorParticipationRepository.saveAndFlush(tutorParticipation);
    }

    /**
     * Validates if tutor feedback matches instructor feedback.
     * Validation rules:
     * - The feedback should have the same creditCount(score)
     * - If instructor feedback has a grading instruction associated with it, so must the tutor feedback
     * - Tutor feedback is not allowed to have negative score without feedback content
     *
     * @return error type if feedback is invalid, `Optional.empty()` otherwise.
     */
    private Optional<FeedbackCorrectionErrorType> tutorFeedbackMatchesInstructorFeedback(Feedback tutorFeedback, Feedback instructorFeedback) {
        // If instructor feedback score is different from tutor one, return incorrect score.
        boolean equalCredits = Double.compare(instructorFeedback.getCredits(), tutorFeedback.getCredits()) == 0;
        if (!equalCredits) {
            return Optional.of(FeedbackCorrectionErrorType.INCORRECT_SCORE);
        }

        if (instructorFeedback.getGradingInstruction() != null) {
            // If instructor used grading instruction while creating the feedback but the tutor didn't use it, return missing grading instruction.
            if (tutorFeedback.getGradingInstruction() == null) {
                return Optional.of(FeedbackCorrectionErrorType.MISSING_GRADING_INSTRUCTION);
            }

            // If instructor used different grading instruction, return incorrect grading instruction.
            if (!Objects.equals(instructorFeedback.getGradingInstruction().getId(), tutorFeedback.getGradingInstruction().getId())) {
                return Optional.of(FeedbackCorrectionErrorType.INCORRECT_GRADING_INSTRUCTION);
            }
        }

        // In case negative feedback is provided, but content is missing, return empty negative feedback.
        var feedbackContent = Optional.ofNullable(tutorFeedback.getText() != null ? tutorFeedback.getText() : tutorFeedback.getDetailText()).orElse("");
        if (tutorFeedback.getCredits() < 0 && feedbackContent.isBlank()) {
            return Optional.of(FeedbackCorrectionErrorType.EMPTY_NEGATIVE_FEEDBACK);
        }

        return Optional.empty();
    }

    private Optional<FeedbackCorrectionErrorType> checkTutorFeedbackForErrors(Feedback tutorFeedback, List<Feedback> instructorFeedback) {
        List<Feedback> matchingInstructorFeedback = instructorFeedback.stream().filter(feedback -> {
            // If tutor feedback is unreferenced, then instructor feedback is a potential match if it is also unreferenced
            if (tutorFeedback.getType() == MANUAL_UNREFERENCED) {
                return feedback.getType() == MANUAL_UNREFERENCED;
            }

            // For other feedback, both feedback have to reference the same element
            return Objects.equals(tutorFeedback.getReference(), feedback.getReference());
        }).toList();

        // If there are no potential matches, then the feedback is unnecessary
        if (matchingInstructorFeedback.isEmpty()) {
            return Optional.of(UNNECESSARY_FEEDBACK);
        }

        // If tutor feedback is unreferenced, then look for the first match and remove it from the next subsequent matches
        if (tutorFeedback.getType() == MANUAL_UNREFERENCED) {
            var hasMatchingInstructorFeedback = matchingInstructorFeedback.stream().anyMatch(feedback -> {
                var isMatch = tutorFeedbackMatchesInstructorFeedback(tutorFeedback, feedback).isEmpty();

                // This instructor feedback can not be used to match other tutor unreferenced feedback
                if (isMatch) {
                    instructorFeedback.remove(feedback);
                }
                return isMatch;
            });
            if (hasMatchingInstructorFeedback) {
                return Optional.empty();
            }

            // Return the highest priority error (the closest instructor feedback match)
            return matchingInstructorFeedback.stream().map(feedback -> tutorFeedbackMatchesInstructorFeedback(tutorFeedback, feedback).get()).max(Comparator.naturalOrder());
        }
        else {
            if (matchingInstructorFeedback.size() > 1) {
                throw new IllegalStateException("Multiple instructor feedback exist with the same reference");
            }

            return tutorFeedbackMatchesInstructorFeedback(tutorFeedback, matchingInstructorFeedback.get(0));
        }
    }

    /**
     * Validates the tutor example submission. If invalid, throw bad request exception with information which feedback are incorrect.
     */
    private void validateTutorialExampleSubmission(ExampleSubmission tutorExampleSubmission) {
        var latestResult = tutorExampleSubmission.getSubmission().getLatestResult();
        if (latestResult == null) {
            throw new BadRequestAlertException("The training does not contain an assessment", ENTITY_NAME, "invalid_assessment");
        }
        var tutorFeedback = latestResult.getFeedbacks();
        var instructorFeedback = exampleSubmissionRepository.getFeedbackForExampleSubmission(tutorExampleSubmission.getId());
        boolean equalFeedbackCount = instructorFeedback.size() == tutorFeedback.size();

        var unreferencedInstructorFeedbackCount = instructorFeedback.stream().filter(feedback -> feedback.getType() == MANUAL_UNREFERENCED).toList().size();
        var unreferencedTutorFeedback = tutorFeedback.stream().filter(feedback -> feedback.getType() == MANUAL_UNREFERENCED).toList();

        // If invalid, get all incorrect feedback and send an array of the corresponding `FeedbackCorrectionError`s to the client.
        var wrongFeedback = tutorFeedback.stream().flatMap(feedback -> {
            // If current tutor feedback is unreferenced and there are already more than enough unreferenced feedback provided, mark this feedback as unnecessary.
            var unreferencedTutorFeedbackCount = unreferencedTutorFeedback.indexOf(feedback) + 1;
            var validationError = unreferencedTutorFeedbackCount > unreferencedInstructorFeedbackCount ? Optional.of(UNNECESSARY_FEEDBACK)
                    : checkTutorFeedbackForErrors(feedback, instructorFeedback);
            if (validationError.isEmpty()) {
                return Stream.empty();
            }

            var objectWriter = new ObjectMapper().writer().withDefaultPrettyPrinter();
            try {
                // Build JSON string for the corresponding `FeedbackCorrectionError` object.
                // TODO: I think we should let Spring automatically convert it to Json
                var feedbackCorrectionErrorJSON = objectWriter.writeValueAsString(new FeedbackCorrectionError(feedback.getReference(), validationError.get()));
                return Stream.of(feedbackCorrectionErrorJSON);
            }
            catch (JsonProcessingException e) {
                log.warn("JsonProcessingException in validateTutorialExampleSubmission: {}", e.getMessage());
                return Stream.empty();
            }
        }).collect(Collectors.joining(","));
        if (wrongFeedback.isBlank() && equalFeedbackCount) {
            return;
        }

        // Pack this information into bad request exception.
        throw new BadRequestAlertException("{\"errors\": [" + wrongFeedback + "]}", ENTITY_NAME, "invalid_assessment", true);
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

        // Cannot start an example submission if the tutor hasn't participated in the exercise yet
        if (existingTutorParticipation.getStatus() == NOT_PARTICIPATED) {
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
                // to assess student submissions in the assessment dashboard.
                .filter(exSub -> exSub.getSubmission() != null && exSub.getSubmission().getLatestResult() != null
                        && Boolean.TRUE.equals(exSub.getSubmission().getLatestResult().isExampleResult()))
                .count();
        int numberOfAlreadyAssessedSubmissions = alreadyAssessedSubmissions.size() + 1;  // +1 because we haven't added yet the one we just did

        /*
         * When the tutor has read and assessed all the exercises, the tutor status goes to the next step.
         */
        if (numberOfAlreadyAssessedSubmissions >= numberOfExampleSubmissionsForTutor) {
            existingTutorParticipation.setStatus(TRAINED);
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
}
