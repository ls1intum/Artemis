package de.tum.cit.aet.artemis.proof.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.proof.domain.DerivationStep;

@Repository
public interface DerivationStepRepository extends JpaRepository<DerivationStep, Long> {

    List<DerivationStep> findBySubmissionIdOrderByStepIndexAsc(Long submissionId);

    void deleteBySubmissionId(Long submissionId);
}
