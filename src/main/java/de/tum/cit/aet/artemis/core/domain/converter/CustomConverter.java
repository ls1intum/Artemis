package de.tum.cit.aet.artemis.core.domain.converter;

import com.webauthn4j.converter.util.ObjectConverter;

public abstract class CustomConverter {

    private ObjectConverter objectConverter;

    public ObjectConverter getObjectConverter() {
        if (objectConverter == null) {
            objectConverter = new ObjectConverter();
        }
        return objectConverter;
    }
}
