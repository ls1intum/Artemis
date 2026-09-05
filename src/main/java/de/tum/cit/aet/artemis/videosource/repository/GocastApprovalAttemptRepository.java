package de.tum.cit.aet.artemis.videosource.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.videosource.domain.GocastApprovalAttempt;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface GocastApprovalAttemptRepository extends ArtemisJpaRepository<GocastApprovalAttempt, Long> {

    Optional<GocastApprovalAttempt> findByCourseId(long courseId);

    Optional<GocastApprovalAttempt> findByStateHash(String stateHash);
}
