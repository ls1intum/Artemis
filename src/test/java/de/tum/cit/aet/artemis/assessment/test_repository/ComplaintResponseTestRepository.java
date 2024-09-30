package de.tum.cit.aet.artemis.assessment.test_repository;

import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintResponseRepository;

@Repository
@Primary
public interface ComplaintResponseTestRepository extends ComplaintResponseRepository {

    Optional<ComplaintResponse> findByComplaintId(long complaintId);
}
