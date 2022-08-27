package de.tum.in.www1.artemis.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TutorialGroupSession;

@Repository
public interface TutorialGroupSessionRepository extends JpaRepository<TutorialGroupSession, Long> {

    @Query("""
            SELECT tutorialGroupSession
            FROM TutorialGroupSession tutorialGroupSession
            WHERE tutorialGroupSession.tutorialGroup.id = :#{#tutorialGroupId}""")
    Set<TutorialGroupSession> findAllByTutorialGroupId(@Param("tutorialGroupId") Long tutorialGroupId);

}
