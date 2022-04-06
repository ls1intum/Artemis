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

    @Query("""
            SELECT DISTINCT post FROM Post post
            LEFT JOIN post.lecture lecture LEFT JOIN post.exercise exercise
            LEFT JOIN post.answers answer LEFT JOIN post.reactions reaction
            WHERE post.conversation IS NULL
                AND (lecture.course.id = :#{#courseId}
                OR exercise.course.id = :#{#courseId}
                OR post.course.id = :#{#courseId})
            AND (:#{#courseWideContext} IS NULL
                OR post.courseWideContext = :#{#courseWideContext})
            AND (:#{#own} IS NULL
                OR post.author.id = :#{#userId})
            AND (:#{#reactedOrReplied} IS NULL
                OR answer.author.id = :#{#userId}
                OR reaction.user.id = :#{#userId})
            AND (:#{#unresolved} IS NULL
                OR NOT EXISTS (SELECT answerPost FROM AnswerPost answerPost
                    WHERE answerPost.resolvesPost = true
                    AND answerPost.post.id = post.id))
            """)
    List<Post> findPostsForCourse(@Param("courseId") Long courseId, @Param("courseWideContext") CourseWideContext courseWideContext, @Param("unresolved") Boolean unresolved,
            @Param("own") Boolean own, @Param("reactedOrReplied") Boolean reactedOrReplied, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT tag FROM Post post
            LEFT JOIN post.tags tag LEFT JOIN post.lecture lecture LEFT JOIN post.exercise exercise
            WHERE (lecture.course.id = :#{#courseId}
            OR exercise.course.id = :#{#courseId}
            OR post.course.id = :#{#courseId})
            """)
    List<String> findPostTagsForCourse(@Param("courseId") Long courseId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            LEFT JOIN post.answers answer LEFT JOIN post.reactions reaction
            WHERE post.conversation IS NULL
            AND post.lecture.id = :#{#lectureId}
            AND (:#{#own} IS NULL
                OR post.author.id = :#{#userId})
            AND (:#{#reactedOrReplied} IS NULL
                OR answer.author.id = :#{#userId}
                OR reaction.user.id = :#{#userId})
            AND (:#{#unresolved} IS NULL
                OR NOT EXISTS (SELECT answerPost FROM AnswerPost answerPost
                    WHERE answerPost.resolvesPost = true
                    AND answerPost.post.id = post.id))
                """)
    List<Post> findPostsByLectureId(@Param("lectureId") Long lectureId, @Param("unresolved") Boolean unresolved, @Param("own") Boolean own,
            @Param("reactedOrReplied") Boolean reactedOrReplied, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            LEFT JOIN post.answers answer LEFT JOIN post.reactions reaction
            WHERE post.conversation IS NULL
            AND post.exercise.id = :#{#exerciseId}
            AND (:#{#own} IS NULL
                OR post.author.id = :#{#userId})
            AND (:#{#reactedOrReplied} IS NULL
                OR answer.author.id = :#{#userId}
                OR reaction.user.id = :#{#userId})
            AND (:#{#unresolved} IS NULL
                OR NOT EXISTS (SELECT answerPost FROM AnswerPost answerPost
                    WHERE answerPost.resolvesPost = true
                    AND answerPost.post.id = post.id))
            """)
    List<Post> findPostsByExerciseId(@Param("exerciseId") Long exerciseId, @Param("unresolved") Boolean unresolved, @Param("own") Boolean own,
            @Param("reactedOrReplied") Boolean reactedOrReplied, @Param("userId") Long userId);

    @Query("""
             SELECT DISTINCT post FROM Post post
             LEFT JOIN post.conversation conversation
             WHERE conversation.id = :#{#sessionId}
            """)
    List<Post> findPostsBySessionId(@Param("sessionId") Long sessionId);

    default Post findByIdElseThrow(Long postId) throws EntityNotFoundException {
        return findById(postId).orElseThrow(() -> new EntityNotFoundException("Post", postId));
    }
}
