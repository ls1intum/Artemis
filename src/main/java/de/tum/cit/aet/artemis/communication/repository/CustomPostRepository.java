package de.tum.cit.aet.artemis.communication.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import de.tum.cit.aet.artemis.communication.domain.Post;

public interface CustomPostRepository {

    Page<Long> findPostIdsWithSpecification(Specification<Post> specification, Pageable pageable);
}
