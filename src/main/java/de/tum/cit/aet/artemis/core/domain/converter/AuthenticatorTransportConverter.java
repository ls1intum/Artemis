package de.tum.cit.aet.artemis.core.domain.converter;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;

@Converter(autoApply = true)
public class AuthenticatorTransportConverter implements AttributeConverter<Set<AuthenticatorTransport>, String> {

    private static final Logger log = LoggerFactory.getLogger(AuthenticatorTransportConverter.class);

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
        return Arrays.stream(dbData.split(",")).map(value -> {
            try {
                return AuthenticatorTransport.valueOf(value);
            }
            catch (IllegalArgumentException e) {
                log.debug("Invalid AuthenticatorTransport value: {}", value);
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
