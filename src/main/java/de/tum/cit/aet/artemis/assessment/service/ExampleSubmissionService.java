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
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.ExampleSubmissionRequestDTO;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.repository.TutorParticipationRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
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
public class ExampleSubmissionService {

    private static final String ENTITY_NAME = "exampleSubmission";

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final SubmissionRepository submissionRepository;

    private final ExerciseRepository exerciseRepository;

    private final Optional<ModelingExerciseImportApi> modelingExerciseImportApi;

    private final Optional<TextSubmissionImportApi> textSubmissionImportApi;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final TutorParticipationRepository tutorParticipationRepository;

    public ExampleSubmissionService(ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository, ExerciseRepository exerciseRepository,
            Optional<ModelingExerciseImportApi> modelingExerciseImportApi, Optional<TextSubmissionImportApi> textSubmissionImportApi,
            GradingCriterionRepository gradingCriterionRepository, TutorParticipationRepository tutorParticipationRepository) {
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.submissionRepository = submissionRepository;
        this.exerciseRepository = exerciseRepository;
        this.modelingExerciseImportApi = modelingExerciseImportApi;
        this.textSubmissionImportApi = textSubmissionImportApi;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.tutorParticipationRepository = tutorParticipationRepository;
    }

    /**
     * Creates an example submission for the given exercise from the request body. The submission entity is built from the
     * exercise type (text or modeling); the request never carries an entity.
     *
     * @param exercise the managed exercise the example submission belongs to
     * @param request  the request body
     * @return the saved example submission
     */
    public ExampleSubmission create(Exercise exercise, ExampleSubmissionRequestDTO request) {
        ExampleSubmission exampleSubmission = new ExampleSubmission();
        exampleSubmission.setExercise(exercise);
        exampleSubmission.setSubmission(newSubmissionFor(exercise, request.submission()));
        applyMetadata(exampleSubmission, request);
        return save(exampleSubmission);
    }

    /**
     * Applies the request body to an existing example submission: metadata and the submitted content. The example
     * assessment is not touched; it is managed through the exercise type's example assessment endpoint.
     *
     * @param exampleSubmission the example submission loaded with its submission and results
     * @param request           the request body
     * @return the saved example submission
     */
    public ExampleSubmission update(ExampleSubmission exampleSubmission, ExampleSubmissionRequestDTO request) {
        applyContent(exampleSubmission.getSubmission(), request.submission());
        applyMetadata(exampleSubmission, request);
        return save(exampleSubmission);
    }

    private static void applyMetadata(ExampleSubmission exampleSubmission, ExampleSubmissionRequestDTO request) {
        // null means "keep the default"; on create there is nothing to keep, so it means "not used for tutorial".
        if (request.usedForTutorial() != null) {
            exampleSubmission.setUsedForTutorial(request.usedForTutorial());
        }
        else if (exampleSubmission.getId() == null) {
            exampleSubmission.setUsedForTutorial(Boolean.FALSE);
        }
        exampleSubmission.setAssessmentExplanation(request.assessmentExplanation());
    }

    private static Submission newSubmissionFor(Exercise exercise, ExampleSubmissionRequestDTO.SubmissionRequestDTO content) {
        if (content == null) {
            // save() rejects the missing submission with the error the client already knows
            return null;
        }
        Submission submission = switch (exercise) {
            case TextExercise ignored -> new TextSubmission();
            case ModelingExercise ignored -> new ModelingSubmission();
            default -> throw new BadRequestAlertException("Example submissions are not supported for this exercise type", ENTITY_NAME, "exerciseTypeNotSupported");
        };
        applyContent(submission, content);
        return submission;
    }

    private static void applyContent(Submission submission, ExampleSubmissionRequestDTO.SubmissionRequestDTO content) {
        if (content == null) {
            return;
        }
        switch (submission) {
            case TextSubmission textSubmission -> textSubmission.setText(content.text());
            case ModelingSubmission modelingSubmission -> {
                modelingSubmission.setModel(content.model());
                modelingSubmission.setExplanationText(content.explanationText());
            }
            default -> {
            }
        }
    }

    /**
     * First saves the corresponding submission with the exampleSubmission flag. Then the example submission itself is saved.
     *
     * @param exampleSubmission the example submission to save
     * @return the exampleSubmission entity
     */
    public ExampleSubmission save(ExampleSubmission exampleSubmission) {
        Submission submission = exampleSubmission.getSubmission();
        if (submission == null) {
            // An example submission is the submission it shows, so one without a submission says nothing. The column
            // requires it, and answering the request tells the caller what is wrong instead of failing on the insert.
            throw new BadRequestAlertException("An example submission must reference a submission", "exampleSubmission", "submissionMissing");
        }
        submission.setExampleSubmission(true);
        // Result.exerciseId is a non-null FK column the cascade merge writes back. Clients echo results they loaded from
        // DTO-shaped endpoints that do not carry it, so derive it from the example submission's (already checked) exercise
        // instead of trusting the payload.
        Long exerciseId = exampleSubmission.getExercise().getId();
        for (Result result : submission.getResults()) {
            if (result != null) {
                result.setExerciseId(exerciseId);
            }
        }
        // Rebuild connection between result and submission, if it has been lost, because hibernate needs it
        if (submission.getLatestResult() != null && submission.getLatestResult().getSubmission() == null) {
            submission.getLatestResult().setSubmission(submission);
        }
        submissionRepository.save(submission);
        return exampleSubmissionRepository.save(exampleSubmission);
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
            var api = modelingExerciseImportApi.orElseThrow(() -> new ModelingApiNotPresentException(ModelingExerciseImportApi.class));
            ModelingSubmission modelingSubmission = (ModelingSubmission) submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(submissionId);
            checkGivenExerciseIdSameForSubmissionParticipation(exercise.getId(), modelingSubmission.getParticipation().getExercise().getId());
            // example submission does not need participation
            modelingSubmission.setParticipation(null);

            newExampleSubmission.setSubmission(api.copySubmission(modelingSubmission, gradingInstructionCopyTracker));
        }
        else if (exercise instanceof TextExercise) {
            var api = textSubmissionImportApi.orElseThrow(() -> new TextApiNotPresentException(TextSubmissionApi.class));
            TextSubmission textSubmission = api.importStudentSubmission(submissionId, exercise.getId(), gradingInstructionCopyTracker);
            newExampleSubmission.setSubmission(textSubmission);
        }
        else {
            // Only modeling and text exercises can copy a student submission into an example submission. For anything
            // else there is nothing to copy, and the example submission would be stored without the submission it is
            // supposed to show.
            throw new BadRequestAlertException("Example submissions cannot be imported for this exercise type", "exampleSubmission", "exerciseTypeNotSupported");
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
