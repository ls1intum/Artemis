package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.communication.domain.Reaction;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the Reaction entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ReactionRepository extends ArtemisJpaRepository<Reaction, Long> {

    List<Reaction> findReactionsByUserId(long userId);

    /**
     * Deletes all reactions on posts associated with the given course ID.
     *
     * @param courseId ID of the course
     */
    @Transactional // ok because of delete
    @Modifying
    @Query("DELETE FROM Reaction r WHERE r.post.conversation.course.id = :courseId")
    void deleteAllByPostCourseId(@Param("courseId") long courseId);

    /**
     * Deletes all reactions on answer posts associated with the given course ID.
     *
     * @param courseId ID of the course
     */
    @Transactional // ok because of delete
    @Modifying
    @Query("DELETE FROM Reaction r WHERE r.answerPost.post.conversation.course.id = :courseId")
    void deleteAllByAnswerPostCourseId(@Param("courseId") long courseId);
}
