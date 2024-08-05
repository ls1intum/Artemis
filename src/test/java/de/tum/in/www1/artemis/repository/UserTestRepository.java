package de.tum.in.www1.artemis.repository;

import java.util.Set;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

public interface UserTestRepository extends ArtemisJpaRepository<User, Long> {

    Set<User> findAllByGroupsNotEmpty();
}
