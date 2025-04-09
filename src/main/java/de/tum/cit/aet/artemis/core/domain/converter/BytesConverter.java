package de.tum.cit.aet.artemis.core.domain.converter;

import java.util.Base64;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.springframework.security.web.webauthn.api.Bytes;

@Converter(autoApply = true)
public class BytesConverter implements AttributeConverter<Bytes, byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(Bytes attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getBytes();
    }

    @Override
    public Bytes convertToEntityAttribute(byte[] dbData) {
        if (dbData == null || dbData.length == 0) {
            return null;
        }
        return new Bytes(dbData);
    }

    /**
     * Converts a Long value to a Bytes object by encoding the Long as a Base64 string.
     *
     * @param value the Long value to be converted
     * @return a Bytes object representing the Base64-encoded Long value
     */
    public static Bytes longToBytes(Long value) {
        String userIdAsBase64 = Base64.getEncoder().encodeToString(value.toString().getBytes());
        return Bytes.fromBase64(userIdAsBase64);
    }

    /**
     * Converts a Bytes object back to a Long value by decoding the Base64 string.
     *
     * @param value the Bytes object to be converted
     * @return the Long value represented by the decoded Bytes object
     */
    public static Long bytesToLong(Bytes value) {
        byte[] decodedBytes = Base64.getDecoder().decode(value.toBase64UrlString());
        String decodedString = new String(decodedBytes);
        return Long.parseLong(decodedString);
    }
}
