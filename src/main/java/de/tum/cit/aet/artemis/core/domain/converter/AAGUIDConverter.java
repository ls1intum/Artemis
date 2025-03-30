package de.tum.cit.aet.artemis.core.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.webauthn4j.data.attestation.authenticator.AAGUID;

@Converter
public class AAGUIDConverter implements AttributeConverter<AAGUID, byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(AAGUID attribute) {
        return attribute.getBytes();
    }

    @Override
    public AAGUID convertToEntityAttribute(byte[] dbData) {
        return new AAGUID(dbData);
    }
}
