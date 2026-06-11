package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

import com.vdurmont.semver4j.Semver;
import com.vdurmont.semver4j.SemverException;

class ArtemisVersionUtilTest {

    @Test
    void parsesTwoPartVersion() {
        Semver semver = ArtemisVersionUtil.parseForComparison("9.2");
        assertThat(semver.getMajor()).isEqualTo(9);
        assertThat(semver.getMinor()).isEqualTo(2);
        assertThat(semver.getPatch()).isEqualTo(0);
    }

    @Test
    void parsesThreePartHotfixVersion() {
        Semver semver = ArtemisVersionUtil.parseForComparison("10.4.1");
        assertThat(semver.getMajor()).isEqualTo(10);
        assertThat(semver.getMinor()).isEqualTo(4);
        assertThat(semver.getPatch()).isEqualTo(1);
    }

    @Test
    void parsesZeroMajorTwoPart() {
        Semver semver = ArtemisVersionUtil.parseForComparison("0.5");
        assertThat(semver.getMajor()).isEqualTo(0);
        assertThat(semver.getMinor()).isEqualTo(5);
        assertThat(semver.getPatch()).isEqualTo(0);
    }

    @Test
    void trimsWhitespace() {
        Semver semver = ArtemisVersionUtil.parseForComparison("  9.2  ");
        assertThat(semver.getMajor()).isEqualTo(9);
        assertThat(semver.getMinor()).isEqualTo(2);
        assertThat(semver.getPatch()).isEqualTo(0);
    }

    @Test
    void rejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> ArtemisVersionUtil.parseForComparison(null));
    }

    @Test
    void rejectsGarbage() {
        assertThatExceptionOfType(SemverException.class).isThrownBy(() -> ArtemisVersionUtil.parseForComparison("garbage"));
    }

    @Test
    void rejectsTooManyParts() {
        assertThatExceptionOfType(SemverException.class).isThrownBy(() -> ArtemisVersionUtil.parseForComparison("9.2.3.4"));
    }

    @Test
    void comparesAcrossTwoAndThreePart() {
        assertThat(ArtemisVersionUtil.parseForComparison("10.5").isGreaterThan(ArtemisVersionUtil.parseForComparison("10.4.1"))).isTrue();
    }

    @Test
    void treatsTwoPartAndPaddedThreePartAsEqual() {
        assertThat(ArtemisVersionUtil.parseForComparison("9.2").isEqualTo(ArtemisVersionUtil.parseForComparison("9.2.0"))).isTrue();
    }
}
