package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    @Query("SELECT r FROM Reaction r WHERE r.post.id IN :postIds")
    List<Reaction> findByPostIds(@Param("postIds") List<Long> postIds);
}
