package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Complaint entity.
 */
@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
}
