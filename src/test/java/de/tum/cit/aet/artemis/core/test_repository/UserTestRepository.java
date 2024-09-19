package de.tum.cit.aet.artemis.core.test_repository;

import java.util.Set;

import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;

@Repository
public interface UserTestRepository extends UserRepository {

    Set<User> findAllByGroupsNotEmpty();
}
