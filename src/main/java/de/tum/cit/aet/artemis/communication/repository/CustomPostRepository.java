package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.Post;

@Profile(PROFILE_CORE)
@Repository
public interface CustomPostRepository {

    Page<Long> findPostIdsWithSpecification(Specification<Post> specification, Pageable pageable);
}
