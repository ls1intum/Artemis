package de.tum.in.www1.artemis.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.User;

public interface UserTestRepository extends JpaRepository<User, Long> {

    Set<User> findAllByGroupsNotEmpty();
}
