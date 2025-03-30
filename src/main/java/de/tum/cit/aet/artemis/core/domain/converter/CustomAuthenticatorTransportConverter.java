package de.tum.cit.aet.artemis.core.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.webauthn4j.converter.AuthenticatorTransportConverter;
import com.webauthn4j.data.AuthenticatorTransport;

@Converter(autoApply = true)
public class CustomAuthenticatorTransportConverter implements AttributeConverter<AuthenticatorTransport, String> {

    private final AuthenticatorTransportConverter transportConverter = new AuthenticatorTransportConverter();

    @Override
    public String convertToDatabaseColumn(AuthenticatorTransport attribute) {
        if (attribute == null) {
            return null;
        }
        return transportConverter.convertToString(attribute);
    }

    @Override
    public AuthenticatorTransport convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return transportConverter.convert(dbData);
    }
}
