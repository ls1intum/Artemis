package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = PyrisTextMessageContentDTO.class, name = "text"), @JsonSubTypes.Type(value = PyrisJsonMessageContentDTO.class, name = "json"),
        @JsonSubTypes.Type(value = PyrisImageMessageContentDTO.class, name = "image"), })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public interface PyrisMessageContentBaseDTO {
}
