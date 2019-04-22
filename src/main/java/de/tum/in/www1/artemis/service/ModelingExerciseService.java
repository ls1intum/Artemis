package de.tum.in.www1.artemis.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ComplaintResponseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.repository.ModelingExerciseRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
@Transactional
public class ModelingExerciseService {

    private final Logger log = LoggerFactory.getLogger(ModelingExerciseService.class);

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final ParticipationService participationService;

    private final ComplaintRepository complaintRepository;

    private final ComplaintResponseRepository complaintResponseRepository;

    public ModelingExerciseService(ParticipationService participationService,
                                   ModelingExerciseRepository modelingExerciseRepository,
                                   ComplaintRepository complaintRepository,
                                   ComplaintResponseRepository complaintResponseRepository) {
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.participationService = participationService;
        this.complaintRepository = complaintRepository;
        this.complaintResponseRepository = complaintResponseRepository;
    }

    /**
     * Get one modeling exercise by id.
     *
     * @param id the id of the entity
     * @return the entity
     */
    @Transactional(readOnly = true)
    public ModelingExercise findOne(Long id) {
        log.debug("Request to get Modeling Exercise : {}", id);
        return modelingExerciseRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Exercise with id: \"" + id + "\" does not exist"));
    }

    /**
     * Delete the modeling exercise by id.
     *
     * @param id the id of the entity
     */
    @Transactional
    public void delete(Long id) {
        log.debug("Request to delete Modeling Exercise : {}", id);
        // delete all complaint responses belonging to this exercise
        complaintResponseRepository.deleteByComplaint_Result_Participation_Exercise_Id(id);
        // delete all complaints belonging to this exercise
        complaintRepository.deleteByResult_Participation_Exercise_Id(id);
        // delete all participations belonging to this modeling exercise
        participationService.deleteAllByExerciseId(id, false, false);
        modelingExerciseRepository.deleteById(id);
        // clean data on file system
        try {
            Path exercisePath = Paths.get(Constants.FILEPATH_COMPASS + File.separator + id);
            if (Files.exists(exercisePath)) {
                boolean success = FileSystemUtils.deleteRecursively(exercisePath.toFile());
                if (!success) {
                    log.error("Unable to delete compass directory for exercise: " + id);
                }
            }
        }
        catch (SecurityException | InvalidPathException e) {
            log.error("Error when trying to find and delete compass directory for exercise: " + id, e);
        }
    }
}
