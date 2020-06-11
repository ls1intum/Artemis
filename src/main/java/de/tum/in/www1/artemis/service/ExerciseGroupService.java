package de.tum.in.www1.artemis.service;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.repository.ExerciseGroupRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service implementation for managing ExerciseGroups
 */
@Service
public class ExerciseGroupService {

    private final Logger log = LoggerFactory.getLogger(ExerciseGroupService.class);

    private final ExerciseGroupRepository exerciseGroupRepository;

    public ExerciseGroupService(ExerciseGroupRepository exerciseGroupRepository) {
        this.exerciseGroupRepository = exerciseGroupRepository;
    }

    /**
     * Get one exerciseGroup by id with the corresponding exam.
     *
     * @param exerciseGroupId the id of the entity
     * @return the entity
     */
    @NotNull
    public ExerciseGroup findOneWithExam(Long exerciseGroupId) {
        log.debug("Request to get exerciseGroup with exam : {}", exerciseGroupId);
        return exerciseGroupRepository.findByIdWithEagerExam(exerciseGroupId)
                .orElseThrow(() -> new EntityNotFoundException("ExerciseGroup with id: \"" + exerciseGroupId + "\" does not exist"));
    }
}
