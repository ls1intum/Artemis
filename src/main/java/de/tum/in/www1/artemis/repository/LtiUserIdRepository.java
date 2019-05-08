package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.LtiUserId;
import de.tum.in.www1.artemis.domain.User;

/**
 * Spring Data JPA repository for the LtiUserId entity.
 */
@SuppressWarnings("unused")
@Repository
public interface LtiUserIdRepository extends JpaRepository<LtiUserId, Long> {

    Optional<LtiUserId> findByUser(User user);

    Optional<LtiUserId> findByLtiUserId(String ltiUserId);
}
