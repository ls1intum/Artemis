package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.dto.ExampleSubmissionInputDTO;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.repository.TutorParticipationRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.service.ModelingExerciseImportService;
import de.tum.cit.aet.artemis.text.api.TextSubmissionApi;
import de.tum.cit.aet.artemis.text.api.TextSubmissionImportApi;
import de.tum.cit.aet.artemis.text.config.TextApiNotPresentException;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ExampleSubmissionService {

    private static final String ENTITY_NAME = "exampleSubmission";

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final SubmissionRepository submissionRepository;

    private final ExerciseRepository exerciseRepository;

    private final ModelingExerciseImportService modelingExerciseImportService;

    private final Optional<TextSubmissionImportApi> textSubmissionImportApi;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final TutorParticipationRepository tutorParticipationRepository;

    public ExampleSubmissionService(ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository, ExerciseRepository exerciseRepository,
            Optional<TextSubmissionImportApi> textSubmissionImportApi, ModelingExerciseImportService modelingExerciseImportService,
            GradingCriterionRepository gradingCriterionRepository, TutorParticipationRepository tutorParticipationRepository) {
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.submissionRepository = submissionRepository;
        this.exerciseRepository = exerciseRepository;
        this.modelingExerciseImportService = modelingExerciseImportService;
        this.textSubmissionImportApi = textSubmissionImportApi;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.tutorParticipationRepository = tutorParticipationRepository;
    }

    /**
     * Creates a new example submission with its associated submission.
     *
     * @param exampleSubmission the new example submission to create
     * @return the created exampleSubmission entity
     */
    public ExampleSubmission create(ExampleSubmission exampleSubmission) {
        Submission submission = exampleSubmission.getSubmission();
        if (submission != null) {
            submission.setExampleSubmission(true);
            submissionRepository.save(submission);
        }
        return exampleSubmissionRepository.save(exampleSubmission);
    }

    /**
     * Updates an existing example submission by loading it from the database first to avoid orphan removal issues.
     * Only updates the submission content and example submission metadata, preserving all relationships.
     *
     * @param exampleSubmission the example submission with updated values
     * @return the updated exampleSubmission entity
     */
    public ExampleSubmission update(ExampleSubmission exampleSubmission) {
        // Load the existing example submission from DB to preserve relationships and avoid orphan removal
        ExampleSubmission existingExampleSubmission = exampleSubmissionRepository.findByIdWithEagerResultAndFeedbackElseThrow(exampleSubmission.getId());

        // Update metadata fields
        existingExampleSubmission.setUsedForTutorial(exampleSubmission.isUsedForTutorial());
        existingExampleSubmission.setAssessmentExplanation(exampleSubmission.getAssessmentExplanation());

        // Update the submission content if provided
        Submission clientSubmission = exampleSubmission.getSubmission();
        Submission existingSubmission = existingExampleSubmission.getSubmission();
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

        return exampleSubmissionRepository.save(existingExampleSubmission);
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
     * First saves the corresponding submission with the exampleSubmission flag. Then the example submission itself is saved.
     * Note: This method is maintained for backwards compatibility. Prefer using create() for new submissions
     * and update() for existing submissions.
     *
     * @param exampleSubmission the example submission to save
     * @return the exampleSubmission entity
     */
    public ExampleSubmission save(ExampleSubmission exampleSubmission) {
        if (exampleSubmission.getId() != null) {
            return update(exampleSubmission);
        }
        return create(exampleSubmission);
    }

    /**
     * Deletes a ExampleSubmission with the given ID, cleans up the tutor participations, removes the result and the submission
     *
     * @param exampleSubmissionId the ID of the ExampleSubmission which should be deleted
     */
    public void deleteById(long exampleSubmissionId) {
        Optional<ExampleSubmission> optionalExampleSubmission = exampleSubmissionRepository.findByIdWithResultsAndTutorParticipations(exampleSubmissionId);

        if (optionalExampleSubmission.isPresent()) {
            ExampleSubmission exampleSubmission = optionalExampleSubmission.get();

            tutorParticipationRepository.deleteAll(exampleSubmission.getTutorParticipations());
            exampleSubmission.setTutorParticipations(null);

            Long exerciseId = exampleSubmission.getExercise().getId();
            Optional<Exercise> optionalExercise = exerciseRepository.findByIdWithEagerExampleSubmissions(exerciseId);

            // Remove the reference to the exercise when the example submission is deleted
            optionalExercise.ifPresent(exercise -> {
                exercise.removeExampleSubmission(exampleSubmission);
                exerciseRepository.save(exercise);
            });

            // due to Cascade.Remove this will also remove the submission and the result(s) in case they exist
            exampleSubmissionRepository.delete(exampleSubmission);
        }
    }

    /**
     * Creates a new example submission from a DTO.
     *
     * @param dto      the DTO containing the example submission data
     * @param exercise the exercise this example submission belongs to
     * @return the created exampleSubmission entity
     */
    public ExampleSubmission createFromDTO(ExampleSubmissionInputDTO dto, Exercise exercise) {
        ExampleSubmission exampleSubmission = dto.toEntity(exercise);
        return create(exampleSubmission);
    }

    /**
     * Updates an existing example submission from a DTO.
     *
     * @param dto the DTO containing the updated data
     * @return the updated exampleSubmission entity
     */
    public ExampleSubmission updateFromDTO(ExampleSubmissionInputDTO dto) {
        // Load the existing example submission from DB to preserve relationships and avoid orphan removal
        ExampleSubmission existingExampleSubmission = exampleSubmissionRepository.findByIdWithEagerResultAndFeedbackElseThrow(dto.id());

        // Update metadata fields from DTO
        dto.applyMetadataTo(existingExampleSubmission);

        // Update the submission content if provided
        Submission existingSubmission = existingExampleSubmission.getSubmission();
        if (existingSubmission != null) {
            dto.applySubmissionContentTo(existingSubmission);
            existingSubmission.setExampleSubmission(true);
            // Rebuild connection between result and submission, if it has been lost
            if (existingSubmission.getLatestResult() != null && existingSubmission.getLatestResult().getSubmission() == null) {
                existingSubmission.getLatestResult().setSubmission(existingSubmission);
            }
            submissionRepository.save(existingSubmission);
        }

        return exampleSubmissionRepository.save(existingExampleSubmission);
    }

    /**
     * Creates new example submission by copying the student submission with its assessments
     * calls copySubmission of required service depending on type of exercise
     *
     * @param submissionId The original student submission id to be copied
     * @param exercise     The exercise to which the example submission belongs
     * @return the exampleSubmission entity
     */
    public ExampleSubmission importStudentSubmissionAsExampleSubmission(Long submissionId, Exercise exercise) {
        ExampleSubmission newExampleSubmission = new ExampleSubmission();
        newExampleSubmission.setExercise(exercise);

        var gradingCriteria = this.gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exercise.getId());
        Map<Long, GradingInstruction> gradingInstructionCopyTracker = new HashMap<>();
        gradingCriteria.stream().flatMap(gradingCriterion -> gradingCriterion.getStructuredGradingInstructions().stream())
                .forEach(gradingInstruction -> gradingInstructionCopyTracker.put(gradingInstruction.getId(), gradingInstruction));

        if (exercise instanceof ModelingExercise) {
            ModelingSubmission modelingSubmission = (ModelingSubmission) submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(submissionId);
            checkGivenExerciseIdSameForSubmissionParticipation(exercise.getId(), modelingSubmission.getParticipation().getExercise().getId());
            // example submission does not need participation
            modelingSubmission.setParticipation(null);

            newExampleSubmission.setSubmission(modelingExerciseImportService.copySubmission(modelingSubmission, gradingInstructionCopyTracker));
        }
        if (exercise instanceof TextExercise) {
            var api = textSubmissionImportApi.orElseThrow(() -> new TextApiNotPresentException(TextSubmissionApi.class));
            TextSubmission textSubmission = api.importStudentSubmission(submissionId, exercise.getId(), gradingInstructionCopyTracker);
            newExampleSubmission.setSubmission(textSubmission);
        }
        return exampleSubmissionRepository.save(newExampleSubmission);
    }

    /**
     * Checks the original exercise id is matched with the exercise id in the submission participation
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
