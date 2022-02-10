package de.tum.in.www1.artemis.service;

import java.util.HashSet;
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
            SubmissionRepository submissionRepository, ResultRepository resultRepository, TextBlockRepository textBlockRepository) {
        super(exampleSubmissionRepository, submissionRepository, resultRepository, textBlockRepository);
        this.modelingExerciseRepository = modelingExerciseRepository;
    }

    /**
     * Imports a modeling exercise creating a new entity, copying all basic values and saving it in the database.
     * All basic include everything except Student-, Tutor participations, and student questions. <br>
     * This method calls {@link #copyModelingExerciseBasis(Exercise)} to set up the basis of the exercise
     * {@link #copyExampleSubmission(Exercise, Exercise)} for a hard copy of the example submissions.
     *
     * @param templateExercise The template exercise which should get imported
     * @param importedExercise The new exercise already containing values which should not get copied, i.e. overwritten
     * @return The newly created exercise
     */
    @NotNull
    public ModelingExercise importModelingExercise(ModelingExercise templateExercise, ModelingExercise importedExercise) {
        log.debug("Creating a new Exercise based on exercise {}", templateExercise.getId());
        ModelingExercise newExercise = copyModelingExerciseBasis(importedExercise);
        newExercise.setKnowledge(templateExercise.getKnowledge());
        modelingExerciseRepository.save(newExercise);
        newExercise.setExampleSubmissions(copyExampleSubmission(templateExercise, newExercise));
        return newExercise;
    }

    /** This helper method copies all attributes of the {@code importedExercise} into the new exercise.
     * Here we ignore all external entities as well as the start-, end-, and assessment due date.
     *
     * @param importedExercise The exercise from which to copy the basis
     * @return the cloned TextExercise basis
     */
    @NotNull
    private ModelingExercise copyModelingExerciseBasis(Exercise importedExercise) {
        log.debug("Copying the exercise basis from {}", importedExercise);
        ModelingExercise newExercise = new ModelingExercise();
        super.copyExerciseBasis(newExercise, importedExercise);

        newExercise.setDiagramType(((ModelingExercise) importedExercise).getDiagramType());
        newExercise.setSampleSolutionModel(((ModelingExercise) importedExercise).getSampleSolutionModel());
        newExercise.setSampleSolutionExplanation(((ModelingExercise) importedExercise).getSampleSolutionExplanation());
        return newExercise;
    }

    /** This functions does a hard copy of the example submissions contained in {@code templateExercise}.
     * To copy the corresponding Submission entity this function calls {@link #copySubmission(Submission)}
     *
     * @param templateExercise {TextExercise} The original exercise from which to fetch the example submissions
     * @param newExercise The new exercise in which we will insert the example submissions
     * @return The cloned set of example submissions
     */
    @Override
    Set<ExampleSubmission> copyExampleSubmission(Exercise templateExercise, Exercise newExercise) {
        log.debug("Copying the ExampleSubmissions to new Exercise: {}", newExercise);
        Set<ExampleSubmission> newExampleSubmissions = new HashSet<>();
        for (ExampleSubmission originalExampleSubmission : templateExercise.getExampleSubmissions()) {
            ModelingSubmission originalSubmission = (ModelingSubmission) originalExampleSubmission.getSubmission();
            ModelingSubmission newSubmission = (ModelingSubmission) copySubmission(originalSubmission);

            ExampleSubmission newExampleSubmission = new ExampleSubmission();
            newExampleSubmission.setExercise(newExercise);
            newExampleSubmission.setSubmission(newSubmission);
            newExampleSubmission.setAssessmentExplanation(originalExampleSubmission.getAssessmentExplanation());

            exampleSubmissionRepository.save(newExampleSubmission);
            newExampleSubmissions.add(newExampleSubmission);
        }
        return newExampleSubmissions;
    }

    /** This helper function does a hard copy of the {@code originalSubmission} and stores the values in {@code newSubmission}.
     * To copy the submission results this function calls {@link #copyExampleResult(Result, Submission)} respectively.
     *
     * @param originalSubmission The original submission to be copied.
     * @return The cloned submission
     */
    @Override
    Submission copySubmission(Submission originalSubmission) {
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
                newSubmission.addResult(copyExampleResult(originalSubmission.getLatestResult(), newSubmission));
            }
            newSubmission = submissionRepository.saveAndFlush(newSubmission);
        }
        return newSubmission;
    }
}
