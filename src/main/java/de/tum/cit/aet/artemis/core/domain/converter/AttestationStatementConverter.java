package de.tum.cit.aet.artemis.core.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.webauthn4j.data.attestation.statement.AttestationStatement;
import com.webauthn4j.util.Base64UrlUtil;

import de.tum.cit.aet.artemis.core.domain.AttestationStatementSerializationContainer;

@Converter
public class AttestationStatementConverter extends CustomConverter implements AttributeConverter<AttestationStatement, String> {

    @Override
    public String convertToDatabaseColumn(AttestationStatement attribute) {
        AttestationStatementSerializationContainer container = new AttestationStatementSerializationContainer(attribute);
        return Base64UrlUtil.encodeToString(getObjectConverter().getCborConverter().writeValueAsBytes(container));
    }

    @Override
    public AttestationStatement convertToEntityAttribute(String dbData) {
        byte[] data = Base64UrlUtil.decode(dbData);
        AttestationStatementSerializationContainer container = getObjectConverter().getCborConverter().readValue(data, AttestationStatementSerializationContainer.class);
        return container.getAttestationStatement(); // TODO is the potential nullpointer a problem?
    }
}
