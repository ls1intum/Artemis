package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.lecture.LectureUnit;
import de.tum.cit.aet.artemis.domain.lecture.LectureUnitCompletion;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
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
            WHERE lectureUnitCompletion.lectureUnit IN :lectureUnits
                AND lectureUnitCompletion.user.id = :userId
            """)
    Set<LectureUnitCompletion> findByLectureUnitsAndUserId(@Param("lectureUnits") Collection<? extends LectureUnit> lectureUnits, @Param("userId") Long userId);

    @Query("""
            SELECT COUNT(lectureUnitCompletion)
            FROM LectureUnitCompletion lectureUnitCompletion
            WHERE lectureUnitCompletion.lectureUnit.id IN :lectureUnitIds
                AND lectureUnitCompletion.user.id = :userId
            """)
    int countByLectureUnitIdsAndUserId(@Param("lectureUnitIds") Collection<Long> lectureUnitIds, @Param("userId") Long userId);

    @Query("""
            SELECT user
            FROM LectureUnitCompletion lectureUnitCompletion
                LEFT JOIN lectureUnitCompletion.user user
            WHERE lectureUnitCompletion.lectureUnit = :lectureUnit
            """)
    Set<User> findCompletedUsersForLectureUnit(@Param("lectureUnit") LectureUnit lectureUnit);
}
