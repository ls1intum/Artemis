package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.*;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.TextBlock;

/**
 * Spring Data repository for the TextBlock entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface TextBlockRepository extends JpaRepository<TextBlock, String> {

    Set<TextBlock> findAllBySubmissionId(Long id);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllBySubmission_Id(Long submissionId);
}
