package de.tum.cit.aet.artemis.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.util.DateUtil;

/**
 * The time zones a course may use are the ones the server can interpret, not the ones a browser happens to list.
 */
class CourseTimeZoneValidationTest {

    // Browsers leave several of these out of Intl.supportedValuesOf('timeZone'), which is why the server's list is the one the form uses.
    @ParameterizedTest
    @ValueSource(strings = { "UTC", "Etc/UTC", "GMT", "Europe/Kyiv", "Europe/Kiev", "Asia/Kolkata", "Asia/Calcutta", "Europe/Berlin", "America/Argentina/Buenos_Aires" })
    void acceptsTimeZonesTheServerCanInterpret(String timeZone) {
        assertThat(DateUtil.isSupportedTimeZone(timeZone)).isTrue();
        assertThatNoException().isThrownBy(() -> ZoneId.of(timeZone));
        assertThatNoException().isThrownBy(() -> CourseValidator.validateTimeZone(timeZone));
    }

    // Browsers accept the first two case-insensitively or as an alias, but ZoneId.of rejects them; the time zone database dropped the SystemV names.
    @ParameterizedTest
    @ValueSource(strings = { "europe/berlin", "EST", "SystemV/EST5", "Europe/Ber", "", " Europe/Berlin" })
    void rejectsTimeZonesACourseMustNotUse(String timeZone) {
        assertThat(DateUtil.isSupportedTimeZone(timeZone)).isFalse();
        assertThatThrownBy(() -> CourseValidator.validateTimeZone(timeZone)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void allowsACourseWithoutTimeZone() {
        assertThat(DateUtil.isSupportedTimeZone(null)).isFalse();
        assertThatNoException().isThrownBy(() -> CourseValidator.validateTimeZone(null));
    }

    @Test
    void listsEverySupportedTimeZoneOnceSortedAndInterpretable() {
        assertThat(DateUtil.SUPPORTED_TIME_ZONES).isSorted().doesNotHaveDuplicates().noneMatch(zoneId -> zoneId.startsWith("SystemV/"));
        assertThat(DateUtil.SUPPORTED_TIME_ZONES).allSatisfy(zoneId -> assertThatNoException().isThrownBy(() -> ZoneId.of(zoneId)));
    }
}
