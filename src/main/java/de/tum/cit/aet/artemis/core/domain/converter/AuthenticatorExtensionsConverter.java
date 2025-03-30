package de.tum.cit.aet.artemis.core.domain.converter;

import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.webauthn4j.converter.util.CborConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.extension.authenticator.RegistrationExtensionAuthenticatorOutput;
import com.webauthn4j.util.Base64UrlUtil;

@Converter
public class AuthenticatorExtensionsConverter implements AttributeConverter<Map<String, RegistrationExtensionAuthenticatorOutput>, String> {

    private CborConverter cborConverter;

    @Resource
    ObjectConverter objectConverter;

    @PostConstruct
    private void init() {
        this.cborConverter = objectConverter.getCborConverter();
    }

    @Override
    public String convertToDatabaseColumn(Map<String, RegistrationExtensionAuthenticatorOutput> attribute) {
        return Base64UrlUtil.encodeToString(cborConverter.writeValueAsBytes(attribute));
    }

    @Override
    public Map<String, RegistrationExtensionAuthenticatorOutput> convertToEntityAttribute(String dbData) {
        return cborConverter.readValue(Base64UrlUtil.decode(dbData), new TypeReference<Map<String, RegistrationExtensionAuthenticatorOutput>>() {
        });
    }
}
