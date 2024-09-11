package de.tum.cit.aet.artemis.repository.hestia;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.domain.hestia.ProgrammingExerciseGitDiffReport;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the ProgrammingExerciseGitDiffReport entity.
 */
@Repository
@Profile(PROFILE_CORE)
public interface ProgrammingExerciseGitDiffReportRepository extends ArtemisJpaRepository<ProgrammingExerciseGitDiffReport, Long> {

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
