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
            WHERE (lecture.course.id = :#{#courseId}
                OR exercise.course.id = :#{#courseId}
                OR post.course.id = :#{#courseId})
            AND (:#{#courseWideContext} IS NULL
                OR post.courseWideContext = :#{#courseWideContext})
            AND (:#{#own} = false
                OR post.author.id = :#{#userId})
            AND (:#{#reactedOrReplied} = false
                OR answer.author.id = :#{#userId}
                OR reaction.user.id = :#{#userId})
            AND (:#{#unresolved} = false
                OR ((post.courseWideContext IS NULL OR post.courseWideContext <> 'ANNOUNCEMENT')
                    AND NOT EXISTS (SELECT answerPost FROM post.answers answerPost
                        WHERE answerPost.resolvesPost = true
                        AND answerPost.post.id = post.id)))
            """)
    List<Post> findPostsForCourse(@Param("courseId") Long courseId, @Param("courseWideContext") CourseWideContext courseWideContext, @Param("unresolved") boolean unresolved,
            @Param("own") boolean own, @Param("reactedOrReplied") boolean reactedOrReplied, @Param("userId") Long userId);

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
            WHERE post.lecture.id = :#{#lectureId}
            AND (:#{#own} = false
                OR post.author.id = :#{#userId})
            AND (:#{#reactedOrReplied} = false
                OR answer.author.id = :#{#userId}
                OR reaction.user.id = :#{#userId})
            AND (:#{#unresolved} = false
                 OR ((post.courseWideContext IS NULL OR post.courseWideContext <> 'ANNOUNCEMENT')
                    AND NOT EXISTS (SELECT answerPost FROM post.answers answerPost
                        WHERE answerPost.resolvesPost = true
                        AND answerPost.post.id = post.id)))
            """)
    List<Post> findPostsByLectureId(@Param("lectureId") Long lectureId, @Param("unresolved") boolean unresolved, @Param("own") boolean own,
            @Param("reactedOrReplied") boolean reactedOrReplied, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            LEFT JOIN post.answers answer LEFT JOIN post.reactions reaction
            WHERE post.exercise.id = :#{#exerciseId}
            AND (:#{#own} = false
                OR post.author.id = :#{#userId})
            AND (:#{#reactedOrReplied} = false
                OR answer.author.id = :#{#userId}
                OR reaction.user.id = :#{#userId})
            AND (:#{#unresolved} = false
                 OR ((post.courseWideContext IS NULL OR post.courseWideContext <> 'ANNOUNCEMENT')
                    AND NOT EXISTS (SELECT answerPost FROM post.answers answerPost
                        WHERE answerPost.resolvesPost = true
                        AND answerPost.post.id = post.id)))
            """)
    List<Post> findPostsByExerciseId(@Param("exerciseId") Long exerciseId, @Param("unresolved") boolean unresolved, @Param("own") boolean own,
            @Param("reactedOrReplied") boolean reactedOrReplied, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            LEFT JOIN post.answers answer LEFT JOIN post.reactions reaction
            WHERE post.plagiarismCase.id = :#{#plagiarismCaseId}
            """)
    List<Post> findPostsByPlagiarismCaseId(@Param("plagiarismCaseId") Long plagiarismCaseId);

    default Post findByIdElseThrow(Long postId) throws EntityNotFoundException {
        return findById(postId).orElseThrow(() -> new EntityNotFoundException("Post", postId));
    }
}
