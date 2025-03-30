package de.tum.cit.aet.artemis.core.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.webauthn4j.data.attestation.authenticator.COSEKey;

@Converter
public class COSEKeyConverter extends CustomConverter implements AttributeConverter<COSEKey, byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(COSEKey attribute) {
        return getObjectConverter().getCborConverter().writeValueAsBytes(attribute);
    }

    @Override
    public COSEKey convertToEntityAttribute(byte[] dbData) {
        return getObjectConverter().getCborConverter().readValue(dbData, COSEKey.class);
    }
}
