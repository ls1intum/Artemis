package de.tum.cit.aet.artemis.assessment.test_repository;

import java.time.Instant;
import java.util.Collection;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.repository.StudentScoreRepository;

/** Provides deterministic score timestamps without changing the shared auditing clock. */
@Lazy
@Primary
@Repository
public interface StudentScoreTestRepository extends StudentScoreRepository {

    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE StudentScore score SET score.lastModifiedDate = :modifiedAt
            WHERE score.user.id = :userId AND score.exercise.id IN :exerciseIds
            """)
    int setLastModifiedDate(@Param("userId") long userId, @Param("exerciseIds") Collection<Long> exerciseIds, @Param("modifiedAt") Instant modifiedAt);
}
