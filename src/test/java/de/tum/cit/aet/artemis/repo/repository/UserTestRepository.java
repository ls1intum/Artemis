package de.tum.cit.aet.artemis.repo.repository;

import java.util.Set;

import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Repository
public interface UserTestRepository extends ArtemisJpaRepository<User, Long> {

    Set<User> findAllByGroupsNotEmpty();
}
