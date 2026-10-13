package de.tum.cit.aet.artemis.metis.repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.domain.MetisExerciseConfig;
import de.tum.cit.aet.artemis.metis.dto.MetisActiveExerciseDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisExerciseContextDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisExerciseFactsDTO;

/**
 * Spring Data JPA repository for the {@link MetisExerciseConfig} entity. The id of the entity is the exercise id, so the inherited lookup by id is the lookup by
 * exercise. The facts about the exercise that Metis needs are read as projections here as well, so that no exercise entity is loaded for them.
 * <p>
 * Every programming exercise owns exactly one row, also while this module is disabled and this repository does not exist:
 * the row is inserted by the exercise module when the exercise is stored ({@code ExerciseDefaultConfigurationsRepository}),
 * and the next update of the exercise adds it if a creation that failed half-way left it out. Settings are changed in that
 * row in place, never by inserting or deleting one, and switching Metis on or off only changes its enabled flag.
 */
@Conditional(MetisEnabled.class)
@Lazy
@Repository
public interface MetisExerciseConfigRepository extends ArtemisJpaRepository<MetisExerciseConfig, Long> {

    /**
     * Whether an exercise has an enabled check, which decides whether the exercise page shows the check tab.
     *
     * @param exerciseId the exercise
     * @return true if a configuration row exists for the exercise and is enabled
     */
    boolean existsByExerciseIdAndEnabledTrue(long exerciseId);

    /**
     * The exercises the generation poller still looks at: course programming exercises with a due date and an enabled check whose due date, latest individual
     * due date, configuration window end or latest group window end is newer than the start of the horizon. So a late extension still gets its check, and a
     * late window still gets the consent sweep, while exercises that lie further back are left to an explicit "generate now".
     *
     * @param horizonStart the start of the horizon
     * @return the active exercises with their course and due date
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.metis.dto.MetisActiveExerciseDTO(e.id, e.course.id, e.dueDate)
            FROM MetisExerciseConfig cfg
                JOIN ProgrammingExercise e ON e.id = cfg.exerciseId
            WHERE cfg.enabled = TRUE
                AND e.course IS NOT NULL
                AND e.dueDate IS NOT NULL
                AND (e.dueDate > :horizonStart
                    OR cfg.windowEnd > :horizonStart
                    OR EXISTS (
                        SELECT w.id
                        FROM MetisGroupWindow w
                        WHERE w.exerciseId = e.id
                            AND w.windowEnd > :horizonStart
                    )
                    OR EXISTS (
                        SELECT p.id
                        FROM StudentParticipation p
                        WHERE p.exercise.id = e.id
                            AND p.individualDueDate > :horizonStart
                    ))
            """)
    List<MetisActiveExerciseDTO> findActiveExercises(@Param("horizonStart") ZonedDateTime horizonStart);

    /**
     * The facts about a programming exercise that decide whether it can have a check and when: its course, its mode, its due date, its title and short name.
     *
     * @param exerciseId the exercise
     * @return the facts, or empty when there is no programming exercise with this id; the course is null for an exam exercise
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.metis.dto.MetisExerciseFactsDTO(e.id, course.id, e.mode, e.dueDate, e.title, e.shortName)
            FROM ProgrammingExercise e
                LEFT JOIN e.course course
            WHERE e.id = :exerciseId
            """)
    Optional<MetisExerciseFactsDTO> findExerciseFacts(@Param("exerciseId") long exerciseId);

    /**
     * What the generation prompt tells the LLM about a programming exercise.
     *
     * @param exerciseId the exercise
     * @return its title, problem statement and programming language, or empty when there is no programming exercise with this id
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.metis.dto.MetisExerciseContextDTO(e.id, e.title, e.problemStatement, e.programmingLanguage)
            FROM ProgrammingExercise e
            WHERE e.id = :exerciseId
            """)
    Optional<MetisExerciseContextDTO> findExerciseContext(@Param("exerciseId") long exerciseId);
}
