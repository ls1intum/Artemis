package de.tum.in.www1.artemis.repository.metis;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the Post entity.
 */
@SuppressWarnings("unused")
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("select post from Post post where post.author.login = :#{#login}")
    List<Post> findByAuthorWithLogin(@Param("login") String login);

    @Query("select post from Post post where post.exercise.id = :#{#exerciseId}")
    List<Post> findPostsForExercise(@Param("exerciseId") Long exerciseId);

    @Query("select post from Post post where post.lecture.id = :#{#lectureId}")
    List<Post> findPostsForLecture(@Param("lectureId") Long lectureId);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where ( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} )")
    List<Post> findPostsForCourse(@Param("courseId") Long courseId);

    default Post findByIdElseThrow(Long postId) throws EntityNotFoundException {
        return findById(postId).orElseThrow(() -> new EntityNotFoundException("Post", postId));
    }
}
