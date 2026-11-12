package de.tum.cit.aet.artemis.exercise.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ParticipationFilterTest {

    @Test
    void trimsAndDeduplicatesSelections() {
        assertThat(ParticipationFilter.parse("Successful, Manual,Manual,Automatic")).containsExactlyInAnyOrder(ParticipationFilter.Successful, ParticipationFilter.Manual,
                ParticipationFilter.Automatic);
    }

    @Test
    void rejectsUnknownAndEmptySelections() {
        for (String value : new String[] { "Successful,Unknown", "Manual,", ",Automatic" }) {
            assertThatThrownBy(() -> ParticipationFilter.parse(value)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void acceptsEmptyAndLegacyUnfilteredRequests() {
        for (String value : new String[] { "", " ", "All", "ALL" }) {
            assertThat(ParticipationFilter.parse(value)).isEmpty();
        }
        assertThat(ParticipationFilter.parse(null)).isEmpty();
    }
}
