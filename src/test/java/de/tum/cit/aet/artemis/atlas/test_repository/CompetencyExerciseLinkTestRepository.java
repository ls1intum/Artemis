package de.tum.cit.aet.artemis.atlas.test_repository;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyExerciseLinkRepository;

@Lazy
@Repository
@Primary
public interface CompetencyExerciseLinkTestRepository extends CompetencyExerciseLinkRepository {

    @Modifying
    @Transactional // ok because of delete
    void deleteAllByExerciseId(long exerciseId);

    List<CompetencyExerciseLink> findAllByCompetencyId(long competencyId);
}
