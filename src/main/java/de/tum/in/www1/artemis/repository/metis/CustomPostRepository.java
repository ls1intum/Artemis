package de.tum.in.www1.artemis.repository.metis;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import de.tum.in.www1.artemis.domain.metis.Post;

public interface CustomPostRepository {

    Page<Long> findPostIdsWithSpecification(Specification<Post> specification, Pageable pageable);
}
