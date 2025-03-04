package de.tum.cit.aet.artemis.atlas.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATLAS;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_ATLAS)
@Repository
public interface CompetencyLectureUnitLinkRepository extends ArtemisJpaRepository<CompetencyLectureUnitLink, Long> {

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM CompetencyLectureUnitLink clul
            WHERE clul.lectureUnit.lecture.id = :lectureId
            """)
    void deleteAllByLectureId(@Param("lectureId") long lectureId);
}
