package de.tum.cit.aet.artemis.core.domain.converter;

import java.util.Map;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.webauthn4j.data.extension.client.RegistrationExtensionClientOutput;

@Converter
public class ClientExtensionsConverter extends CustomConverter implements AttributeConverter<Map<String, RegistrationExtensionClientOutput>, String> {

    @Override
    public String convertToDatabaseColumn(Map<String, RegistrationExtensionClientOutput> attribute) {
        return getObjectConverter().getJsonConverter().writeValueAsString(attribute);
    }

    @Override
    public Map<String, RegistrationExtensionClientOutput> convertToEntityAttribute(String dbData) {
        return getObjectConverter().getJsonConverter().readValue(dbData, new TypeReference<Map<String, RegistrationExtensionClientOutput>>() {
        });
    }
}
