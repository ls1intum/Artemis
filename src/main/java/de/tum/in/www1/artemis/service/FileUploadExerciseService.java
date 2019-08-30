package de.tum.in.www1.artemis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.repository.FileUploadExerciseRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
@Transactional
public class FileUploadExerciseService {

    private final Logger log = LoggerFactory.getLogger(FileUploadExerciseService.class);

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    private final ParticipationService participationService;

    public FileUploadExerciseService(ParticipationService participationService, FileUploadExerciseRepository fileUploadExerciseRepository) {
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.participationService = participationService;
    }

    /**
     * Get one file upload exercise by id.
     *
     * @param exerciseId the id of the entity
     * @return the entity
     */
    @Transactional(readOnly = true)
    public FileUploadExercise findOne(Long exerciseId) {
        log.debug("Request to get File Upload Exercise : {}", exerciseId);
        return fileUploadExerciseRepository.findById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Exercise with id: \"" + exerciseId + "\" does not exist"));
    }

    /**
     * Delete the file upload exercise by id.
     *
     * @param exerciseId the id of the entity
     */
    @Transactional
    public void delete(Long exerciseId) {
        log.debug("Request to delete File Upload Exercise : {}", exerciseId);
        // delete all participations belonging to this file upload exercise
        participationService.deleteAllByExerciseId(exerciseId, false, false);
        fileUploadExerciseRepository.deleteById(exerciseId);
    }
}
