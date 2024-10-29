package de.tum.cit.aet.artemis.atlas.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the Competency Relation entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface CompetencyRelationRepository extends ArtemisJpaRepository<CompetencyRelation, Long> {

    @Transactional // ok because of delete
    @Modifying
    @Query("""
            DELETE FROM CompetencyRelation relation
            WHERE relation.headCompetency.id = :competencyId
                OR relation.tailCompetency.id = :competencyId
            """)
    void deleteAllByCompetencyId(@Param("competencyId") long competencyId);

    @Query("""
            SELECT relation
            FROM CompetencyRelation relation
                LEFT JOIN FETCH relation.headCompetency
                LEFT JOIN FETCH relation.tailCompetency
            WHERE relation.headCompetency.course.id = :courseId
                AND relation.tailCompetency.course.id = :courseId
            """)
    Set<CompetencyRelation> findAllWithHeadAndTailByCourseId(@Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(cr)
            FROM CompetencyRelation cr
            WHERE cr.headCompetency.course.id = :courseId
                OR cr.tailCompetency.course.id = :courseId
            """)
    long countByCourseId(@Param("courseId") long courseId);

    @Query("""
            SELECT DISTINCT relation.headCompetency.id
            FROM CompetencyRelation relation
                LEFT JOIN relation.headCompetency
                LEFT JOIN relation.tailCompetency
            WHERE relation.tailCompetency.id IN :competencyIds
                AND relation.type <> de.tum.cit.aet.artemis.atlas.domain.competency.RelationType.MATCHES
            """)
    Set<Long> getPriorCompetenciesByCompetencyIds(@Param("competencyIds") Set<Long> competencyIds);

    @Query("""
            SELECT COUNT(relation)
            FROM CompetencyRelation relation
                LEFT JOIN relation.headCompetency
                LEFT JOIN relation.tailCompetency
            WHERE relation.tailCompetency.id IN :competencyTailIds
                AND relation.headCompetency.id IN :competencyHeadIds
                AND relation.type = :type
            """)
    long countRelationsOfTypeBetweenCompetencyGroups(@Param("competencyTailIds") Set<Long> competencyTailIds, @Param("type") RelationType type,
            @Param("competencyHeadIds") Set<Long> competencyHeadIds);

    /**
     * Gets set of all competency ids that are (transitively) connected via a matching relation to the given competency id.
     * <p>
     * Important: this query is native since JPARepositories don't support recursive queries of this form.
     *
     * @param competencyId the id of the competency
     * @return set of all competency ids that are (transitively) connected via a matching relation
     */
    @Query(value = """
            WITH RECURSIVE transitive_closure(id) AS
            (
                (SELECT competency.id FROM competency WHERE competency.id = :competencyId)
                UNION
                (
                    SELECT CASE
                        WHEN relation.tail_competency_id = tc.id THEN relation.head_competency_id
                        WHEN relation.head_competency_id = tc.id THEN relation.tail_competency_id
                        END
                    FROM competency_relation AS relation
                    JOIN transitive_closure AS tc ON relation.tail_competency_id = tc.id OR relation.head_competency_id = tc.id
                    WHERE relation.type = :#{T(de.tum.cit.aet.artemis.atlas.domain.competency.RelationType).MATCHES.ordinal()}
                )
            )
            SELECT * FROM transitive_closure
            """, nativeQuery = true)
    Set<Long> getMatchingCompetenciesByCompetencyId(@Param("competencyId") long competencyId);

    Set<CompetencyRelation> findAllByHeadCompetencyIdInAndTailCompetencyIdIn(Set<Long> headCompetencyIds, Set<Long> tailCompetencyIds);

    @Transactional // ok because of delete
    @Modifying
    @Query("""
            DELETE FROM CompetencyRelation cr
            WHERE cr.headCompetency.course.id = :courseId
                OR cr.tailCompetency.course.id = :courseId
            """)
    void deleteAllByCourseId(@Param("courseId") long courseId);
}
