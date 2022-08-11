package de.tum.in.www1.artemis.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.*;

@Service
public class ModelingExerciseImportService extends ExerciseImportService {

    private final Logger log = LoggerFactory.getLogger(ModelingExerciseImportService.class);

    private final ModelingExerciseRepository modelingExerciseRepository;

    public ModelingExerciseImportService(ModelingExerciseRepository modelingExerciseRepository, ExampleSubmissionRepository exampleSubmissionRepository,
            SubmissionRepository submissionRepository, ResultRepository resultRepository) {
        super(exampleSubmissionRepository, submissionRepository, resultRepository);
        this.modelingExerciseRepository = modelingExerciseRepository;
    }

    /**
     * Imports a modeling exercise creating a new entity, copying all basic values and saving it in the database.
     * All basic include everything except Student-, Tutor participations, and student questions. <br>
     * This method calls {@link #copyModelingExerciseBasis(Exercise, Map<Long, GradingInstruction>)} to set up the basis of the exercise
     * {@link #copyExampleSubmission(Exercise, Exercise)} for a hard copy of the example submissions.
     *
     * @param templateExercise The template exercise which should get imported
     * @param importedExercise The new exercise already containing values which should not get copied, i.e. overwritten
     * @return The newly created exercise
     */
    @NotNull
    public ModelingExercise importModelingExercise(ModelingExercise templateExercise, ModelingExercise importedExercise) {
        log.debug("Creating a new Exercise based on exercise {}", templateExercise.getId());
        Map<Long, GradingInstruction> gradingInstructionCopyTracker = new HashMap<>();
        ModelingExercise newExercise = copyModelingExerciseBasis(importedExercise, gradingInstructionCopyTracker);
        newExercise.setKnowledge(templateExercise.getKnowledge());
        modelingExerciseRepository.save(newExercise);
        newExercise.setExampleSubmissions(copyExampleSubmission(templateExercise, newExercise, gradingInstructionCopyTracker));
        return newExercise;
    }

    /** This helper method copies all attributes of the {@code importedExercise} into the new exercise.
     * Here we ignore all external entities as well as the start-, end-, and assessment due date.
     * Also fills {@code gradingInstructionCopyTracker}.
     *
     * @param importedExercise The exercise from which to copy the basis
     * @param gradingInstructionCopyTracker  The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     * @return the cloned TextExercise basis
     */
    @NotNull
    private ModelingExercise copyModelingExerciseBasis(Exercise importedExercise, Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        log.debug("Copying the exercise basis from {}", importedExercise);
        ModelingExercise newExercise = new ModelingExercise();
        super.copyExerciseBasis(newExercise, importedExercise, gradingInstructionCopyTracker);

        newExercise.setDiagramType(((ModelingExercise) importedExercise).getDiagramType());
        newExercise.setExampleSolutionModel(((ModelingExercise) importedExercise).getExampleSolutionModel());
        newExercise.setExampleSolutionExplanation(((ModelingExercise) importedExercise).getExampleSolutionExplanation());
        return newExercise;
    }

    /** This functions does a hard copy of the example submissions contained in {@code templateExercise}.
     * To copy the corresponding Submission entity this function calls {@link #copySubmission(Submission, Map)}
     *
     * @param templateExercise {TextExercise} The original exercise from which to fetch the example submissions
     * @param newExercise The new exercise in which we will insert the example submissions
     * @return The cloned set of example submissions
     */
    Set<ExampleSubmission> copyExampleSubmission(Exercise templateExercise, Exercise newExercise) {
        return copyExampleSubmission(templateExercise, newExercise, new HashMap<>());
    }

    /** This functions does a hard copy of the example submissions contained in {@code templateExercise}.
     * To copy the corresponding Submission entity this function calls {@link #copySubmission(Submission, Map)}
     *
     * @param templateExercise {TextExercise} The original exercise from which to fetch the example submissions
     * @param newExercise The new exercise in which we will insert the example submissions
     * @param gradingInstructionCopyTracker  The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     * @return The cloned set of example submissions
     */
    Set<ExampleSubmission> copyExampleSubmission(Exercise templateExercise, Exercise newExercise, Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        log.debug("Copying the ExampleSubmissions to new Exercise: {}", newExercise);
        Set<ExampleSubmission> newExampleSubmissions = new HashSet<>();
        for (ExampleSubmission originalExampleSubmission : templateExercise.getExampleSubmissions()) {
            ModelingSubmission originalSubmission = (ModelingSubmission) originalExampleSubmission.getSubmission();
            ModelingSubmission newSubmission = (ModelingSubmission) copySubmission(originalSubmission, gradingInstructionCopyTracker);

            ExampleSubmission newExampleSubmission = new ExampleSubmission();
            newExampleSubmission.setExercise(newExercise);
            newExampleSubmission.setSubmission(newSubmission);
            newExampleSubmission.setAssessmentExplanation(originalExampleSubmission.getAssessmentExplanation());

            exampleSubmissionRepository.save(newExampleSubmission);
            newExampleSubmissions.add(newExampleSubmission);
        }
        return newExampleSubmissions;
    }

    /**
     * This helper function does a hard copy of the {@code originalSubmission} and stores the values in {@code newSubmission}.
     * To copy the submission results this function calls {@link ExerciseImportService#copyExampleResult(Result, Submission, Map)} respectively.
     *
     * @param originalSubmission  The original submission to be copied.
     * @param gradingInstructionCopyTracker  The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     * @return The cloned submission
     */
    Submission copySubmission(Submission originalSubmission, Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        ModelingSubmission newSubmission = new ModelingSubmission();
        if (originalSubmission != null) {
            log.debug("Copying the Submission to new ExampleSubmission: {}", newSubmission);
            newSubmission.setExampleSubmission(true);
            newSubmission.setSubmissionDate(originalSubmission.getSubmissionDate());
            newSubmission.setType(originalSubmission.getType());
            newSubmission.setParticipation(originalSubmission.getParticipation());
            newSubmission.setExplanationText(((ModelingSubmission) originalSubmission).getExplanationText());
            newSubmission.setModel(((ModelingSubmission) originalSubmission).getModel());

            newSubmission = submissionRepository.saveAndFlush(newSubmission);
            if (originalSubmission.getLatestResult() != null) {
                newSubmission.addResult(copyExampleResult(originalSubmission.getLatestResult(), newSubmission, gradingInstructionCopyTracker));
            }
            newSubmission = submissionRepository.saveAndFlush(newSubmission);
        }
        return newSubmission;
    }
}
