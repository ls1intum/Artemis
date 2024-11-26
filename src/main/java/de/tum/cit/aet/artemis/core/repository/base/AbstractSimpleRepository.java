package de.tum.cit.aet.artemis.core.repository.base;

import java.util.Optional;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;

/**
 * Abstract base for simple repository services. These are services on repository
 * layer which contain the default methods of the repository layer/data access.
 */
public abstract class AbstractSimpleRepository {

    protected <U> U getValueElseThrow(Optional<U> optional, String entityName) {
        return optional.orElseThrow(() -> new EntityNotFoundException(entityName));
    }
}
