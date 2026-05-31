package de.tum.cit.aet.artemis.math.repository;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.DerivationStep;

@Conditional(MathEnabled.class)
@Lazy
@Repository
public interface DerivationStepRepository extends JpaRepository<DerivationStep, Long> {

    List<DerivationStep> findBySubmissionIdOrderByStepIndexAsc(Long submissionId);

    void deleteBySubmissionId(Long submissionId);
}
