package de.tum.cit.aet.artemis.core.domain.converter;

import java.util.Map;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.webauthn4j.data.extension.authenticator.RegistrationExtensionAuthenticatorOutput;
import com.webauthn4j.util.Base64UrlUtil;

@Converter
public class AuthenticatorExtensionsConverter extends CustomConverter implements AttributeConverter<Map<String, RegistrationExtensionAuthenticatorOutput>, String> {

    @Override
    public String convertToDatabaseColumn(Map<String, RegistrationExtensionAuthenticatorOutput> attribute) {
        return Base64UrlUtil.encodeToString(getObjectConverter().getCborConverter().writeValueAsBytes(attribute));
    }

    @Override
    public Map<String, RegistrationExtensionAuthenticatorOutput> convertToEntityAttribute(String dbData) {
        return getObjectConverter().getCborConverter().readValue(Base64UrlUtil.decode(dbData), new TypeReference<Map<String, RegistrationExtensionAuthenticatorOutput>>() {
        });
    }
}
