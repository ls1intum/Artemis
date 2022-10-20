package de.tum.in.www1.artemis.service.connectors.jenkins.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.collections.CollectionUtils;

import com.fasterxml.jackson.annotation.*;

import de.tum.in.www1.artemis.service.dto.TestCaseDTOInterface;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TestCaseDTO(String name, String classname, double time, List<TestCaseDetailMessageDTO> failures, List<TestCaseDetailMessageDTO> errors,
        List<TestCaseDetailMessageDTO> successInfos) implements TestCaseDTOInterface {

    // Note: this constructor makes sure that null values are deserialized as empty lists (to allow iterations): https://github.com/FasterXML/jackson-databind/issues/2974
    @JsonCreator
    public TestCaseDTO(String name, String classname, double time, @JsonProperty("failures") @JsonSetter(nulls = Nulls.AS_EMPTY) List<TestCaseDetailMessageDTO> failures,
            @JsonProperty("errors") @JsonSetter(nulls = Nulls.AS_EMPTY) List<TestCaseDetailMessageDTO> errors,
            @JsonProperty("successInfos") @JsonSetter(nulls = Nulls.AS_EMPTY) List<TestCaseDetailMessageDTO> successInfos) {
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

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<String> getMessage() {
        return extractMessageFromTestCase().map(List::of).orElse(List.of());
    }

    private Optional<String> extractMessageFromTestCase() {
        var hasErrors = !CollectionUtils.isEmpty(errors());
        var hasFailures = !CollectionUtils.isEmpty(failures());
        var hasSuccessInfos = !CollectionUtils.isEmpty(successInfos());
        boolean successful = isSuccessful();

        if (successful && hasSuccessInfos && successInfos().get(0).getMostInformativeMessage() != null) {
            return Optional.of(successInfos().get(0).getMostInformativeMessage());
        }
        else if (hasErrors && errors().get(0).getMostInformativeMessage() != null) {
            return Optional.of(errors().get(0).getMostInformativeMessage());
        }
        else if (hasFailures && failures().get(0).getMostInformativeMessage() != null) {
            return Optional.of(failures().get(0).getMostInformativeMessage());
        }
        else if (hasErrors && errors().get(0).type() != null) {
            return Optional.of(String.format("Unsuccessful due to an error of type: %s", errors().get(0).type()));
        }
        else if (hasFailures && failures().get(0).type() != null) {
            return Optional.of(String.format("Unsuccessful due to an error of type: %s", failures().get(0).type()));
        }
        else if (!successful) {
            // this is an edge case which typically does not happen
            return Optional.of("Unsuccessful due to an unknown error. Please contact your instructor!");
        }
        else {
            // successful and no message available => do not generate one
            return Optional.empty();
        }
    }
}
