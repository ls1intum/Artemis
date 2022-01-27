package de.tum.in.www1.artemis.service.hestia;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.hestia.CodeHintRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service for managing code hints.
 */
@Service
public class CodeHintService {

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final CodeHintRepository codeHintRepository;

    public CodeHintService(ProgrammingExerciseRepository programmingExerciseRepository, CodeHintRepository codeHintRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.codeHintRepository = codeHintRepository;
    }

    /**
     * Create a codeHint
     * @param codeHint
     * @return created code hint
     */
    public CodeHint createCodeHint(CodeHint codeHint) {
        if (codeHint.getExercise() == null) {
            throw new BadRequestAlertException("A code hint can only be created if the exercise is not null", "Code Hint", "idnull");
        }
        programmingExerciseRepository.findByIdElseThrow(codeHint.getExercise().getId());

        return codeHintRepository.save(codeHint);
    }

    /**
     * Delete a codeHint by id
     * @param codeHintId the code hint id
     */
    public void deleteCodeHint(Long codeHintId) {
        var codeHint = codeHintRepository.findById(codeHintId).orElseThrow(() -> new EntityNotFoundException("Code Hint", codeHintId));

        codeHintRepository.delete(codeHint);
    }

    /**
     * Update a codeHint by id
     * @param codeHint the updated codeHint
     * @param codeHintId the code hint id
     * @return the updated codeHint
     */
    public CodeHint updateCodeHint(CodeHint codeHint, Long codeHintId) {
        if (codeHint.getId() == null || !codeHintId.equals(codeHint.getId()) || codeHint.getExercise() == null) {
            throw new BadRequestAlertException("A code hint can only be changed if it has an ID and if the exercise is not null", "Code Hint", "idnull");
        }

        return codeHintRepository.save(codeHint);
    }
}
