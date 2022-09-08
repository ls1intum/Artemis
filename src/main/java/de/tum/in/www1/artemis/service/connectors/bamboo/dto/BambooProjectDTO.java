package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BambooProjectDTO(String key, String name, String description, BambooBuildPlansDTO plans) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BambooBuildPlansDTO(List<BambooBuildPlanDTO> plan) {

        // Note: this constructor makes sure that null values are deserialized as empty lists (to allow iterations): https://github.com/FasterXML/jackson-databind/issues/2974
        @JsonCreator
        public BambooBuildPlansDTO(@JsonProperty("plan") @JsonSetter(nulls = Nulls.AS_EMPTY) List<BambooBuildPlanDTO> plan) {
            this.plan = plan;
        }
    }
}
