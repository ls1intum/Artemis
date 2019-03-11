package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for the Complaint entity.
 */
@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    Optional<Complaint> findByResult_Id(Long resultId);
}
