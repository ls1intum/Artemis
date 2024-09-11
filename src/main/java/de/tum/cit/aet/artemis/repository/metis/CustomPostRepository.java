package de.tum.cit.aet.artemis.repository.metis;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import de.tum.cit.aet.artemis.domain.metis.Post;

public interface CustomPostRepository {

    Page<Long> findPostIdsWithSpecification(Specification<Post> specification, Pageable pageable);
}
