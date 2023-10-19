package de.tum.in.www1.artemis.service.programming;

import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;

@Service
public class ProgrammingExerciseCreationService {

    private final ProgrammingExerciseService programmingExerciseService;

    public ProgrammingExerciseCreationService(ProgrammingExerciseService programmingExerciseService) {
        this.programmingExerciseService = programmingExerciseService;
    }

    public ProgrammingExercise createProgrammingExercise(ProgrammingExercise programmingExercise, boolean isImportedFromFile) throws GitAPIException, IOException {
        return programmingExerciseService.createProgrammingExerciseTransactional(programmingExercise, isImportedFromFile);
    }
}
