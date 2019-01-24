package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.ExampleSubmission;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ExampleSubmissionService {

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    public ExampleSubmissionService(ExampleSubmissionRepository exampleSubmissionRepository) {
        this.exampleSubmissionRepository = exampleSubmissionRepository;
    }

    /**
     * Saves the given example submission and the corresponding model and creates the result if necessary.
     * Rolls back if inserting fails - occurs for concurrent createExampleSubmission() calls.
     *
     * @param exampleSubmission the submission to save
     * @return the exampleSubmission entity
     */
    @Transactional(rollbackFor = Exception.class)
    public ExampleSubmission save(ExampleSubmission exampleSubmission) {
        exampleSubmission = exampleSubmissionRepository.save(exampleSubmission);

        return exampleSubmission;
    }
}
