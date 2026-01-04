package de.tum.cit.aet.artemis.modeling.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.modeling.config.ModelingEnabled;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.repository.ModelingSubmissionRepository;

/**
 * API for modeling submission operations.
 */
@Conditional(ModelingEnabled.class)
@Controller
@Lazy
public class ModelingSubmissionApi extends AbstractModelingApi {

    private final ModelingSubmissionRepository modelingSubmissionRepository;

    public ModelingSubmissionApi(ModelingSubmissionRepository modelingSubmissionRepository) {
        this.modelingSubmissionRepository = modelingSubmissionRepository;
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
}
