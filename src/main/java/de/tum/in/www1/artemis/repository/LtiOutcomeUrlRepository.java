package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.LtiOutcomeUrl;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;
import java.util.List;

/**
 * Spring Data JPA repository for the LtiOutcomeUrl entity.
 */
@SuppressWarnings("unused")
@Repository
public interface LtiOutcomeUrlRepository extends JpaRepository<LtiOutcomeUrl, Long> {

    @Query("select lti_outcome_url from LtiOutcomeUrl lti_outcome_url where lti_outcome_url.user.login = ?#{principal.username}")
    List<LtiOutcomeUrl> findByUserIsCurrentUser();

}
