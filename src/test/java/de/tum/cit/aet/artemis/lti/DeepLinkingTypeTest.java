package de.tum.cit.aet.artemis.lti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.lti.service.DeepLinkingType;

class DeepLinkingTypeTest {

    @Test
    void fromString_validExercise() {
        DeepLinkingType type = DeepLinkingType.fromString("EXERCISE");
        assertThat(type).isEqualTo(DeepLinkingType.EXERCISE);
    }

    @Test
    void fromString_validLecture() {
        DeepLinkingType type = DeepLinkingType.fromString("LECTURE");
        assertThat(type).isEqualTo(DeepLinkingType.LECTURE);
    }

    @Test
    void fromString_validCompetency() {
        DeepLinkingType type = DeepLinkingType.fromString("COMPETENCY");
        assertThat(type).isEqualTo(DeepLinkingType.COMPETENCY);
    }

    @Test
    void fromString_validLearningPath() {
        DeepLinkingType type = DeepLinkingType.fromString("LEARNING_PATH");
        assertThat(type).isEqualTo(DeepLinkingType.LEARNING_PATH);
    }

    @Test
    void fromString_validIris() {
        DeepLinkingType type = DeepLinkingType.fromString("IRIS");
        assertThat(type).isEqualTo(DeepLinkingType.IRIS);
    }

    @Test
    void fromString_caseInsensitive() {
        DeepLinkingType type = DeepLinkingType.fromString("exercise");
        assertThat(type).isEqualTo(DeepLinkingType.EXERCISE);
    }

    @Test
    void fromString_invalidType() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> DeepLinkingType.fromString("INVALID_TYPE"))
                .withMessage("Invalid deep linking type: INVALID_TYPE");
    }

    @Test
    void fromString_nullType() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> DeepLinkingType.fromString(null)).withMessage("Invalid deep linking type: null");
    }
}
