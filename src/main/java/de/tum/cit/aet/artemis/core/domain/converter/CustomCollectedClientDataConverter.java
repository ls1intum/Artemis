package de.tum.cit.aet.artemis.core.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.webauthn4j.converter.CollectedClientDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.client.CollectedClientData;

@Converter(autoApply = true)
public class CustomCollectedClientDataConverter implements AttributeConverter<CollectedClientData, byte[]> {

    private final CollectedClientDataConverter delegate = new CollectedClientDataConverter(new ObjectConverter());

    @Override
    public byte[] convertToDatabaseColumn(CollectedClientData attribute) {
        return attribute == null ? null : delegate.convertToBytes(attribute);
    }

    @Override
    public CollectedClientData convertToEntityAttribute(byte[] dbData) {
        return dbData == null ? null : delegate.convert(dbData);
    }
}
