package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.dto.ImageDTO;

class ImageDTOTest {

    private static ImageDTO image(int page, byte... bytes) {
        return new ImageDTO(page, 1.5f, 2.5f, 100, 200, 50, 60, bytes);
    }

    @Test
    void equalsComparesArrayContentAndAllFields() {
        var image = image(1, (byte) 1, (byte) 2);

        assertThat(image).isEqualTo(image).isEqualTo(image(1, (byte) 1, (byte) 2)).isNotEqualTo(image(2, (byte) 1, (byte) 2)).isNotEqualTo(image(1, (byte) 1, (byte) 3))
                .isNotEqualTo(new ImageDTO(1, 9f, 2.5f, 100, 200, 50, 60, new byte[] { 1, 2 })).isNotEqualTo(new ImageDTO(1, 1.5f, 9f, 100, 200, 50, 60, new byte[] { 1, 2 }))
                .isNotEqualTo(new ImageDTO(1, 1.5f, 2.5f, 1, 200, 50, 60, new byte[] { 1, 2 })).isNotEqualTo(new ImageDTO(1, 1.5f, 2.5f, 100, 1, 50, 60, new byte[] { 1, 2 }))
                .isNotEqualTo(new ImageDTO(1, 1.5f, 2.5f, 100, 200, 1, 60, new byte[] { 1, 2 })).isNotEqualTo(new ImageDTO(1, 1.5f, 2.5f, 100, 200, 50, 1, new byte[] { 1, 2 }))
                .isNotEqualTo(null).isNotEqualTo("image");
    }

    @Test
    void hashCodeIsConsistentWithEquals() {
        assertThat(image(1, (byte) 1, (byte) 2)).hasSameHashCodeAs(image(1, (byte) 1, (byte) 2));
        assertThat(image(1, (byte) 1, (byte) 2).hashCode()).isNotEqualTo(image(1, (byte) 1, (byte) 3).hashCode());
    }

    @Test
    void toStringContainsFieldsAndByteCount() {
        assertThat(image(3, (byte) 7, (byte) 8)).hasToString(
                "ImageDTO[page=3, xPosition=1.5, yPosition=2.5, originalWidth=100, originalHeight=200, " + "renderedWidth=50, renderedHeight=60, imageInBytes=2 bytes]");
    }
}
