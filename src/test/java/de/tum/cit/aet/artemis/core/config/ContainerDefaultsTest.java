package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link ContainerDefaults}.
 * <p>
 * An installation that sets nothing has to behave exactly as before, so the empty case is the first one. What has to fail
 * at startup, and not later, is an entry that cannot be passed to a container and a network that exercises may not
 * select, because either would otherwise fail every build and not one. A message must never repeat a value.
 */
class ContainerDefaultsTest {

    private static final List<String> ONLY_NONE = List.of("none");

    private static ContainerDefaults defaultsWith(List<String> env, String network, List<String> allowedNetworks) {
        return new ContainerDefaults(new ContainerDefaultsConfiguration(env, network), allowedNetworks);
    }

    @Test
    void shouldChangeNothingWithoutConfiguration() {
        ContainerDefaults defaults = defaultsWith(List.of(), null, ONLY_NONE);

        assertThat(defaults.environment()).isEmpty();
        assertThat(defaults.network()).isNull();
    }

    @Test
    void shouldKeepTheCaseOfTheVariableNames() {
        // the point of binding a list and not a map: a map bound from an environment variable arrives in lower case
        ContainerDefaults defaults = defaultsWith(List.of("CI=true", "Maven_Opts=-Xmx512m"), null, ONLY_NONE);

        assertThat(defaults.environment()).containsOnlyKeys("CI", "Maven_Opts").containsEntry("CI", "true").containsEntry("Maven_Opts", "-Xmx512m");
    }

    @Test
    void shouldKeepEqualsSignsAndCommasInAValueAndAllowAnEmptyOne() {
        ContainerDefaults defaults = defaultsWith(List.of("OPTS=-Da=b,-Dc=d", "EMPTY="), null, ONLY_NONE);

        assertThat(defaults.environment()).containsEntry("OPTS", "-Da=b,-Dc=d").containsEntry("EMPTY", "");
    }

    @Test
    void shouldLetALaterEntryWinOverAnEarlierOneWithTheSameName() {
        assertThat(defaultsWith(List.of("CI=false", "CI=true"), null, ONLY_NONE).environment()).containsEntry("CI", "true");
    }

    @Test
    void shouldTrimTheNetworkAndTreatABlankOneAsNone() {
        assertThat(defaultsWith(List.of(), " none ", ONLY_NONE).network()).isEqualTo("none");
        assertThat(defaultsWith(List.of(), "  ", ONLY_NONE).network()).isNull();
        assertThat(defaultsWith(List.of(), "", ONLY_NONE).network()).isNull();
    }

    @Test
    void shouldAcceptANetworkThatExercisesMaySelect() {
        assertThat(defaultsWith(List.of(), "artemis-restricted", List.of("none", "artemis-restricted")).network()).isEqualTo("artemis-restricted");
    }

    @Test
    void shouldRefuseANetworkThatExercisesMayNotSelect() {
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> defaultsWith(List.of(), "bridge", ONLY_NONE)).withMessageContaining("container-defaults.network")
                .withMessageContaining("allowed-custom-networks");
    }

    @ParameterizedTest
    @ValueSource(strings = { "NO_SEPARATOR", "=value", " =value", "1STARTS_WITH_A_DIGIT=x", "HAS-A-DASH=x", "HAS SPACE=x", "A.B=x" })
    void shouldRefuseAnEntryThatIsNotAVariableAssignment(String entry) {
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> defaultsWith(List.of("CI=true", entry), null, ONLY_NONE))
                .withMessageContaining("container-defaults.env").withMessageContaining("Entry 1");
    }

    @Test
    void shouldNeverRepeatAValueInTheMessageOfAnEntryItRefuses() {
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> defaultsWith(List.of("A SECRET NAME=the-secret-value"), null, ONLY_NONE))
                .satisfies(exception -> assertThat(exception.getMessage()).doesNotContain("the-secret-value").doesNotContain("A SECRET NAME"));
    }

    @Test
    void shouldRefuseANullEntry() {
        List<String> entries = new java.util.ArrayList<>();
        entries.add(null);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> defaultsWith(entries, null, ONLY_NONE)).withMessageContaining("Entry 0");
    }
}
