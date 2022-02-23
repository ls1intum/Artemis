package de.tum.in.www1.artemis.repository.metis;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.CourseWideContext;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the Post entity.
 */
@SuppressWarnings("unused")
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findPostsByAuthorLogin(String login);

    List<Post> findPostsByExerciseId(Long exerciseId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            WHERE post.exercise.id = :#{#exerciseId}
            AND NOT EXISTS (SELECT answerPost FROM AnswerPost answerPost
                WHERE answerPost.resolvesPost = true
                AND answerPost.post.id = post.id)""")
    List<Post> findUnresolvedPostsByExerciseId(@Param("exerciseId") Long exerciseId);

    List<Post> findPostsByLectureId(Long lectureId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            WHERE (post.lecture.id = :#{#lectureId}
            AND NOT EXISTS (SELECT answerPost FROM AnswerPost answerPost
                WHERE answerPost.resolvesPost = true
                AND answerPost.post.id = post.id))""")
    List<Post> findUnresolvedPostsByLectureId(@Param("lectureId") Long lectureId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            LEFT JOIN post.lecture lecture LEFT JOIN post.exercise exercise
            WHERE (post.courseWideContext = :#{#courseWideContext}
            AND (lecture.course.id = :#{#courseId}
            OR exercise.course.id = :#{#courseId}
            OR post.course.id = :#{#courseId} ))""")
    List<Post> findPostsForCourseWideContext(@Param("courseId") Long courseId, @Param("courseWideContext") CourseWideContext courseWideContext);

    @Query("""
            SELECT DISTINCT post FROM Post post
            LEFT JOIN post.lecture lecture LEFT JOIN post.exercise exercise
            WHERE (lecture.course.id = :#{#courseId}
            OR exercise.course.id = :#{#courseId}
            OR post.course.id = :#{#courseId} )""")
    List<Post> findPostsForCourse(@Param("courseId") Long courseId);

    @Query("""
            SELECT DISTINCT tag FROM Post post
            LEFT JOIN post.tags tag LEFT JOIN post.lecture lecture LEFT JOIN post.exercise exercise
            WHERE (lecture.course.id = :#{#courseId}
            OR exercise.course.id = :#{#courseId}
            OR post.course.id = :#{#courseId} )""")
    List<String> findPostTagsForCourse(@Param("courseId") Long courseId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            LEFT JOIN post.lecture lecture LEFT JOIN post.exercise exercise
            WHERE (post.author.id = :#{#userId}
            AND (lecture.course.id = :#{#courseId}
            OR exercise.course.id = :#{#courseId}
            OR post.course.id = :#{#courseId} ))""")
    List<Post> findOwnPostsForCourse(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            LEFT JOIN post.lecture lecture LEFT JOIN post.exercise exercise
            WHERE ((lecture.course.id = :#{#courseId}
            OR exercise.course.id = :#{#courseId}
            OR post.course.id = :#{#courseId})
            AND (NOT EXISTS (SELECT answerPost FROM AnswerPost answerPost
                WHERE answerPost.resolvesPost = true
                AND answerPost.post.id = post.id)))""")
    List<Post> findUnresolvedPostsForCourse(@Param("courseId") Long courseId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            LEFT JOIN post.lecture lecture LEFT JOIN post.exercise exercise
            WHERE ((post.author.id = :#{#userId})
            AND (lecture.course.id = :#{#courseId}
            OR exercise.course.id = :#{#courseId}
            OR post.course.id = :#{#courseId})
            AND (NOT EXISTS (SELECT answerPost FROM AnswerPost answerPost
            WHERE answerPost.resolvesPost = true
            AND answerPost.post.id = post.id)))""")
    List<Post> findOwnAndUnresolvedPostsForCourse(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            LEFT JOIN post.lecture lecture LEFT JOIN post.exercise exercise
            WHERE (lecture.course.id = :#{#courseId}
            OR exercise.course.id = :#{#courseId}
            OR post.course.id = :#{#courseId})
            AND (EXISTS (SELECT answerPost FROM AnswerPost answerPost
                WHERE answerPost.post.id = post.id
                AND answerPost.author.id = :#{#userId})
                OR EXISTS (SELECT reaction FROM Reaction reaction
                    WHERE reaction.post.id = post.id
                    AND reaction.user.id = :#{#userId}) )""")
    List<Post> findAnsweredOrReactedPostsByUserForCourse(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            LEFT JOIN post.lecture lecture LEFT JOIN post.exercise exercise
            WHERE (lecture.course.id = :#{#courseId}
            OR exercise.course.id = :#{#courseId}
            OR post.course.id = :#{#courseId})
            AND post.author.id = :#{#userId}
            AND (EXISTS (SELECT answerPost FROM AnswerPost answerPost
                WHERE answerPost.post.id = post.id
                AND answerPost.author.id = :#{#userId})
                OR (EXISTS (SELECT reaction FROM Reaction reaction
                    WHERE reaction.post.id = post.id
                    AND reaction.user.id = :#{#userId})))""")
    List<Post> findOwnAndAnsweredOrReactedPostsByUserForCourse(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            LEFT JOIN post.lecture lecture LEFT JOIN post.exercise exercise
            WHERE ((lecture.course.id = :#{#courseId}
            OR exercise.course.id = :#{#courseId}
            OR post.course.id = :#{#courseId})
            AND (NOT EXISTS (SELECT answerPost FROM AnswerPost answerPost
                WHERE answerPost.resolvesPost = true AND answerPost.post.id = post.id))
                AND (EXISTS (SELECT answerPost FROM AnswerPost answerPost
                    WHERE answerPost.post.id = post.id
                    AND answerPost.author.id = :#{#userId})
                    OR EXISTS (SELECT reaction FROM Reaction reaction
                        WHERE reaction.post.id = post.id
                        AND reaction.user.id = :#{#userId})))""")
    List<Post> findUnresolvedAndAnsweredOrReactedPostsByUserForCourse(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            LEFT JOIN post.lecture lecture LEFT JOIN post.exercise exercise
            WHERE ((lecture.course.id = :#{#courseId}
            OR exercise.course.id = :#{#courseId}
            OR post.course.id = :#{#courseId})
            AND (post.author.id = :#{#userId})
            AND (NOT EXISTS (SELECT answerPost FROM AnswerPost answerPost
                WHERE answerPost.resolvesPost = true
                AND answerPost.post.id = post.id))
                AND (EXISTS (SELECT answerPost FROM AnswerPost answerPost
                    WHERE answerPost.post.id = post.id
                    AND answerPost.author.id = :#{#userId} )
                    OR EXISTS (SELECT reaction FROM Reaction reaction
                        WHERE reaction.post.id = post.id
                        AND reaction.user.id = :#{#userId})))""")
    List<Post> findUnresolvedAndOwnAndAnsweredOrReactedPostsByUserForCourse(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            WHERE (post.author.id = :#{#userId}
            AND post.lecture.id = :#{#lectureId})""")
    List<Post> findOwnPostsByLectureId(@Param("lectureId") Long lectureId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            WHERE (post.lecture.id = :#{#lectureId})
            AND (EXISTS (SELECT answerPost FROM AnswerPost answerPost
                WHERE answerPost.post.id = post.id
                AND answerPost.author.id = :#{#userId})
                OR EXISTS (SELECT reaction FROM Reaction reaction
                    WHERE reaction.post.id = post.id
                    AND reaction.user.id = :#{#userId}))""")
    List<Post> findAnsweredOrReactedPostsByUserByLectureId(@Param("lectureId") Long lectureId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            WHERE (post.author.id = :#{#userId}
            AND post.lecture.id = :#{#lectureId}
            AND (NOT EXISTS (SELECT answerPost FROM AnswerPost answerPost
                WHERE answerPost.resolvesPost = true
                AND answerPost.post.id = post.id)))""")
    List<Post> findOwnAndUnresolvedPostsForLecture(@Param("lectureId") Long lectureId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            WHERE (post.lecture.id = :#{#lectureId}
            AND post.author.id = :#{#userId})
            AND (EXISTS (SELECT answerPost FROM AnswerPost answerPost
                WHERE answerPost.post.id = post.id
                AND answerPost.author.id = :#{#userId})
                OR EXISTS (SELECT reaction FROM Reaction reaction
                    WHERE reaction.post.id = post.id
                    AND reaction.user.id = :#{#userId}))""")
    List<Post> findOwnAndAnsweredOrReactedPostsByUserForLecture(@Param("lectureId") Long lectureId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            WHERE (post.lecture.id = :#{#lectureId}
            AND (NOT EXISTS (SELECT answerPost FROM AnswerPost answerPost
                WHERE answerPost.resolvesPost = true
                AND answerPost.post.id = post.id))
                AND (EXISTS (SELECT answerPost FROM AnswerPost answerPost
                    WHERE answerPost.post.id = post.id
                    AND answerPost.author.id = :#{#userId})
                    OR EXISTS (SELECT reaction FROM Reaction reaction
                        WHERE reaction.post.id = post.id
                        AND reaction.user.id = :#{#userId})))""")
    List<Post> findUnresolvedAndAnsweredOrReactedPostsByUserForLecture(@Param("lectureId") Long lectureId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            WHERE (post.lecture.id = :#{#lectureId}
            AND post.author.id = :#{#userId}
            AND (NOT EXISTS (SELECT answerPost FROM AnswerPost answerPost
                WHERE answerPost.resolvesPost = true
                AND answerPost.post.id = post.id))
                AND (EXISTS (SELECT answerPost FROM AnswerPost answerPost
                    WHERE answerPost.post.id = post.id
                    AND answerPost.author.id = :#{#userId})
                    OR EXISTS (SELECT reaction FROM Reaction reaction
                        WHERE reaction.post.id = post.id
                        AND reaction.user.id = :#{#userId})))""")
    List<Post> findUnresolvedAndOwnAndAnsweredOrReactedPostsByUserForLecture(@Param("lectureId") Long lectureId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            WHERE (post.author.id = :#{#userId}
            AND post.exercise.id = :#{#exerciseId})""")
    List<Post> findOwnPostsByExerciseId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            WHERE (post.exercise.id = :#{#exerciseId})
            AND (EXISTS (SELECT answerPost FROM AnswerPost answerPost
                WHERE answerPost.post.id = post.id
                AND answerPost.author.id = :#{#userId})
                OR EXISTS (SELECT reaction FROM Reaction reaction
                    WHERE reaction.post.id = post.id
                    AND reaction.user.id = :#{#userId}))""")
    List<Post> findAnsweredOrReactedPostsByUserByExerciseId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            WHERE (post.author.id = :#{#userId}
            AND post.exercise.id = :#{#exerciseId}
            AND (NOT EXISTS (SELECT answerPost FROM AnswerPost answerPost
                WHERE answerPost.resolvesPost = true
                AND answerPost.post.id = post.id)))""")
    List<Post> findOwnAndUnresolvedPostsByExerciseId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT post FROM Post post WHERE (post.exercise.id = :#{#exerciseId}
            AND post.author.id = :#{#userId})
            AND (EXISTS (SELECT answerPost FROM AnswerPost answerPost
                WHERE answerPost.post.id = post.id
                AND answerPost.author.id = :#{#userId})
                OR EXISTS (SELECT reaction FROM Reaction reaction
                    WHERE reaction.post.id = post.id
                    AND reaction.user.id = :#{#userId}))""")
    List<Post> findOwnAndAnsweredOrReactedPostsByUserByExerciseId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            WHERE (post.exercise.id = :#{#exerciseId}
            AND (NOT EXISTS (SELECT answerPost FROM AnswerPost answerPost
                WHERE answerPost.resolvesPost = true
                AND answerPost.post.id = post.id))
                AND (EXISTS (SELECT answerPost FROM AnswerPost answerPost
                    WHERE answerPost.post.id = post.id
                    AND answerPost.author.id = :#{#userId})
                    OR EXISTS (SELECT reaction FROM Reaction reaction
                        WHERE reaction.post.id = post.id
                        AND reaction.user.id = :#{#userId})))""")
    List<Post> findUnresolvedAndAnsweredOrReactedPostsByUserByExerciseId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            WHERE (post.exercise.id = :#{#exerciseId}
            AND post.author.id = :#{#userId}
            AND (NOT EXISTS (SELECT answerPost FROM AnswerPost answerPost
                WHERE answerPost.resolvesPost = true
                AND answerPost.post.id = post.id))
                AND (EXISTS (SELECT answerPost FROM AnswerPost answerPost
                    WHERE answerPost.post.id = post.id
                    AND answerPost.author.id = :#{#userId})
                    OR EXISTS (SELECT reaction FROM Reaction reaction
                    WHERE reaction.post.id = post.id
                    AND reaction.user.id = :#{#userId})))""")
    List<Post> findUnresolvedAndOwnAndAnsweredOrReactedPostsByUserByExerciseId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            LEFT JOIN post.lecture lecture LEFT JOIN post.exercise exercise
            WHERE ((lecture.course.id = :#{#courseId}
            OR exercise.course.id = :#{#courseId}
            OR post.course.id = :#{#courseId})
            AND (post.courseWideContext = :#{#courseWideContext}))""")
    List<Post> findPostsForCourseByCourseWideContext(@Param("courseId") Long courseId, @Param("courseWideContext") CourseWideContext courseWideContext);

    @Query("""
            SELECT DISTINCT post FROM Post post
            LEFT JOIN post.lecture lecture LEFT JOIN post.exercise exercise
            WHERE ((lecture.course.id = :#{#courseId}
            OR exercise.course.id = :#{#courseId}
            OR post.course.id = :#{#courseId})
            AND (post.courseWideContext = :#{#courseWideContext}
            AND (NOT EXISTS (SELECT answerPost FROM AnswerPost answerPost
                WHERE answerPost.resolvesPost = true
                AND answerPost.post.id = post.id))))""")
    List<Post> findUnresolvedPostsForCourseByCourseWideContext(@Param("courseId") Long courseId, @Param("courseWideContext") CourseWideContext courseWideContext);

    @Query("""
            SELECT DISTINCT post FROM Post post
            LEFT JOIN post.lecture lecture LEFT JOIN post.exercise exercise
            WHERE (post.author.id = :#{#userId}
            AND (lecture.course.id = :#{#courseId}
            OR exercise.course.id = :#{#courseId}
            OR post.course.id = :#{#courseId})
            AND (post.courseWideContext = :#{#courseWideContext}))""")
    List<Post> findOwnPostsForCourseByCourseWideContext(@Param("courseId") Long courseId, @Param("userId") Long userId,
            @Param("courseWideContext") CourseWideContext courseWideContext);

    @Query("""
            SELECT DISTINCT post FROM Post post
            LEFT JOIN post.lecture lecture LEFT JOIN post.exercise exercise
            WHERE (lecture.course.id = :#{#courseId}
            OR exercise.course.id = :#{#courseId}
            OR post.course.id = :#{#courseId})
            AND (post.courseWideContext = :#{#courseWideContext})
            AND (EXISTS (SELECT answerPost FROM AnswerPost answerPost
                WHERE answerPost.post.id = post.id
                AND answerPost.author.id = :#{#userId})
                OR EXISTS (SELECT reaction FROM Reaction reaction
                    WHERE reaction.post.id = post.id
                    AND reaction.user.id = :#{#userId}) )""")
    List<Post> findAnsweredOrReactedPostsByUserForCourseByCourseWideContext(@Param("courseId") Long courseId, @Param("userId") Long userId,
            @Param("courseWideContext") CourseWideContext courseWideContext);

    @Query("""
            SELECT DISTINCT post FROM Post post
            LEFT JOIN post.lecture lecture LEFT JOIN post.exercise exercise
            WHERE ( (post.author.id = :#{#userId})
            AND ( lecture.course.id = :#{#courseId}
            OR exercise.course.id = :#{#courseId}
            OR post.course.id = :#{#courseId})
            AND (post.courseWideContext = :#{#courseWideContext})
            AND (NOT EXISTS (SELECT answerPost FROM AnswerPost answerPost
                WHERE answerPost.resolvesPost = true
                AND answerPost.post.id = post.id)))""")
    List<Post> findOwnAndUnresolvedPostsForCourseByCourseWideContext(@Param("courseId") Long courseId, @Param("userId") Long userId,
            @Param("courseWideContext") CourseWideContext courseWideContext);

    @Query("""
            SELECT DISTINCT post FROM Post post
            LEFT JOIN post.lecture lecture LEFT JOIN post.exercise exercise
            WHERE (lecture.course.id = :#{#courseId}
            OR exercise.course.id = :#{#courseId}
            OR post.course.id = :#{#courseId})
            AND post.author.id = :#{#userId
            AND (post.courseWideContext = :#{#courseWideContext})
            AND (EXISTS (SELECT answerPost FROM AnswerPost answerPost
                WHERE answerPost.post.id = post.id
                AND answerPost.author.id = :#{#userId})
                OR (EXISTS (SELECT reaction FROM Reaction reaction
                    WHERE reaction.post.id = post.id
                    AND reaction.user.id = :#{#userId})))""")
    List<Post> findOwnAndAnsweredOrReactedPostsByUserForCourseByCourseWideContext(@Param("courseId") Long courseId, @Param("userId") Long userId,
            @Param("courseWideContext") CourseWideContext courseWideContext);

    @Query("""
            SELECT DISTINCT post FROM Post post
            LEFT JOIN post.lecture lecture LEFT JOIN post.exercise exercise
            WHERE (( lecture.course.id = :#{#courseId}
            OR exercise.course.id = :#{#courseId}
            OR post.course.id = :#{#courseId})
            AND (post.courseWideContext = :#{#courseWideContext})
            AND (NOT EXISTS (SELECT answerPost FROM AnswerPost answerPost
                WHERE answerPost.resolvesPost = true
                AND answerPost.post.id = post.id))
                AND (EXISTS (SELECT answerPost FROM AnswerPost answerPost
                WHERE answerPost.post.id = post.id
                AND answerPost.author.id = :#{#userId})
                OR EXISTS (SELECT reaction FROM Reaction reaction
                    WHERE reaction.post.id = post.id
                    AND reaction.user.id = :#{#userId})))""")
    List<Post> findUnresolvedAndAnsweredOrReactedPostsByUserForCourseByCourseWideContext(@Param("courseId") Long courseId, @Param("userId") Long userId,
            @Param("courseWideContext") CourseWideContext courseWideContext);

    @Query("""
            SELECT DISTINCT post FROM Post post
            LEFT JOIN post.lecture lecture LEFT JOIN post.exercise exercise
            WHERE (( lecture.course.id = :#{#courseId}
            OR exercise.course.id = :#{#courseId}
            OR post.course.id = :#{#courseId})
            AND (post.author.id = :#{#userId})
            AND (post.courseWideContext = :#{#courseWideContext})
            AND (NOT EXISTS (SELECT answerPost FROM AnswerPost answerPost
                WHERE answerPost.resolvesPost = true
                AND answerPost.post.id = post.id))
                AND (EXISTS (SELECT answerPost FROM AnswerPost answerPost
                    WHERE answerPost.post.id = post.id
                    AND answerPost.author.id = :#{#userId})
                    OR EXISTS (SELECT reaction FROM Reaction reaction
                        WHERE reaction.post.id = post.id
                        AND reaction.user.id = :#{#userId})))""")
    List<Post> findUnresolvedAndOwnAndAnsweredOrReactedPostsByUserForCourseByCourseWideContext(@Param("courseId") Long courseId, @Param("userId") Long userId,
            @Param("courseWideContext") CourseWideContext courseWideContext);

    default Post findByIdElseThrow(Long postId) throws EntityNotFoundException {
        return findById(postId).orElseThrow(() -> new EntityNotFoundException("Post", postId));
    }
}
