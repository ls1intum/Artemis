package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
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
}
