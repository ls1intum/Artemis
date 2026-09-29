package de.tum.cit.aet.artemis.iris.domain.message;

import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityDTO;

@Converter
public class IrisMessageToolActivityConverter implements AttributeConverter<List<PyrisActivityDTO>, String> {

    private static final Logger log = LoggerFactory.getLogger(IrisMessageToolActivityConverter.class);

    private static final JsonMapper objectMapper = JsonObjectMapper.get();

    @Override
    public String convertToDatabaseColumn(List<PyrisActivityDTO> activities) {
        if (activities == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(activities);
        }
        catch (JacksonException e) {
            throw new IllegalArgumentException("Could not convert Iris tool activities to JSON", e);
        }
    }

    @Override
    public List<PyrisActivityDTO> convertToEntityAttribute(String jsonData) {
        if (jsonData == null || jsonData.isEmpty()) {
            return null;
        }
        try {
            JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, PyrisActivityDTO.class);
            return objectMapper.readValue(jsonData, type);
        }
        catch (JacksonException e) {
            // A stored value that is not a tool trail is read as no trail at all, rather than failing the read. The
            // trail is a rendering detail of one message, while the read that fails is the one loading a whole chat
            // session: a single unreadable value would otherwise leave the user unable to open the chat.
            log.warn("Ignoring unreadable Iris tool activities", e);
            return null;
        }
    }
}
