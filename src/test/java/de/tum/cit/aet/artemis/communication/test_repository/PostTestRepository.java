package de.tum.cit.aet.artemis.communication.test_repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;

@Repository
public interface PostTestRepository extends PostRepository {

    /**
     * find all posts of a user in a course
     * currently only used for testing
     *
     * @param authorId id of the user
     * @param courseId id of the course
     * @return a list of posts
     */
    @Query("""
            SELECT p
            FROM Post p
            WHERE p.author.id =:authorId
                AND p.conversation.course.id = :courseId
            """)
    List<Post> findPostsByAuthorIdAndCourseId(@Param("authorId") long authorId, @Param("courseId") long courseId);
}
