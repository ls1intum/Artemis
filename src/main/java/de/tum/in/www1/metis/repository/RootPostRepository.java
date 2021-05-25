package de.tum.in.www1.metis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.metis.domain.RootPost;

/**
 * Spring Data repository for the rootPost entity.
 */
@Repository
public interface RootPostRepository extends JpaRepository<RootPost, Long> {

}
