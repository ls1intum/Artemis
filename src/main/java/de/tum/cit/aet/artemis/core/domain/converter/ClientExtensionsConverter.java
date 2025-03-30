package de.tum.cit.aet.artemis.core.domain.converter;

import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.webauthn4j.converter.util.JsonConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.extension.client.RegistrationExtensionClientOutput;

@Converter
public class ClientExtensionsConverter implements AttributeConverter<Map<String, RegistrationExtensionClientOutput>, String> {

    private JsonConverter jsonConverter;

    @Resource
    ObjectConverter objectConverter;

    @PostConstruct
    private void init() {
        this.jsonConverter = objectConverter.getJsonConverter();
    }

    @Override
    public String convertToDatabaseColumn(Map<String, RegistrationExtensionClientOutput> attribute) {
        return jsonConverter.writeValueAsString(attribute);
    }

    @Override
    public Map<String, RegistrationExtensionClientOutput> convertToEntityAttribute(String dbData) {
        return jsonConverter.readValue(dbData, new TypeReference<Map<String, RegistrationExtensionClientOutput>>() {
        });
    }
}
