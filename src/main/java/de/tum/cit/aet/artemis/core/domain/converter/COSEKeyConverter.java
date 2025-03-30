package de.tum.cit.aet.artemis.core.domain.converter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.webauthn4j.converter.util.CborConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.attestation.authenticator.COSEKey;

@Converter
public class COSEKeyConverter implements AttributeConverter<COSEKey, byte[]> {

    private CborConverter cborConverter;

    @Resource
    ObjectConverter objectConverter;

    @PostConstruct
    private void init() {
        this.cborConverter = objectConverter.getCborConverter();
    }

    @Override
    public byte[] convertToDatabaseColumn(COSEKey attribute) {
        return cborConverter.writeValueAsBytes(attribute);
    }

    @Override
    public COSEKey convertToEntityAttribute(byte[] dbData) {
        return cborConverter.readValue(dbData, COSEKey.class);
    }
}
