package de.tum.in.www1.artemis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.repository.FileUploadExerciseRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class FileUploadExerciseService {

    private final Logger log = LoggerFactory.getLogger(FileUploadExerciseService.class);

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    public FileUploadExerciseService(FileUploadExerciseRepository fileUploadExerciseRepository) {
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
    }

    /**
     * Get one file upload exercise by id.
     *
     * @param exerciseId the id of the entity
     * @return the entity
     */
    public FileUploadExercise findOne(Long exerciseId) {
        log.debug("Request to get File Upload Exercise : {}", exerciseId);
        return fileUploadExerciseRepository.findById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Exercise with id: \"" + exerciseId + "\" does not exist"));
    }
}
