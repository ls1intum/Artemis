package de.tum.in.www1.artemis.service.connectors.jenkins.dto;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;

import com.fasterxml.jackson.annotation.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TestCaseDTO(String name, String classname, double time, List<TestCaseDetailMessageDTO> failures, List<TestCaseDetailMessageDTO> errors,
        List<TestCaseDetailMessageDTO> successInfos) {

    // Note: this constructor makes sure that null values are deserialized as empty lists (to allow iterations): https://github.com/FasterXML/jackson-databind/issues/2974
    @JsonCreator
    public TestCaseDTO(String name, String classname, double time, @JsonSetter(nulls = Nulls.AS_EMPTY) List<TestCaseDetailMessageDTO> failures,
            @JsonSetter(nulls = Nulls.AS_EMPTY) List<TestCaseDetailMessageDTO> errors, @JsonSetter(nulls = Nulls.AS_EMPTY) List<TestCaseDetailMessageDTO> successInfos) {
        this.name = name;
        this.classname = classname;
        this.time = time;
        this.failures = failures;
        this.errors = errors;
        this.successInfos = successInfos;
    }

    public TestCaseDTO(String name, String classname, double time) {
        this(name, classname, time, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    @JsonIgnore
    public boolean isSuccessful() {
        return CollectionUtils.isEmpty(errors) && CollectionUtils.isEmpty(failures);
    }
}
