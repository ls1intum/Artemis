package de.tum.cit.aet.artemis.tutorialgroup;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.ZonedDateTime;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupFreePeriodDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupSummaryDTO;

/**
 * Contract tests that pin down the field types of REST DTOs in the tutorial group module.
 * <p>
 * These guard against regressions where typed values (timestamps, enums) get downgraded to
 * {@code String} via {@code toString()} or {@code Enum.name()} in a {@code from(entity)} mapper.
 * Such a downgrade silently changes the wire format (e.g. {@code ZonedDateTime.toString()} appends
 * a Java-specific {@code [ZoneId]} suffix that breaks strict ISO-8601 parsers) and weakens the
 * generated OpenAPI client.
 */
class TutorialGroupDTOContractTest {

    @Test
    void tutorialGroupFreePeriodDtoStartAndEndShouldBeTypedZonedDateTime() {
        assertThat(componentType(TutorialGroupFreePeriodDTO.class, "start")).isEqualTo(ZonedDateTime.class);
        assertThat(componentType(TutorialGroupFreePeriodDTO.class, "end")).isEqualTo(ZonedDateTime.class);
    }

    @Test
    void tutorialGroupSummarySessionDtoStartAndEndShouldBeTypedZonedDateTime() {
        assertThat(componentType(TutorialGroupSummaryDTO.TutorialGroupSummarySessionDTO.class, "start")).isEqualTo(ZonedDateTime.class);
        assertThat(componentType(TutorialGroupSummaryDTO.TutorialGroupSummarySessionDTO.class, "end")).isEqualTo(ZonedDateTime.class);
    }

    @Test
    void tutorialGroupSummarySessionDtoStatusShouldBeTypedEnum() {
        assertThat(componentType(TutorialGroupSummaryDTO.TutorialGroupSummarySessionDTO.class, "status")).isEqualTo(TutorialGroupSessionStatus.class);
    }

    private static Class<?> componentType(Class<?> recordClass, String componentName) {
        return Arrays.stream(recordClass.getRecordComponents()).filter(rc -> rc.getName().equals(componentName)).map(RecordComponent::getType).findFirst()
                .orElseThrow(() -> new AssertionError("Record component not found: " + recordClass.getSimpleName() + "." + componentName));
    }
}
