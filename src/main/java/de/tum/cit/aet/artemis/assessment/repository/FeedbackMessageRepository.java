package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.FeedbackMessage;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the deduplicated, content-addressed {@link FeedbackMessage} entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface FeedbackMessageRepository extends ArtemisJpaRepository<FeedbackMessage, Long> {

    Optional<FeedbackMessage> findByHash(byte[] hash);
}
