package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.GradingInstruction;
import de.tum.in.www1.artemis.repository.GradingInstructionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service Implementation for managing Structured Grading Instructions.
 */
@Service
@Transactional
public class GradingInstructionService {
    private final GradingInstructionRepository gradingInstructionRepository;

    public GradingInstructionService(GradingInstructionRepository gradingInstructionRepository) {
        this.gradingInstructionRepository = gradingInstructionRepository;
    }

    /**
     * Finds all Grading Instructions for a given Exercise
     *
     * @param exercise corresponding exercise
     * @return a List of all Exercises for the given course
     */
    @Transactional(readOnly = true)
    public List<GradingInstruction> findAllForExercise(Exercise exercise) { // Todo call methode in Exercise service
        return gradingInstructionRepository.findByExerciseId(exercise.getId());
    }
}
