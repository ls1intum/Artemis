package de.tum.cit.aet.artemis.core.security.policy;

/**
 * Strategy interface for loading a resource entity by its ID for policy evaluation.
 * <p>
 * Implementations are discovered automatically by the
 * {@link de.tum.cit.aet.artemis.core.security.annotations.enforceAccessPolicy.EnforceAccessPolicyAspect}
 * and matched to policies via {@link #getResourceType()}.
 * <p>
 * The default implementation uses {@code EntityManager.find()} for any JPA entity.
 * Modules can register more specific resolvers (e.g., with eager fetch joins) by
 * implementing this interface and declaring the bean as a Spring component.
 *
 * @param <T> the entity type this resolver handles
 */
public interface PolicyResourceResolver<T> {

    /**
     * Returns the entity class this resolver can load.
     *
     * @return the resource type
     */
    Class<T> getResourceType();

    /**
     * Loads the resource entity by its primary key.
     *
     * @param id the entity ID
     * @return the loaded entity
     * @throws de.tum.cit.aet.artemis.core.exception.EntityNotFoundException if the entity does not exist
     */
    T loadById(long id);
}
