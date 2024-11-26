package de.tum.cit.aet.artemis.core.repository.base;

import java.util.Optional;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;

public abstract class AbstractSimpleRepositoryService {

    protected <U> U getValueElseThrow(Optional<U> optional, String entityName) {
        return optional.orElseThrow(() -> new EntityNotFoundException(entityName));
    }
}
