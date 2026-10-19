package de.tum.cit.aet.artemis.core.dto;

import java.util.Arrays;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Contains the information about image
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ImageDTO(int page, float xPosition, float yPosition, int originalWidth, int originalHeight, int renderedWidth, int renderedHeight, byte[] imageInBytes) {

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof ImageDTO that && page == that.page && Float.compare(xPosition, that.xPosition) == 0
                && Float.compare(yPosition, that.yPosition) == 0 && originalWidth == that.originalWidth && originalHeight == that.originalHeight
                && renderedWidth == that.renderedWidth && renderedHeight == that.renderedHeight && Arrays.equals(imageInBytes, that.imageInBytes));
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(page, xPosition, yPosition, originalWidth, originalHeight, renderedWidth, renderedHeight) + Arrays.hashCode(imageInBytes);
    }

    @Override
    public String toString() {
        return "ImageDTO[page=" + page + ", xPosition=" + xPosition + ", yPosition=" + yPosition + ", originalWidth=" + originalWidth + ", originalHeight=" + originalHeight
                + ", renderedWidth=" + renderedWidth + ", renderedHeight=" + renderedHeight + ", imageInBytes=" + imageInBytes.length + " bytes" + "]";
    }
}
