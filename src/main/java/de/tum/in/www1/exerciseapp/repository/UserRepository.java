package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.User;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for the User entity.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    public static String USERS_CACHE = "users";

    Optional<User> findOneByActivationKey(String activationKey);

    List<User> findAllByActivatedIsFalseAndCreatedDateBefore(Instant dateTime);

    Optional<User> findOneByResetKey(String resetKey);

    Optional<User> findOneByEmailIgnoreCase(String email);

    Optional<User> findOneByLogin(String login);

    @EntityGraph(attributePaths = "authorities")
    Optional<User> findOneWithAuthoritiesById(Long id);

    @EntityGraph(attributePaths = "authorities")
    @Cacheable(cacheNames = USERS_CACHE)
    Optional<User> findOneWithAuthoritiesByLogin(String login);

    Page<User> findAllByLoginNot(Pageable pageable, String login);

    //TODO: is this really faster than populating the user and its group in Hibernate/Java?
    @Query("select COUNT(u) = 1 from User u where u.login = :username and :teachingAssistantGroup member of u.groups")
    boolean isTeachingAssistantInCourse(@Param("username") String username, @Param("teachingAssistantGroup") String teachingAssistantGroup);
    //TODO: implement SQL query for authorization checks
}
