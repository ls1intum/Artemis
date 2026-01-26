package de.tum.cit.aet.artemis.modeling.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.modeling.config.ModelingEnabled;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.repository.ModelingSubmissionRepository;
import de.tum.cit.aet.artemis.modeling.service.ModelingSubmissionService;

/**
 * API for modeling submission operations.
 */
@Conditional(ModelingEnabled.class)
@Controller
@Lazy
public class ModelingSubmissionApi extends AbstractModelingApi {

    private final ModelingSubmissionRepository modelingSubmissionRepository;

    private final ModelingSubmissionService modelingSubmissionService;

    public ModelingSubmissionApi(ModelingSubmissionRepository modelingSubmissionRepository, ModelingSubmissionService modelingSubmissionService) {
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.modelingSubmissionService = modelingSubmissionService;
    }

    public ModelingSubmission save(ModelingSubmission submission) {
        return modelingSubmissionRepository.save(submission);
    }

    /**
     * Finds a modeling submission by id, throwing an exception if not found.
     *
     * @param submissionId the id of the submission
     * @return the found submission
     */
    public ModelingSubmission findByIdElseThrow(long submissionId) {
        return modelingSubmissionRepository.findByIdElseThrow(submissionId);
    }

    /**
     * Handles a modeling submission by saving it and creating the result if necessary.
     *
     * @param modelingSubmission the submission to handle
     * @param exercise           the exercise the submission belongs to
     * @param user               the user who initiated the save
     * @return the saved modeling submission
     */
    public ModelingSubmission handleModelingSubmission(ModelingSubmission modelingSubmission, ModelingExercise exercise, User user) {
        return modelingSubmissionService.handleModelingSubmission(modelingSubmission, exercise, user);
    }

    /**
     * Hides details of a submission that should not be visible to the user.
     *
     * @param submission the submission to hide details from
     * @param user       the user for whom details should be hidden
     */
    public void hideDetails(Submission submission, User user) {
        modelingSubmissionService.hideDetails(submission, user);
    }
}
