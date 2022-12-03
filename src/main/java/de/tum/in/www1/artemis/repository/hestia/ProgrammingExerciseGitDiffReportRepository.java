package de.tum.in.www1.artemis.repository.hestia;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffReport;

/**
 * Spring Data JPA repository for the ProgrammingExerciseGitDiffReport entity.
 */
@Repository
public interface ProgrammingExerciseGitDiffReportRepository extends JpaRepository<ProgrammingExerciseGitDiffReport, Long> {

    /**
     * Avoid using this method. Use ProgrammingExerciseGitDiffReportService::getReportOfExercise instead
     *
     * @param exerciseId The id of the programming exercise
     * @return A list of all git-diff reports that belong to the exercise
     */
    List<ProgrammingExerciseGitDiffReport> findByProgrammingExerciseId(Long exerciseId);

    @Transactional // ok because of delete
    @Modifying
    void deleteByProgrammingExerciseId(Long exerciseId);
}
