package de.tum.in.www1.artemis.service.programming;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseSolutionEntryRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ProgrammingExerciseSolutionService {

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseSolutionEntryRepository programmingExerciseSolutionEntryRepository;

    public ProgrammingExerciseSolutionService(ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseSolutionEntryRepository programmingExerciseSolutionEntryRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseSolutionEntryRepository = programmingExerciseSolutionEntryRepository;
    }

    /**
     * Create a programmingExerciseSolutionEntry
     * @param programmingExerciseSolutionEntry the entry to be created
     * @return created programmingExerciseSolutionEntry
     */
    public ProgrammingExerciseSolutionEntry createProgrammingExerciseSolutionEntry(ProgrammingExerciseSolutionEntry programmingExerciseSolutionEntry) {
        if (programmingExerciseSolutionEntry.getCodeHint() == null || programmingExerciseSolutionEntry.getCodeHint().getExercise() == null) {
            throw new BadRequestAlertException("A programming exercise solution entry can only be created if the exercise is not null", "Programming ExerciseSolutionEntry",
                    "idnull");
        }
        programmingExerciseRepository.findByIdElseThrow(programmingExerciseSolutionEntry.getCodeHint().getExercise().getId());

        return programmingExerciseSolutionEntryRepository.save(programmingExerciseSolutionEntry);
    }

    /**
     * Delete a programmingExerciseSolutionEntry by id
     * @param programmingExerciseSolutionEntryId the programmingExerciseSolutionEntry id
     */
    public void deleteProgrammingExerciseSolutionEntry(Long programmingExerciseSolutionEntryId) {
        var programmingExerciseSolutionEntry = programmingExerciseSolutionEntryRepository.findById(programmingExerciseSolutionEntryId)
                .orElseThrow(() -> new EntityNotFoundException("Programming Exercise Solution Entry", programmingExerciseSolutionEntryId));

        programmingExerciseSolutionEntryRepository.delete(programmingExerciseSolutionEntry);
    }

    /**
     * Update a programmingExerciseSolutionEntry by id
     * @param programmingExerciseSolutionEntry the updated programmingExerciseSolutionEntry
     * @param programmingExerciseSolutionEntryId the programmingExerciseSolutionEntry id
     * @return
     */
    public ProgrammingExerciseSolutionEntry updateProgrammingExerciseSolutionEntry(ProgrammingExerciseSolutionEntry programmingExerciseSolutionEntry,
            Long programmingExerciseSolutionEntryId) {
        if (programmingExerciseSolutionEntry.getId() == null || !programmingExerciseSolutionEntryId.equals(programmingExerciseSolutionEntry.getId())
                || programmingExerciseSolutionEntry.getCodeHint() == null || programmingExerciseSolutionEntry.getCodeHint().getExercise() == null) {
            throw new BadRequestAlertException("A programming exercise solution entry can only be changed if it has an ID, the code hint and it's exercise is not null",
                    "Programming Exercise Solution Entry", "idnull");
        }

        return programmingExerciseSolutionEntryRepository.save(programmingExerciseSolutionEntry);
    }
}
