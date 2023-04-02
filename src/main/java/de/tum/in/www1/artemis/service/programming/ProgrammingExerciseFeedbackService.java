package de.tum.in.www1.artemis.service.programming;

import java.util.*;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseTaskService;

@Service
public class ProgrammingExerciseFeedbackService {

    private final ProgrammingExerciseTestCaseRepository testCaseRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    public ProgrammingExerciseFeedbackService(ProgrammingExerciseTestCaseRepository testCaseRepository, ProgrammingExerciseTaskService programmingExerciseTaskService) {
        this.testCaseRepository = testCaseRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
    }
}
