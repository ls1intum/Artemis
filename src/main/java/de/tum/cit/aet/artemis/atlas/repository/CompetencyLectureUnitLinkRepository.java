package de.tum.cit.aet.artemis.atlas.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyLectureUnitIdDTO;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Conditional(AtlasEnabled.class)
@Lazy
@Repository
public interface CompetencyLectureUnitLinkRepository extends ArtemisJpaRepository<CompetencyLectureUnitLink, Long> {

    @Query("""
            SELECT clul.id.lectureUnitId
            FROM CompetencyLectureUnitLink clul
            WHERE clul.competency.id IN :competencyIds
            """)
    Set<Long> findLectureUnitIdsByCompetencyIds(@Param("competencyIds") Set<Long> competencyIds);

    /**
     * Finds the links of the given competencies as competency and lecture unit id pairs.
     *
     * @param competencyIds the competencies
     * @return one pair per link
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.atlas.dto.CompetencyLectureUnitIdDTO(clul.id.competencyId, clul.id.lectureUnitId)
            FROM CompetencyLectureUnitLink clul
            WHERE clul.id.competencyId IN :competencyIds
            """)
    Set<CompetencyLectureUnitIdDTO> findLinkIdsByCompetencyIds(@Param("competencyIds") Set<Long> competencyIds);

    Optional<CompetencyLectureUnitLink> findByLectureUnitIdAndCompetencyId(long lectureUnitId, long competencyId);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM CompetencyLectureUnitLink clul
            WHERE clul.lectureUnit.lecture.id = :lectureId
            """)
    void deleteAllByLectureId(@Param("lectureId") long lectureId);
}
