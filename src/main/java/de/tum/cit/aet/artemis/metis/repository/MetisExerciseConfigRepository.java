package de.tum.cit.aet.artemis.metis.repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
 * row in place with {@link #updateSettings}, never by inserting or deleting one, and switching Metis on or off only changes its enabled flag.
 * <p>
 * A request that changes the settings, or the windows of the tutorial groups, which reference the row, adds a missing row with {@link #ensureExistsFor(long)}
 * first. That repair works like the one of the exercise module, but with a statement of this repository, since a module never uses the repositories of another.
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
     * Adds the configuration row of a programming exercise that has none, which is what a creation that failed between storing the exercise and inserting the
     * row leaves behind; the next update of the exercise would add it as well. The row gets the defaults of its columns: switched off, without a window and
     * without a hint.
     * <p>
     * It takes no lock and needs no transaction: a missing row is found by a plain read and added by a plain insert. If a parallel request adds the same row
     * first, the primary key refuses the second insert and the read that follows finds the row, which is all this method has to achieve; any other violation,
     * such as an exercise that no longer exists, is thrown. A row that exists is never replaced, so the settings it holds survive any number of calls.
     *
     * @param exerciseId the programming exercise; only a programming exercise owns a configuration, which the caller makes sure of
     */
    default void ensureExistsFor(long exerciseId) {
        if (existsById(exerciseId)) {
            return;
        }
        try {
            insertDefaults(exerciseId);
        }
        catch (DataIntegrityViolationException e) {
            // a parallel request adding the same row first is what was wanted; any other violation is a real failure
            if (!existsById(exerciseId)) {
                throw e;
            }
        }
    }

    /**
     * Creates the configuration of a programming exercise that has none. Only the key is written, so the column defaults of the changelog supply every setting,
     * exactly as for the row the exercise module inserts when it stores the exercise. A plain insert: {@link #ensureExistsFor(long)} reads first, so no
     * statement ever guards the insert with a subquery on the table, which would lock an index range.
     *
     * @param exerciseId the programming exercise, which has no configuration yet
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            INSERT INTO MetisExerciseConfig (exerciseId)
            VALUES (:exerciseId)
            """)
    void insertDefaults(@Param("exerciseId") long exerciseId);

    /**
     * Changes every setting of an exercise's configuration in place, in one statement that also checks the rule the settings have towards the checks: the
     * rating scale stays as it is once a student has started the check, because every score is read against it. The check is part of the statement, so a
     * student who starts between a read of the checks and the write cannot slip past it. A refused statement writes nothing at all, not even the other
     * settings.
     *
     * @param exerciseId              the exercise, whose row is the one that changes
     * @param enabled                 whether the check runs for the exercise
     * @param windowStart             the start of the default window, or null to start at the due date
     * @param windowEnd               the end of the default window, or null for no end
     * @param mainQuestionCount       how many main questions each student gets
     * @param maxFollowUpsPerQuestion how many follow-ups one main question may get
     * @param minSecondsPerQuestion   the lower bound of the time limit of a question
     * @param maxSecondsPerQuestion   the upper bound of the time limit of a question
     * @param answerLength            the expected length of an answer, from 1 to 5
     * @param complexity              the demand of the questions, from 1 to 5
     * @param likertScalePoints       the points of the rating scale
     * @param followUpThreshold       the score at or below which a follow-up is asked
     * @param reviewRequired          whether generated questions wait for an approval
     * @param instructorHint          the free text handed to the LLM, or null for none
     * @return 1 if the row changed; 0 if the exercise has no row, or the rating scale would change although a student has started the check
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE MetisExerciseConfig cfg
            SET cfg.enabled = :enabled,
                cfg.windowStart = :windowStart,
                cfg.windowEnd = :windowEnd,
                cfg.mainQuestionCount = :mainQuestionCount,
                cfg.maxFollowUpsPerQuestion = :maxFollowUpsPerQuestion,
                cfg.minSecondsPerQuestion = :minSecondsPerQuestion,
                cfg.maxSecondsPerQuestion = :maxSecondsPerQuestion,
                cfg.answerLength = :answerLength,
                cfg.complexity = :complexity,
                cfg.likertScalePoints = :likertScalePoints,
                cfg.followUpThreshold = :followUpThreshold,
                cfg.reviewRequired = :reviewRequired,
                cfg.instructorHint = :instructorHint
            WHERE cfg.exerciseId = :exerciseId
                AND (cfg.likertScalePoints = :likertScalePoints
                    OR NOT EXISTS (
                        SELECT c.id
                        FROM MetisCheck c
                            JOIN StudentParticipation p ON p.id = c.participationId
                        WHERE p.exercise.id = :exerciseId
                            AND c.startedAt IS NOT NULL
                    ))
            """)
    int updateSettings(@Param("exerciseId") long exerciseId, @Param("enabled") boolean enabled, @Param("windowStart") @Nullable ZonedDateTime windowStart,
            @Param("windowEnd") @Nullable ZonedDateTime windowEnd, @Param("mainQuestionCount") int mainQuestionCount, @Param("maxFollowUpsPerQuestion") int maxFollowUpsPerQuestion,
            @Param("minSecondsPerQuestion") int minSecondsPerQuestion, @Param("maxSecondsPerQuestion") int maxSecondsPerQuestion, @Param("answerLength") int answerLength,
            @Param("complexity") int complexity, @Param("likertScalePoints") int likertScalePoints, @Param("followUpThreshold") int followUpThreshold,
            @Param("reviewRequired") boolean reviewRequired, @Param("instructorHint") @Nullable String instructorHint);

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
