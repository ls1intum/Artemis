package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.UserStoryEffort;
import de.tum.cit.aet.artemis.programming.dto.UserStoryEffortStatusDTO;

/**
 * Spring Data JPA repository for {@link UserStoryEffort}, the effort a participant reports for a
 * {@code UserStoryExercise}.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface UserStoryEffortRepository extends ArtemisJpaRepository<UserStoryEffort, Long> {

    /**
     * The effort reported on one participation, if any.
     *
     * @param participationId the id of the participation
     * @return the reported effort, or empty if the participant has not reported any yet
     */
    Optional<UserStoryEffort> findByParticipationId(long participationId);

    /**
     * Every user story in the course that the requesting participant has started, with whatever effort they reported.
     * <p>
     * One query for the whole course overview: the effort deliberately does not hang off the serialized participation,
     * because an inverse {@code @OneToOne} cannot be proxied and so cost an extra select per participation.
     * <p>
     * Matches the participant through either an individual participation or team membership.
     *
     * @param courseId the course whose stories to report on
     * @param login    the login of the participating student
     * @return one entry per started story, with null values where nothing was reported
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.programming.dto.UserStoryEffortStatusDTO(participation.exercise.id, effort.estimatedEffort, effort.actualEffort)
            FROM StudentParticipation participation
                LEFT JOIN UserStoryEffort effort ON effort.participation.id = participation.id
                LEFT JOIN participation.team.students teamStudent
            WHERE participation.exercise.course.id = :courseId
                AND TYPE(participation.exercise) = UserStoryExercise
                AND (participation.student.login = :login OR teamStudent.login = :login)
            """)
    List<UserStoryEffortStatusDTO> findAllStartedStoriesByCourseIdAndStudentLogin(@Param("courseId") long courseId, @Param("login") String login);

    /**
     * The titles of the user story exercises in a milestone group that the participant has started but not yet estimated
     * - exactly what blocks a push (see {@code MilestoneEffortGateService}).
     * <p>
     * Only stories the participant already has a participation in are considered, so the gate can always be cleared: a
     * story that was never started has nowhere to record an estimate and is therefore not asked about. Titles rather than
     * a count, so the rejection message can name what is missing.
     *
     * Matches the participant through either an individual participation or team membership, so a team's shared
     * estimate counts for every member.
     *
     * @param milestoneGroupId the id of the milestone exercise group whose member stories to check
     * @param login            the login of the participating student
     * @return the titles of the started-but-unestimated member stories, empty when nothing blocks the push
     */
    @Query("""
            SELECT participation.exercise.title
            FROM StudentParticipation participation
                LEFT JOIN UserStoryEffort effort ON effort.participation.id = participation.id
                LEFT JOIN participation.team.students teamStudent
            WHERE participation.exercise.exerciseVariantGroup.id = :milestoneGroupId
                AND TYPE(participation.exercise) = UserStoryExercise
                AND (participation.student.login = :login OR teamStudent.login = :login)
                AND (effort IS NULL OR effort.estimatedEffort IS NULL)
            """)
    List<String> findStartedStoryTitlesWithoutEstimateByGroupIdAndStudentLogin(@Param("milestoneGroupId") long milestoneGroupId, @Param("login") String login);
}
