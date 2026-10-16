package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisCategory;

/**
 * Spring Data repository for the StaticCodeAnalysisCategory entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface StaticCodeAnalysisCategoryRepository extends ArtemisJpaRepository<StaticCodeAnalysisCategory, Long> {

    Set<StaticCodeAnalysisCategory> findByExerciseId(long exerciseId);

    /**
     * Inserts all default categories in one statement. IDENTITY generation prevents Hibernate from batching entity
     * inserts, and callers do not need the generated ids. JSON_TABLE is supported by PostgreSQL 17+ and MySQL 8+.
     *
     * @param exerciseId the exercise whose categories are created
     * @param categories the JSON array of category settings (name, penalty, maxPenalty and state)
     */
    @Modifying
    @Transactional // ok because this inserts the categories atomically
    @Query(value = """
            INSERT INTO static_code_analysis_category (exercise_id, name, penalty, max_penalty, state)
            SELECT :exerciseId, defaults.name, defaults.penalty, defaults.max_penalty, defaults.state
            FROM JSON_TABLE(CAST(:categories AS JSON), '$[*]' COLUMNS (
                name VARCHAR(255) PATH '$.name' ERROR ON ERROR,
                penalty DOUBLE PRECISION PATH '$.penalty' ERROR ON ERROR,
                max_penalty DOUBLE PRECISION PATH '$.maxPenalty' ERROR ON ERROR,
                state VARCHAR(255) PATH '$.state' ERROR ON ERROR
            )) AS defaults
            """, nativeQuery = true)
    void insertDefaultCategories(@Param("exerciseId") long exerciseId, @Param("categories") String categories);
}
