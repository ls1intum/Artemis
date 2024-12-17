package de.tum.cit.aet.artemis.core.repository.base;

import java.util.Optional;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;

/**
 * Interface for simple (repository) services. These are services on repository
 * layer which contain the default methods of the repository layer/data access.
 */
public interface ISimpleService<T extends DomainObject> {

    String getEntityName();

    default <U extends T> U getValueElseThrow(Optional<U> optional) {
        return getValueElseThrow(optional, getEntityName());
    }

    default <U extends T> U getValueElseThrow(Optional<U> optional, String entityName) {
        return optional.orElseThrow(() -> new EntityNotFoundException(entityName));
    }
}
