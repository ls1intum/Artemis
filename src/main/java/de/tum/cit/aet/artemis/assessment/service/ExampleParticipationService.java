package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.ExampleParticipation;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.dto.ExampleParticipationInputDTO;
import de.tum.cit.aet.artemis.assessment.repository.ExampleParticipationRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.repository.TutorParticipationRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.modeling.api.ModelingExerciseImportApi;
import de.tum.cit.aet.artemis.modeling.config.ModelingApiNotPresentException;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.text.api.TextSubmissionApi;
import de.tum.cit.aet.artemis.text.api.TextSubmissionImportApi;
import de.tum.cit.aet.artemis.text.config.TextApiNotPresentException;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ExampleParticipationService {

    private static final String ENTITY_NAME = "exampleParticipation";

    private final ExampleParticipationRepository exampleParticipationRepository;

    private final SubmissionRepository submissionRepository;

    private final Optional<ModelingExerciseImportApi> modelingExerciseImportApi;

    private final Optional<TextSubmissionImportApi> textSubmissionImportApi;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final TutorParticipationRepository tutorParticipationRepository;

    public ExampleParticipationService(ExampleParticipationRepository exampleParticipationRepository, SubmissionRepository submissionRepository,
            Optional<TextSubmissionImportApi> textSubmissionImportApi, Optional<ModelingExerciseImportApi> modelingExerciseImportApi,
            GradingCriterionRepository gradingCriterionRepository, TutorParticipationRepository tutorParticipationRepository) {
        this.exampleParticipationRepository = exampleParticipationRepository;
        this.submissionRepository = submissionRepository;
        this.modelingExerciseImportApi = modelingExerciseImportApi;
        this.textSubmissionImportApi = textSubmissionImportApi;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.tutorParticipationRepository = tutorParticipationRepository;
    }

    /**
     * Creates a new example participation with its associated submission.
     * The submission must be saved after the participation to satisfy the NOT NULL constraint on participation_id.
     *
     * @param exampleParticipation the new example participation to create
     * @return the created exampleParticipation entity
     */
    public ExampleParticipation create(ExampleParticipation exampleParticipation) {
        exampleParticipation.setInitializationState(InitializationState.INITIALIZED);

        // Extract the submission - it may have been added via addSubmission() in the DTO
        // We need to save it separately after the participation has an ID
        Submission submission = exampleParticipation.getSubmission();

        // Save the example participation first to get an ID
        // Note: submissions are NOT cascaded, so the submission in the set won't be persisted here
        ExampleParticipation savedParticipation = exampleParticipationRepository.save(exampleParticipation);

        // Now save the submission with the correct participation reference
        if (submission != null) {
            submission.setExampleSubmission(true);
            submission.setParticipation(savedParticipation);
            submissionRepository.saveAndFlush(submission);
            // The submission is already in savedParticipation.getSubmissions() from addSubmission() call
        }

        return savedParticipation;
    }

    /**
     * Updates an existing example participation by loading it from the database first to avoid orphan removal issues.
     * Only updates the submission content and example participation metadata, preserving all relationships.
     *
     * @param exampleParticipation the example participation with updated values
     * @return the updated exampleParticipation entity
     */
    public ExampleParticipation update(ExampleParticipation exampleParticipation) {
        // Load the existing example participation from DB to preserve relationships
        ExampleParticipation existingExampleParticipation = exampleParticipationRepository.findByIdWithEagerResultAndFeedbackElseThrow(exampleParticipation.getId());

        // Update metadata fields
        existingExampleParticipation.setUsedForTutorial(exampleParticipation.getUsedForTutorial());
        existingExampleParticipation.setAssessmentExplanation(exampleParticipation.getAssessmentExplanation());

        // Update the submission content if provided
        Submission clientSubmission = exampleParticipation.getSubmission();
        Submission existingSubmission = existingExampleParticipation.getSubmission();
        if (clientSubmission != null && existingSubmission != null) {
            // Update submission content based on type
            updateSubmissionContent(existingSubmission, clientSubmission);
            existingSubmission.setExampleSubmission(true);
            // Rebuild connection between result and submission, if it has been lost
            if (existingSubmission.getLatestResult() != null && existingSubmission.getLatestResult().getSubmission() == null) {
                existingSubmission.getLatestResult().setSubmission(existingSubmission);
            }
            submissionRepository.save(existingSubmission);
        }

        return exampleParticipationRepository.save(existingExampleParticipation);
    }

    /**
     * Updates the content of an existing submission from a client-provided submission.
     * Only copies content fields, not relationships or IDs.
     *
     * @param existing the existing submission to update
     * @param client   the client-provided submission with new content
     */
    private void updateSubmissionContent(Submission existing, Submission client) {
        if (existing instanceof TextSubmission existingText && client instanceof TextSubmission clientText) {
            existingText.setText(clientText.getText());
        }
        else if (existing instanceof ModelingSubmission existingModeling && client instanceof ModelingSubmission clientModeling) {
            existingModeling.setModel(clientModeling.getModel());
            existingModeling.setExplanationText(clientModeling.getExplanationText());
        }
        // Add other submission types as needed
    }

    /**
     * First saves the example participation, then the corresponding submission with the exampleSubmission flag.
     * Note: This method is maintained for backwards compatibility. Prefer using create() for new participations
     * and update() for existing participations.
     *
     * @param exampleParticipation the example participation to save
     * @return the exampleParticipation entity
     */
    public ExampleParticipation save(ExampleParticipation exampleParticipation) {
        if (exampleParticipation.getId() != null) {
            return update(exampleParticipation);
        }
        return create(exampleParticipation);
    }

    /**
     * Deletes an ExampleParticipation with the given ID, cleans up the tutor participations.
     * The submission and results are deleted via cascade.
     *
     * @param exampleParticipationId the ID of the ExampleParticipation which should be deleted
     */
    public void deleteById(long exampleParticipationId) {
        Optional<ExampleParticipation> optionalExampleParticipation = exampleParticipationRepository.findByIdWithResultsAndTutorParticipations(exampleParticipationId);

        if (optionalExampleParticipation.isPresent()) {
            ExampleParticipation exampleParticipation = optionalExampleParticipation.get();

            // Remove tutor participation associations
            tutorParticipationRepository.deleteAll(exampleParticipation.getTutorParticipations());
            exampleParticipation.setTutorParticipations(null);

            // Clear the submissions collection to prevent Hibernate from trying to merge entities
            // Make a copy of IDs first since we're clearing the collection
            Set<Long> submissionIds = exampleParticipation.getSubmissions().stream().map(Submission::getId).filter(Objects::nonNull).collect(Collectors.toSet());
            exampleParticipation.getSubmissions().clear();

            // Delete submissions that exist in the database
            for (Long submissionId : submissionIds) {
                if (submissionRepository.existsById(submissionId)) {
                    submissionRepository.deleteById(submissionId);
                }
            }

            // Delete the example participation using its ID to avoid merge issues
            exampleParticipationRepository.deleteById(exampleParticipation.getId());
        }
    }

    /**
     * Creates a new example participation from a DTO.
     *
     * @param dto      the DTO containing the example participation data
     * @param exercise the exercise this example participation belongs to
     * @return the created exampleParticipation entity
     */
    public ExampleParticipation createFromDTO(ExampleParticipationInputDTO dto, Exercise exercise) {
        ExampleParticipation exampleParticipation = dto.toEntity(exercise);
        return create(exampleParticipation);
    }

    /**
     * Updates an existing example participation from a DTO.
     *
     * @param dto the DTO containing the updated data
     * @return the updated exampleParticipation entity
     */
    public ExampleParticipation updateFromDTO(ExampleParticipationInputDTO dto) {
        // Load the existing example participation from DB to preserve relationships
        ExampleParticipation existingExampleParticipation = exampleParticipationRepository.findByIdWithEagerResultAndFeedbackElseThrow(dto.id());

        // Update metadata fields from DTO
        dto.applyMetadataTo(existingExampleParticipation);

        // Update the submission content if provided
        Submission existingSubmission = existingExampleParticipation.getSubmission();
        if (existingSubmission != null) {
            dto.applySubmissionContentTo(existingSubmission);
            existingSubmission.setExampleSubmission(true);
            // Rebuild connection between result and submission, if it has been lost
            if (existingSubmission.getLatestResult() != null && existingSubmission.getLatestResult().getSubmission() == null) {
                existingSubmission.getLatestResult().setSubmission(existingSubmission);
            }
            submissionRepository.save(existingSubmission);
        }

        return exampleParticipationRepository.save(existingExampleParticipation);
    }

    /**
     * Creates a new example participation by copying the student submission with its assessments.
     * Calls copySubmission of required service depending on type of exercise.
     *
     * @param submissionId The original student submission id to be copied
     * @param exercise     The exercise to which the example participation belongs
     * @return the exampleParticipation entity
     */
    public ExampleParticipation importStudentSubmissionAsExampleParticipation(Long submissionId, Exercise exercise) {
        ExampleParticipation newExampleParticipation = new ExampleParticipation();
        newExampleParticipation.setExercise(exercise);
        newExampleParticipation.setInitializationState(InitializationState.INITIALIZED);

        var gradingCriteria = this.gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exercise.getId());
        Map<Long, GradingInstruction> gradingInstructionCopyTracker = new HashMap<>();
        gradingCriteria.stream().flatMap(gradingCriterion -> gradingCriterion.getStructuredGradingInstructions().stream())
                .forEach(gradingInstruction -> gradingInstructionCopyTracker.put(gradingInstruction.getId(), gradingInstruction));

        // First save the example participation to get an ID (required because submission.participation_id is NOT NULL)
        ExampleParticipation savedParticipation = exampleParticipationRepository.save(newExampleParticipation);

        Submission copiedSubmission = null;
        if (exercise instanceof ModelingExercise) {
            var api = modelingExerciseImportApi.orElseThrow(() -> new ModelingApiNotPresentException(ModelingExerciseImportApi.class));
            ModelingSubmission modelingSubmission = (ModelingSubmission) submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(submissionId);
            checkGivenExerciseIdSameForSubmissionParticipation(exercise.getId(), modelingSubmission.getParticipation().getExercise().getId());

            copiedSubmission = api.copySubmission(modelingSubmission, gradingInstructionCopyTracker, savedParticipation);
        }
        if (exercise instanceof TextExercise) {
            var api = textSubmissionImportApi.orElseThrow(() -> new TextApiNotPresentException(TextSubmissionApi.class));
            copiedSubmission = api.importStudentSubmission(submissionId, exercise.getId(), gradingInstructionCopyTracker, savedParticipation);
        }

        if (copiedSubmission != null) {
            savedParticipation.addSubmission(copiedSubmission);
        }

        return savedParticipation;
    }

    /**
     * Checks the original exercise id is matched with the exercise id in the submission participation.
     *
     * @param originalExerciseId     given exercise id in the request
     * @param exerciseIdInSubmission exercise id in submission participation
     */
    public void checkGivenExerciseIdSameForSubmissionParticipation(long originalExerciseId, long exerciseIdInSubmission) {
        if (!Objects.equals(originalExerciseId, exerciseIdInSubmission)) {
            throw new BadRequestAlertException("ExerciseId does not match with the exerciseId in submission participation", ENTITY_NAME, "idNotMatched");
        }
    }
}
