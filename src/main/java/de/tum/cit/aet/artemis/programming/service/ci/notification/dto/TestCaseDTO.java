package de.tum.cit.aet.artemis.programming.service.ci.notification.dto;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.util.ObjectUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.service.dto.TestCaseBaseDTO;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TestCaseDTO(String name, String classname, double time, @JsonProperty("failures") @JsonSetter(nulls = Nulls.AS_EMPTY) List<TestCaseDetailMessageDTO> failures,
        @JsonProperty("errors") @JsonSetter(nulls = Nulls.AS_EMPTY) List<TestCaseDetailMessageDTO> errors,
        @JsonProperty("successInfos") @JsonSetter(nulls = Nulls.AS_EMPTY) List<TestCaseDetailMessageDTO> successInfos) implements TestCaseBaseDTO {

    @JsonIgnore
    public boolean isSuccessful() {
        return ObjectUtils.isEmpty(errors) && ObjectUtils.isEmpty(failures);
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
        boolean hasErrors = !ObjectUtils.isEmpty(errors());
        boolean hasFailures = !ObjectUtils.isEmpty(failures());
        boolean hasSuccessInfos = !ObjectUtils.isEmpty(successInfos());
        boolean successful = isSuccessful();

        if (successful && hasSuccessInfos && successInfos().getFirst().getMostInformativeMessage() != null) {
            return Optional.of(successInfos().getFirst().getMostInformativeMessage());
        }
        else if (hasErrors && errors().getFirst().getMostInformativeMessage() != null) {
            return Optional.of(errors().getFirst().getMostInformativeMessage());
        }
        else if (hasFailures && failures().getFirst().getMostInformativeMessage() != null) {
            return Optional.of(failures().getFirst().getMostInformativeMessage());
        }
        else if (hasErrors && errors().getFirst().type() != null) {
            return Optional.of(String.format("Unsuccessful due to an error of type: %s", errors().getFirst().type()));
        }
        else if (hasFailures && failures().getFirst().type() != null) {
            return Optional.of(String.format("Unsuccessful due to an error of type: %s", failures().getFirst().type()));
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
