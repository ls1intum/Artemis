package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.ForwardedMessage;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Repository
public interface ForwardedMessageRepository extends ArtemisJpaRepository<ForwardedMessage, Long> {

    @Query("SELECT fm FROM ForwardedMessage fm WHERE fm.destinationPost.id = :destinationPostId")
    Set<ForwardedMessage> findAllByDestinationPostId(@Param("destinationPostId") Long destinationPostId);

    @Query("SELECT fm FROM ForwardedMessage fm WHERE fm.destinationAnswerPost.id = :destinationAnswerPostId")
    Set<ForwardedMessage> findAllByDestinationAnswerPostId(@Param("destinationAnswerPostId") Long destinationAnswerPostId);

}
