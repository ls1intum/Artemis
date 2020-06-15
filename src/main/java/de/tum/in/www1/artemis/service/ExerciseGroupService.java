package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.util.List;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.repository.ExerciseGroupRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service Implementation for managing ExerciseGroup.
 */
@Service
public class ExerciseGroupService {

    private final Logger log = LoggerFactory.getLogger(ExerciseGroupService.class);

    private final ExerciseGroupRepository exerciseGroupRepository;

    private final ExamAccessService examAccessService;

    public ExerciseGroupService(ExerciseGroupRepository exerciseGroupRepository, ExamAccessService examAccessService) {
        this.exerciseGroupRepository = exerciseGroupRepository;
        this.examAccessService = examAccessService;
    }

    /**
     * Save an exerciseGroup
     *
     * @param exerciseGroup the entity to save
     * @return the persisted entity
     */
    public ExerciseGroup save(ExerciseGroup exerciseGroup) {
        log.debug("Request to save exerciseGroup : {}", exerciseGroup);
        return exerciseGroupRepository.save(exerciseGroup);
    }

    /**
     * Get one exercise group by id.
     *
     * @param exerciseGroupId the id of the exercise group
     * @return the entity
     */
    @NotNull
    public ExerciseGroup findOne(Long exerciseGroupId) {
        log.debug("Request to get exercise group : {}", exerciseGroupId);
        return exerciseGroupRepository.findById(exerciseGroupId)
                .orElseThrow(() -> new EntityNotFoundException("Exercise group with id \"" + exerciseGroupId + "\" does not exist"));
    }

    /**
     * Get all exercise groups for the given exam.
     *
     * @param examId the id of the exam
     * @return the list of all exercise groups
     */
    public List<ExerciseGroup> findAllByExamId(Long examId) {
        log.debug("REST request to get all exercise groups for Exam : {}", examId);
        return exerciseGroupRepository.findByExamId(examId);
    }

    /**
     * Delete the exercise group by id.
     *
     * @param exerciseGroupId the id of the entity
     */
    public void delete(Long exerciseGroupId) {
        log.debug("Request to delete exercise group : {}", exerciseGroupId);
        exerciseGroupRepository.deleteById(exerciseGroupId);
    }
}
