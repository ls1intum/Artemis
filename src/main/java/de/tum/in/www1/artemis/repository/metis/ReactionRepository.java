package de.tum.in.www1.artemis.repository.metis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.Reaction;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the Reaction entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    default Reaction findByIdElseThrow(Long reactionId) throws EntityNotFoundException {
        return findById(reactionId).orElseThrow(() -> new EntityNotFoundException("Reaction", reactionId));
    }
}
