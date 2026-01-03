package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.assessment.domain.FeedbackType.MANUAL_UNREFERENCED;
import static de.tum.cit.aet.artemis.assessment.service.TutorParticipationService.FeedbackCorrectionErrorType.UNNECESSARY_FEEDBACK;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.tutorialgroup.domain.TutorParticipationStatus.NOT_PARTICIPATED;
import static de.tum.cit.aet.artemis.tutorialgroup.domain.TutorParticipationStatus.REVIEWED_INSTRUCTIONS;
import static de.tum.cit.aet.artemis.tutorialgroup.domain.TutorParticipationStatus.TRAINED;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.assessment.domain.ExampleParticipation;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.assessment.repository.ExampleParticipationRepository;
import de.tum.cit.aet.artemis.assessment.repository.TutorParticipationRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Service Implementation for managing TutorParticipation.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class TutorParticipationService {

    /**
     * Possible feedback validation error types.
     */
    enum FeedbackCorrectionErrorType {
        INCORRECT_SCORE, UNNECESSARY_FEEDBACK, MISSING_GRADING_INSTRUCTION, INCORRECT_GRADING_INSTRUCTION, EMPTY_NEGATIVE_FEEDBACK
    }

    private static final String ENTITY_NAME = "TutorParticipation";

    private static final Logger log = LoggerFactory.getLogger(TutorParticipationService.class);

    private final ExampleParticipationRepository exampleParticipationRepository;

    private final TutorParticipationRepository tutorParticipationRepository;

    private final ExampleParticipationService exampleParticipationService;

    /**
     * Wraps the information of tutor feedback validation (during tutor training).
     */
    record FeedbackCorrectionError(String reference, FeedbackCorrectionErrorType type) {
    }

    public TutorParticipationService(TutorParticipationRepository tutorParticipationRepository, ExampleParticipationRepository exampleParticipationRepository,
            ExampleParticipationService exampleParticipationService) {
        this.tutorParticipationRepository = tutorParticipationRepository;
        this.exampleParticipationRepository = exampleParticipationRepository;
        this.exampleParticipationService = exampleParticipationService;
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
        TutorParticipation participation = tutorParticipationRepository.findWithEagerExampleParticipationAndResultsByAssessedExerciseAndTutor(exercise, tutor);

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
        Long exampleParticipationsCount = exampleParticipationRepository.countAllByExerciseId(exercise.getId());
        tutorParticipation.setStatus(exampleParticipationsCount == 0 ? TRAINED : REVIEWED_INSTRUCTIONS);
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
        var feedbackContent = Objects.requireNonNullElse(tutorFeedback.getText() != null ? tutorFeedback.getText() : tutorFeedback.getDetailText(), "");
        if (tutorFeedback.getCredits() < 0 && feedbackContent.isBlank()) {
            return Optional.of(FeedbackCorrectionErrorType.EMPTY_NEGATIVE_FEEDBACK);
        }

        return Optional.empty();
    }

    private Optional<FeedbackCorrectionErrorType> checkTutorFeedbackForErrors(Feedback tutorFeedback, Collection<Feedback> instructorFeedback) {
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
            return matchingInstructorFeedback.stream().map(feedback -> tutorFeedbackMatchesInstructorFeedback(tutorFeedback, feedback).orElseThrow())
                    .max(Comparator.naturalOrder());
        }
        else {
            if (matchingInstructorFeedback.size() > 1) {
                throw new IllegalStateException("Multiple instructor feedback exist with the same reference");
            }

            return tutorFeedbackMatchesInstructorFeedback(tutorFeedback, matchingInstructorFeedback.getFirst());
        }
    }

    /**
     * Validates the tutor example participation. If invalid, throw bad request exception with information which feedback are incorrect.
     */
    private void validateTutorialExampleParticipation(ExampleParticipation tutorExampleParticipation) {
        var latestResult = tutorExampleParticipation.getSubmission().getLatestResult();
        if (latestResult == null) {
            throw new BadRequestAlertException("The training does not contain an assessment", ENTITY_NAME, "invalid_assessment");
        }
        var tutorFeedback = latestResult.getFeedbacks();
        var instructorFeedback = exampleParticipationRepository.getFeedbackForExampleParticipation(tutorExampleParticipation.getId());
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
     * Given an exercise, it adds to the tutor participation of that exercise the example participation passed as argument.
     * If it is valid (e.g: if it is an example participation used for tutorial, we check the result is close enough to the one of the instructor)
     *
     * @param exercise                  - the exercise we are referring to
     * @param tutorExampleParticipation - the example participation to add
     * @param user                      - the user who invokes this request
     * @return the updated tutor participation
     * @throws EntityNotFoundException  if example participation or tutor participation is not found
     * @throws BadRequestAlertException if tutor didn't review the instructions before assessing example participations
     */
    public TutorParticipation addExampleParticipation(Exercise exercise, ExampleParticipation tutorExampleParticipation, User user)
            throws EntityNotFoundException, BadRequestAlertException {
        TutorParticipation existingTutorParticipation = this.findByExerciseAndTutor(exercise, user);
        // Do not trust the user input
        Optional<ExampleParticipation> exampleParticipationFromDatabase = exampleParticipationRepository
                .findByIdWithResultsAndTutorParticipations(tutorExampleParticipation.getId());

        if (existingTutorParticipation == null || exampleParticipationFromDatabase.isEmpty()) {
            throw new EntityNotFoundException("There isn't such example participation, or there isn't any tutor participation for this exercise");
        }

        ExampleParticipation originalExampleParticipation = exampleParticipationFromDatabase.get();

        // Cannot start an example participation if the tutor hasn't participated in the exercise yet
        if (existingTutorParticipation.getStatus() == NOT_PARTICIPATED) {
            throw new BadRequestAlertException("The tutor needs review the instructions before assessing example participations", ENTITY_NAME, "wrongStatus");
        }

        // Check if it is a tutorial or not
        boolean isTutorial = Boolean.TRUE.equals(originalExampleParticipation.isUsedForTutorial());

        // If it is a tutorial we check the assessment
        if (isTutorial) {
            validateTutorialExampleParticipation(tutorExampleParticipation);
        }

        Set<ExampleParticipation> alreadyAssessedParticipations = new HashSet<>(existingTutorParticipation.getTrainedExampleParticipations());

        // If the example participation was already assessed, we do not assess it again, we just return the current participation
        if (alreadyAssessedParticipations.contains(tutorExampleParticipation)) {
            return existingTutorParticipation;
        }

        long numberOfExampleParticipationsForTutor = exampleParticipationRepository.findAllWithSubmissionsAndResultsByExerciseId(exercise.getId()).stream()
                // We are only interested in example participations with an assessment as these are the ones that can be reviewed/assessed by tutors.
                // Otherwise, the tutor could not reach the total number of example participations, if there are example participations without assessment.
                // In this case the tutor could not reach status "TRAINED" in the if statement below and would not be allowed
                // to assess student submissions in the assessment dashboard.
                .filter(exPart -> exPart.getSubmission() != null && exPart.getSubmission().getLatestResult() != null
                        && Boolean.TRUE.equals(exPart.getSubmission().getLatestResult().isExampleResult()))
                .count();
        int numberOfAlreadyAssessedParticipations = alreadyAssessedParticipations.size() + 1;  // +1 because we haven't added yet the one we just did

        /*
         * When the tutor has read and assessed all the exercises, the tutor status goes to the next step.
         */
        if (numberOfAlreadyAssessedParticipations >= numberOfExampleParticipationsForTutor) {
            existingTutorParticipation.setStatus(TRAINED);
        }

        // keep example participation set reference with loaded submission.results to reconnect after save response from DB
        var exampleParticipationSet = existingTutorParticipation.getTrainedExampleParticipations();

        existingTutorParticipation = existingTutorParticipation.addTrainedExampleParticipation(originalExampleParticipation);
        exampleParticipationService.save(originalExampleParticipation);
        existingTutorParticipation = tutorParticipationRepository.saveAndFlush(existingTutorParticipation);

        existingTutorParticipation.setTrainedExampleParticipations(exampleParticipationSet);
        existingTutorParticipation.getTrainedExampleParticipations().add(originalExampleParticipation);

        return existingTutorParticipation;
    }

    /**
     * This method removes the tutor participation for the example participation of an exercise
     *
     * @param exercise the exercise to which the example participation and tutor participation are linked to
     * @param user     the user for which the tutor participation should be removed
     */
    public void removeTutorParticipations(Exercise exercise, User user) {
        if (!tutorParticipationRepository.existsByAssessedExerciseIdAndTutorId(exercise.getId(), user.getId())) {
            return;
        }

        Set<ExampleParticipation> exampleParticipations = exampleParticipationRepository.findAllByExerciseId(exercise.getId());
        TutorParticipation tutorParticipation = tutorParticipationRepository.findWithEagerExampleParticipationAndResultsByAssessedExerciseAndTutor(exercise, user);

        for (ExampleParticipation exampleParticipation : exampleParticipations) {
            Optional<ExampleParticipation> exampleParticipationWithTutorParticipation = exampleParticipationRepository
                    .findByIdWithResultsAndTutorParticipations(exampleParticipation.getId());
            if (exampleParticipationWithTutorParticipation.isPresent()) {
                exampleParticipationWithTutorParticipation.get().removeTutorParticipation(tutorParticipation);
                tutorParticipationRepository.delete(tutorParticipation);
            }
        }
    }
}
