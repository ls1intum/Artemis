package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.util.List;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.repository.ExerciseGroupRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service Implementation for managing ExerciseGroup.
 */
@Service
public class ExerciseGroupService {

    private final Logger log = LoggerFactory.getLogger(ExamService.class);

    private final ExerciseGroupRepository exerciseGroupRepository;

    private final ExamService examService;

    public ExerciseGroupService(ExerciseGroupRepository exerciseGroupRepository, ExamService examService) {
        this.exerciseGroupRepository = exerciseGroupRepository;
        this.examService = examService;
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

    /**
     * Checks if the current user is allowed to manage exams of the given course, that the exam exists,
     * that the exam belongs to the given course and the exercise group belongs to the given exam.
     *
     * @param courseId          The id of the course
     * @param examId            The id of the exam
     * @param exerciseGroupId   The id of the exercise group
     * @param <X>               The type of the return type of the requesting route so that the
     *                          response can be returned there
     * @return an Optional with a typed ResponseEntity. If it is empty all checks passed
     */
    public <X> Optional<ResponseEntity<X>> checkCourseAndExamAndExerciseGroupAccess(Long courseId, Long examId, Long exerciseGroupId) {
        Optional<ResponseEntity<X>> courseAndExamAccessFailure = examService.checkCourseAndExamAccess(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure;
        }
        Optional<ExerciseGroup> exerciseGroup = exerciseGroupRepository.findById(exerciseGroupId);
        if (exerciseGroup.isEmpty()) {
            return Optional.of(notFound());
        }
        if (!exerciseGroup.get().getExam().getId().equals(exerciseGroupId)) {
            return Optional.of(forbidden());
        }
        return Optional.empty();
    }
}
