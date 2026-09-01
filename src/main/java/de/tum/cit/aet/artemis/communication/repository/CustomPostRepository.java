package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.LinkedHashSet;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.Post_;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CustomPostRepository extends JpaSpecificationExecutor<Post> {

    default Page<Long> findPostIdsWithSpecification(Specification<Post> specification, Pageable pageable) {
        Page<PostIdProjection> result = findBy(specification, query -> query.as(PostIdProjection.class).project(Post_.ID).page(pageable));
        var uniquePostIds = new ArrayList<>(new LinkedHashSet<>(result.map(PostIdProjection::getId).getContent()));
        return new PageImpl<>(uniquePostIds, pageable, result.getTotalElements());
    }

    interface PostIdProjection {

        Long getId();
    }
}
