package de.tum.cit.aet.artemis.exercise.repository.review;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadGroup;

/**
 * Spring Data repository for the CommentThreadGroup entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CommentThreadGroupRepository extends ArtemisJpaRepository<CommentThreadGroup, Long> {
}
