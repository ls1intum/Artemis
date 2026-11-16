package de.tum.cit.aet.artemis.account.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.stereotype.Repository;

/**
 * Serializes bounded creation for a user across all application nodes. Inherit this query in the repository owning
 * the creation transaction; only the user id is fetched, and the lock must remain held through counting and inserting.
 */
@Repository
@Profile(PROFILE_CORE)
@Lazy
@NoRepositoryBean
public interface UserCreationLockRepository {

    /**
     * Locks the user's existing row until the enclosing repository transaction completes.
     *
     * @param userId the user whose creation limit is checked
     * @return the locked user id
     */
    @Query(value = "SELECT id FROM jhi_user WHERE id = :userId FOR UPDATE", nativeQuery = true)
    Long lockUserForCreation(long userId);
}
