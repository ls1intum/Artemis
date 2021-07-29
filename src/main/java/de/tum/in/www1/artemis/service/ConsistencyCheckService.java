package de.tum.in.www1.artemis.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.dto.ConsistencyErrorDTO;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseService;

/**
 * Service Implementation for consistency checks
 * of programming exercises
 */
@Service
public class ConsistencyCheckService {

    private final Logger log = LoggerFactory.getLogger(ConsistencyCheckService.class);

    private final ProgrammingExerciseService programmingExerciseService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    public ConsistencyCheckService(ProgrammingExerciseService programmingExerciseService, ProgrammingExerciseRepository programmingExerciseRepository) {
        this.programmingExerciseService = programmingExerciseService;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    /**
     * Check VCS and CI consistency of a given programming exercise
     *
     * @param exerciseId of the programming exercise to check
     * @return List containing the resulting errors, if any.
     */
    public List<ConsistencyErrorDTO> checkConsistencyOfProgrammingExercise(long exerciseId) {
        log.debug("Checking consistency for programming exercise [{}]", exerciseId);
        return programmingExerciseService.checkConsistencyOfProgrammingExercise(exerciseId);
    }

    /**
     * Check VCS and CI consistency of programming exercises contained in
     * a given course
     * @param courseId of the course containing the programming exercises to check
     * @return List containing the resulting errors, if any.
     */
    public List<ConsistencyErrorDTO> checkConsistencyOfCourse(long courseId) {
        log.debug("Checking consistency for course [{}]", courseId);
        List<ConsistencyErrorDTO> result = new ArrayList<>();
        List<ProgrammingExercise> exercises = programmingExerciseRepository.findAllByCourseWithTemplateAndSolutionParticipation(courseId);

        for (ProgrammingExercise programmingExercise : exercises) {
            result.addAll(programmingExerciseService.checkConsistencyOfProgrammingExercise(programmingExercise));
        }

        return result;
    }

}
