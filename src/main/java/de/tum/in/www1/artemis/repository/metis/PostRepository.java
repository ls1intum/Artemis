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

    List<Post> findPostsByAuthorLogin(String login);

    List<Post> findPostsByExerciseId(Long exerciseId);

    List<Post> findPostsByLectureId(Long lectureId);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where ( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId} )")
    List<Post> findPostsForCourse(@Param("courseId") Long courseId);

    @Query("select distinct tag from Post post left join post.tags tag left join post.lecture lecture left join post.exercise exercise where ( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId} )")
    List<String> findPostTagsForCourse(@Param("courseId") Long courseId);

    default Post findByIdElseThrow(Long postId) throws EntityNotFoundException {
        return findById(postId).orElseThrow(() -> new EntityNotFoundException("Post", postId));
    }
}
