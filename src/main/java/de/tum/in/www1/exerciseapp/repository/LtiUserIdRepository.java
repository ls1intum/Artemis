package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.LtiUserId;

import de.tum.in.www1.exerciseapp.domain.User;
import org.springframework.data.jpa.repository.*;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for the LtiUserId entity.
 */
@SuppressWarnings("unused")
public interface LtiUserIdRepository extends JpaRepository<LtiUserId,Long> {

    Optional<LtiUserId> findByUser(User user);

    Optional<LtiUserId> findByLtiUserId(String ltiUserId);

}
