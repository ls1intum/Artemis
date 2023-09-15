package de.tum.in.www1.artemis.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import de.tum.in.www1.artemis.domain.User;

public interface UserTestRepository extends JpaRepository<User, Long> {

    @Query("""
            SELECT user from User user
            JOIN user.groups
            """)
    Set<User> findAllInAnyGroup();
}
