package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.domain.TextBlock;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the TextBlock entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface TextBlockRepository extends ArtemisJpaRepository<TextBlock, String> {

    Set<TextBlock> findAllBySubmissionId(Long id);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllBySubmission_Id(Long submissionId);
}
