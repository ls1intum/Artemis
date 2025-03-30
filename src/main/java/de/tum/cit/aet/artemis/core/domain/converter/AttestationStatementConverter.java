package de.tum.cit.aet.artemis.core.domain.converter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.webauthn4j.converter.util.CborConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.attestation.statement.AttestationStatement;
import com.webauthn4j.util.Base64UrlUtil;

import de.tum.cit.aet.artemis.core.domain.AttestationStatementSerializationContainer;

@Converter
public class AttestationStatementConverter implements AttributeConverter<AttestationStatement, String> {

    private CborConverter cborConverter;

    @Resource
    ObjectConverter objectConverter;

    @PostConstruct
    private void init() {
        this.cborConverter = objectConverter.getCborConverter();
    }

    @Override
    public String convertToDatabaseColumn(AttestationStatement attribute) {
        AttestationStatementSerializationContainer container = new AttestationStatementSerializationContainer(attribute);
        return Base64UrlUtil.encodeToString(cborConverter.writeValueAsBytes(container));
    }

    @Override
    public AttestationStatement convertToEntityAttribute(String dbData) {
        byte[] data = Base64UrlUtil.decode(dbData);
        AttestationStatementSerializationContainer container = cborConverter.readValue(data, AttestationStatementSerializationContainer.class);
        return container.getAttestationStatement(); // TODO is the potential nullpointer a problem?
    }
}
