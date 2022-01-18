package de.tum.in.www1.artemis.repository;

import java.util.Set;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@SuppressWarnings("unused")
public interface ProgrammingExerciseSolutionEntryRepository extends JpaRepository<ProgrammingExerciseSolutionEntry, Long> {

    Set<ProgrammingExerciseSolutionEntry> findByExerciseId(Long exerciseId);

    @NotNull
    default ProgrammingExerciseSolutionEntry findByIdElseThrow(long entryId) throws EntityNotFoundException {
        return findById(entryId).orElseThrow(() -> new EntityNotFoundException("Programming Exercise Solution Entry", entryId));
    }
}
