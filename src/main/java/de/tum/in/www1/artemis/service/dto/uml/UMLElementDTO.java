package de.tum.in.www1.artemis.service.dto.uml;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude
public class UMLElementDTO {

    private String id;

    private String name;

    private String type;

    private Bounds bounds;

    public UMLElementDTO(String id, String name, String type, Bounds bounds) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.bounds = bounds;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Bounds getBounds() {
        return bounds;
    }

    public void setBounds(Bounds bounds) {
        this.bounds = bounds;
    }

    @JsonInclude
    public static final class Bounds {

        private int x;

        private int y;

        private int width;

        private int height;

        public Bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }
    }
}
