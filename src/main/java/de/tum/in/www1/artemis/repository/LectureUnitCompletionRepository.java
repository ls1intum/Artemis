package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.lecture.LectureUnitCompletion;

@Repository
public interface LectureUnitCompletionRepository extends JpaRepository<LectureUnitCompletion, Long> {

    @Query("""
            SELECT lectureUnitCompletion
            FROM LectureUnitCompletion lectureUnitCompletion
            LEFT JOIN FETCH lectureUnitCompletion.user
            LEFT JOIN FETCH lectureUnitCompletion.lectureUnit
            WHERE lectureUnitCompletion.lectureUnit.id = :#{#lectureUnitId}
            AND lectureUnitCompletion.user.id = :#{#userId}
            """)
    Optional<LectureUnitCompletion> findByLectureUnitIdAndUserId(@Param("lectureUnitId") Long lectureUnitId, @Param("userId") Long userId);

}
