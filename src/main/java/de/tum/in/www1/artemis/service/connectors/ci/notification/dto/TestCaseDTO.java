package de.tum.in.www1.artemis.service.connectors.ci.notification.dto;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.commons.collections.CollectionUtils;

import com.fasterxml.jackson.annotation.*;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.service.dto.TestCaseDTOInterface;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TestCaseDTO(String name, String classname, double time, @JsonProperty("failures") @JsonSetter(nulls = Nulls.AS_EMPTY) List<TestCaseDetailMessageDTO> failures,
        @JsonProperty("errors") @JsonSetter(nulls = Nulls.AS_EMPTY) List<TestCaseDetailMessageDTO> errors,
        @JsonProperty("successInfos") @JsonSetter(nulls = Nulls.AS_EMPTY) List<TestCaseDetailMessageDTO> successInfos) implements TestCaseDTOInterface {

    @JsonIgnore
    public boolean isSuccessful() {
        return CollectionUtils.isEmpty(errors) && CollectionUtils.isEmpty(failures);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<String> getTestMessages() {
        return extractMessage().map(Collections::singletonList).orElse(Collections.emptyList());
    }

    /**
     * Extracts the most helpful message from the test case received from the continuous integration system.
     *
     * @return the most helpful message that can be added to an automatic {@link Feedback}.
     */
    private Optional<String> extractMessage() {
        boolean hasErrors = !CollectionUtils.isEmpty(errors());
        boolean hasFailures = !CollectionUtils.isEmpty(failures());
        boolean hasSuccessInfos = !CollectionUtils.isEmpty(successInfos());
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
