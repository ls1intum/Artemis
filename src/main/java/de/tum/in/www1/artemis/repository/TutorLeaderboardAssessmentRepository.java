package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardAssessment;

@Repository
public interface TutorLeaderboardAssessmentRepository {// extends JpaRepository<TutorLeaderboardAssessment, Long> {
    // TODO: try to leave out extends completely

    @Query("""
            SELECT
            new de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardAssessment(
                null,
                result.assessor,
                count(*),
                sum(exercise.max_points),
                course.id
                )
            FROM
                Course course left join course.exercises exercise left join exercise.studentParticipations participation left join participation.results result
            WHERE
                course.teachingAssistantGroupName member of result.assessor.groups
                and result.completionDate is not null
                and course.id = :#{#courseId}
                and exercise.discriminator in ('M', 'T', 'F', 'P')
            GROUP BY result.assessor.id, course.id
            """)
    List<TutorLeaderboardAssessment> findTutorLeaderboardAssessmentByCourseId(@Param("courseId") long courseId);

    @Query("""
            SELECT
            new de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardAssessment(
                exercise.id,
                assessor,
                count(*),
                sum(exercise.max_points),
                null
                )
            FROM
                Exercise exercise left join exercise.studentParticipations participation left join participation.results result
            WHERE
                :#{#groupName} member of result.assessor.groups
                and result.completionDate is not null
                and exercise.id = :#{#exerciseId}
                and exercise.discriminator in ('M', 'T', 'F', 'P')
            GROUP BY result.assessor.id, exercise.id
            """)
    List<TutorLeaderboardAssessment> findTutorLeaderboardAssessmentByExerciseId(@Param("groupName") String groupName, @Param("exerciseId") long exerciseId);

    // Alternative which might be faster, in particular for complaints in the other repositories

    @Query("""
            SELECT
            new de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardAssessment(
                null,
                result.assessor,
                count(*),
                sum(result.participation.exercise.max_points),
                result.participation.exercise.course.id
                )
            FROM
                Result result
            WHERE
                result.participation.exercise.course.teachingAssistantGroupName member of result.assessor.groups
                and result.completionDate is not null
                and result.participation.exercise.course.id = :#{#courseId}
                and result.participation.exercise.discriminator in ('M', 'T', 'F', 'P')
            GROUP BY result.assessor.id, result.participation.exercise.course.id
            """)
    List<TutorLeaderboardAssessment> findTutorLeaderboardAssessmentByCourseIdAlternative(@Param("courseId") long courseId);

    // Alternative which might be faster, in particular for complaints in the other repositories

    @Query("""
            SELECT
            new de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardAssessment(
                result.participation.exercise.id,
                assessor,
                count(*),
                sum(result.participation.exercise.max_points),
                null
                )
            FROM
                Result result
            WHERE
                :#{#groupName} member of result.assessor.groups
                and result.completionDate is not null
                and result.participation.exercise.id = :#{#exerciseId}
                and result.participation.exercise.discriminator in ('M', 'T', 'F', 'P')
            GROUP BY result.assessor.id, result.participation.exercise.id
            """)
    List<TutorLeaderboardAssessment> findTutorLeaderboardAssessmentByExerciseIdAlternative(@Param("groupName") String groupName, @Param("exerciseId") long exerciseId);
}
