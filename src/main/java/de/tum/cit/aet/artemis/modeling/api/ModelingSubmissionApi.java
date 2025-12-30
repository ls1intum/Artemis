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
}
