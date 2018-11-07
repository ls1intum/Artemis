package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.ShortAnswerMapping;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;


/**
 * Spring Data  repository for the ShortAnswerMapping entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ShortAnswerMappingRepository extends JpaRepository<ShortAnswerMapping, Long> {

}
