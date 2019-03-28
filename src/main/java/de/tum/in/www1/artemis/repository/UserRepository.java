package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for the User entity. */
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

    @Query(
            "SELECT r.participation.student.id FROM Result r WHERE r.submission.id = :#{#submissionId}")
    Long findUserIdBySubmissionId(@Param("submissionId") Long submissionId);

    @Query("SELECT r.participation.student FROM Result r WHERE r.id = :#{#resultId}")
    User findUserByResultId(@Param("resultId") Long resultId);

    Long countByGroupsIsContaining(List<String> groups);

    @Modifying
    @Query("Update User u SET u.lastNotificationRead = utc_timestamp where u.id = :#{#userId}")
    void updateUserNotificationReadDate(@Param("userId") Long id);
}
