package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.in.www1.artemis.web.rest.dto.user.IrisCodeEditorClientDTO;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// @formatter:off
@JsonSubTypes({
        @JsonSubTypes.Type(value = IrisCodeEditorClientDTO.class, name = "code_editor")
})
// @formatter:on
public class IrisClientArgumentsDTO {
}
