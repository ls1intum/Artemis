package de.tum.cit.aet.artemis.core.test_repository;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Repository
public interface ComplaintResponseTestRepository extends ArtemisJpaRepository<ComplaintResponse, Long> {

    Optional<ComplaintResponse> findByComplaintId(long complaintId);
}
