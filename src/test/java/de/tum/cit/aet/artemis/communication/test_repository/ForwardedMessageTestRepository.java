package de.tum.cit.aet.artemis.communication.test_repository;

import java.util.Set;

import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.ForwardedMessage;
import de.tum.cit.aet.artemis.communication.repository.ForwardedMessageRepository;

/**
 * Test repository for ForwardedMessageRepository.
 */
@Repository
@Primary
public interface ForwardedMessageTestRepository extends ForwardedMessageRepository {

    /**
     * Find all forwarded messages by destination post IDs.
     *
     * @param destinationPostIds Set of destination post IDs
     * @return Set of ForwardedMessage entities
     */
    @Override
    @Query("SELECT fm FROM ForwardedMessage fm WHERE fm.destinationPost.id IN :destinationPostIds")
    Set<ForwardedMessage> findAllByDestinationPostIds(@Param("destinationPostIds") Set<Long> destinationPostIds);

    /**
     * Find all forwarded messages by destination answer post IDs.
     *
     * @param destinationAnswerPostIds Set of destination answer post IDs
     * @return Set of ForwardedMessage entities
     */
    @Override
    @Query("SELECT fm FROM ForwardedMessage fm WHERE fm.destinationAnswerPost.id IN :destinationAnswerPostIds")
    Set<ForwardedMessage> findAllByDestinationAnswerPostIds(@Param("destinationAnswerPostIds") Set<Long> destinationAnswerPostIds);

}
