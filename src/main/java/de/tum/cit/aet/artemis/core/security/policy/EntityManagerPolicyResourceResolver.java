package de.tum.cit.aet.artemis.core.security.policy;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.persistence.EntityManager;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;

/**
 * Default {@link PolicyResourceResolver} that uses JPA's {@link EntityManager#find} to load any entity by ID.
 * <p>
 * This resolver is used as a fallback when no module-specific resolver is registered for a given resource type.
 * It works for any JPA entity without requiring custom repository methods. Relationships accessed by the policy
 * conditions will be lazy-loaded within the current persistence context.
 */
@Component
@Profile(PROFILE_CORE)
@Lazy
public class EntityManagerPolicyResourceResolver {

    private final EntityManager entityManager;

    public EntityManagerPolicyResourceResolver(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Loads an entity of the given type by its primary key using {@link EntityManager#find}.
     *
     * @param <T>          the entity type
     * @param resourceType the entity class
     * @param id           the entity ID
     * @return the loaded entity
     * @throws EntityNotFoundException if no entity with the given ID exists
     */
    public <T> T loadById(Class<T> resourceType, long id) {
        T entity = entityManager.find(resourceType, id);
        if (entity == null) {
            throw new EntityNotFoundException(resourceType.getSimpleName(), id);
        }
        return entity;
    }
}
