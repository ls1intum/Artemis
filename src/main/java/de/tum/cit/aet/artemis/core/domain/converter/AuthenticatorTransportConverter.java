package de.tum.cit.aet.artemis.core.domain.converter;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.springframework.security.web.webauthn.api.AuthenticatorTransport;

@Converter(autoApply = true)
public class AuthenticatorTransportConverter implements AttributeConverter<Set<AuthenticatorTransport>, String> {

    @Override
    public String convertToDatabaseColumn(Set<AuthenticatorTransport> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "";
        }
        return attribute.stream().map(AuthenticatorTransport::getValue).collect(Collectors.joining(","));
    }

    @Override
    public Set<AuthenticatorTransport> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(dbData.split(",")).map(AuthenticatorTransport::valueOf).collect(Collectors.toSet());
    }
}
