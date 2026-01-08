package de.tum.cit.aet.artemis.lecture.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitCompletion;

@Conditional(LectureEnabled.class)
@Lazy
@Repository
public interface LectureUnitCompletionRepository extends ArtemisJpaRepository<LectureUnitCompletion, Long> {

    @Query("""
            SELECT lectureUnitCompletion
            FROM LectureUnitCompletion lectureUnitCompletion
                LEFT JOIN FETCH lectureUnitCompletion.user
                LEFT JOIN FETCH lectureUnitCompletion.lectureUnit
            WHERE lectureUnitCompletion.lectureUnit.id = :lectureUnitId
                AND lectureUnitCompletion.user.id = :userId
            """)
    Optional<LectureUnitCompletion> findByLectureUnitIdAndUserId(@Param("lectureUnitId") Long lectureUnitId, @Param("userId") Long userId);

    @Query("""
            SELECT lectureUnitCompletion
            FROM LectureUnitCompletion lectureUnitCompletion
                LEFT JOIN FETCH lectureUnitCompletion.lectureUnit
            WHERE lectureUnitCompletion.lectureUnit IN :lectureUnits
                AND lectureUnitCompletion.user.id = :userId
            """)
    <T extends LectureUnit> Set<LectureUnitCompletion> findByLectureUnitsAndUserId(@Param("lectureUnits") Collection<T> lectureUnits, @Param("userId") Long userId);

    @Query("""
            SELECT user
            FROM LectureUnitCompletion lectureUnitCompletion
                LEFT JOIN lectureUnitCompletion.user user
            WHERE lectureUnitCompletion.lectureUnit = :lectureUnit
            """)
    Set<User> findCompletedUsersForLectureUnit(@Param("lectureUnit") LectureUnit lectureUnit);
}
