package de.tum.in.www1.artemis.repository.hestia;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffReport;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the ProgrammingExerciseGitDiffReport entity.
 */
@Repository
public interface ProgrammingExerciseGitDiffReportRepository extends JpaRepository<ProgrammingExerciseGitDiffReport, Long> {

    ProgrammingExerciseGitDiffReport findByProgrammingExerciseId(Long exerciseId);

    @NotNull
    default ProgrammingExerciseGitDiffReport findByIdElseThrow(Long gitDiffId) throws EntityNotFoundException {
        return findById(gitDiffId).orElseThrow(() -> new EntityNotFoundException("ProgrammingExerciseGitDiff", gitDiffId));
    }
}
