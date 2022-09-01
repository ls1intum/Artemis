package de.tum.in.www1.artemis.service.connectors.jenkins.dto;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TestCaseDTO(String name, String classname, double time, List<TestCaseDetailMessageDTO> failures, List<TestCaseDetailMessageDTO> errors,
        List<TestCaseDetailMessageDTO> successInfos) {

    public TestCaseDTO(String name, String classname, double time) {
        this(name, classname, time, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    @JsonIgnore
    public boolean isSuccessful() {
        return CollectionUtils.isEmpty(errors) && CollectionUtils.isEmpty(failures);
    }
}
