package de.tum.in.www1.artemis.web.rest.dto;

import java.awt.image.BufferedImage;

/**
 * Contains the information about an exam user image
 */
public class ExamUserImageDTO {

    private int page;

    private float xPosition;

    private float yPosition;

    private int originalWidth;

    private int originalHeight;

    private int renderedWidth;

    private int renderedHeight;

    private BufferedImage image;

    public BufferedImage getImage() {
        return image;
    }

    public void setImage(BufferedImage image) {
        this.image = image;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public float getXPosition() {
        return xPosition;
    }

    public void setXPosition(float xPosition) {
        this.xPosition = xPosition;
    }

    public float getYPosition() {
        return yPosition;
    }

    public void setYPosition(float yPosition) {
        this.yPosition = yPosition;
    }

    public int getOriginalWidth() {
        return originalWidth;
    }

    public void setOriginalWidth(int originalWidth) {
        this.originalWidth = originalWidth;
    }

    public int getOriginalHeight() {
        return originalHeight;
    }

    public void setOriginalHeight(int originalHeight) {
        this.originalHeight = originalHeight;
    }

    public int getRenderedWidth() {
        return renderedWidth;
    }

    public void setRenderedWidth(int renderedWidth) {
        this.renderedWidth = renderedWidth;
    }

    public int getRenderedHeight() {
        return renderedHeight;
    }

    public void setRenderedHeight(int renderedHeight) {
        this.renderedHeight = renderedHeight;
    }
}
