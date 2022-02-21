package de.tum.in.www1.artemis.repository.hestia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffReport;

/**
 * Spring Data JPA repository for the ProgrammingExerciseGitDiffReport entity.
 */
@Repository
public interface ProgrammingExerciseGitDiffReportRepository extends JpaRepository<ProgrammingExerciseGitDiffReport, Long> {

    ProgrammingExerciseGitDiffReport findByProgrammingExerciseId(Long exerciseId);
}
