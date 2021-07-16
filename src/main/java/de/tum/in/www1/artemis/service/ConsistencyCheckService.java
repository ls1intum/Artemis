package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.dto.ConsistencyErrorDTO;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.ArrayList;
import java.util.List;

/**
 * Service Implementation for consistency checks
 * of programming exercises
 */
@Service
public class ConsistencyCheckService {

    private final Logger log = LoggerFactory.getLogger(ConsistencyCheckService.class);

    private final ProgrammingExerciseService programmingExerciseService;

    private final CourseRepository courseRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    public ConsistencyCheckService(ProgrammingExerciseService programmingExerciseService, CourseRepository courseRepository,
                                   ProgrammingExerciseRepository programmingExerciseRepository) {
        this.programmingExerciseService = programmingExerciseService;
        this.courseRepository = courseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    /**
     * Check VCS and CI consistency of a given programming exercise
     *
     * @param exerciseId of the programming exercise to check
     * @return List containing the resulting errors, if any.
     */
    public List<ConsistencyErrorDTO> checkConsistencyOfProgrammingExercise(long exerciseId) {
        return programmingExerciseService.checkConsistencyOfProgrammingExercise(exerciseId);
    }

    /**
     * Check VCS and CI consistency of programming exercises contained in
     * a given course
     * @param courseId of the course containing the programming exercises to check
     * @return List containing the resulting errors, if any.
     */
    public List<ConsistencyErrorDTO> checkConsistencyOfCourse(long courseId) {
        List<ConsistencyErrorDTO> result = new ArrayList<>();
        List<ProgrammingExercise> exercises = programmingExerciseRepository.findAllByCourseWithTemplateAndSolutionParticipation(courseId);

        for (ProgrammingExercise programmingExercise : exercises) {
            result.addAll(programmingExerciseService.checkConsistencyOfProgrammingExercise(programmingExercise));
        }

        return result;
    }

}
