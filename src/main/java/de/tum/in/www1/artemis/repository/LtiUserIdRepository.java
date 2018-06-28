package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.LtiUserId;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the LtiUserId entity.
 */
@SuppressWarnings("unused")
@Repository
public interface LtiUserIdRepository extends JpaRepository<LtiUserId, Long> {

}
