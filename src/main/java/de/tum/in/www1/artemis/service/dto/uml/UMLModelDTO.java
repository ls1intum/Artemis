package de.tum.in.www1.artemis.service.dto.uml;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude
public class UMLModelDTO {

    private String version;

    private String type;

    private Size size;

    private List<UMLElementDTO> elements;

    private List<UMLRelationshipDTO> relationships;

    private Interactive interactive;

    private List<Void> assessments;

    public UMLModelDTO(String version, String type, Size size, List<UMLElementDTO> elements, List<UMLRelationshipDTO> relationships) {
        this.version = version;
        this.type = type;
        this.size = size;
        this.elements = elements;
        this.relationships = relationships;
        this.interactive = new Interactive();
        this.assessments = new ArrayList<>();
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Size getSize() {
        return size;
    }

    public void setSize(Size size) {
        this.size = size;
    }

    public List<UMLElementDTO> getElements() {
        return elements;
    }

    public void setElements(List<UMLElementDTO> elements) {
        this.elements = elements;
    }

    public List<UMLRelationshipDTO> getRelationships() {
        return relationships;
    }

    public void setRelationships(List<UMLRelationshipDTO> relationships) {
        this.relationships = relationships;
    }

    public Interactive getInteractive() {
        return interactive;
    }

    public List<Void> getAssessments() {
        return assessments;
    }

    @JsonInclude
    public static final class Size {

        private int width;

        private int height;

        public Size(int width, int height) {
            this.width = width;
            this.height = height;
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

    @JsonInclude
    public static final class Interactive {

        private List<UMLElementDTO> elements;

        private List<UMLRelationshipDTO> relationships;

        Interactive() {
            this.elements = new ArrayList<>();
            this.relationships = new ArrayList<>();
        }

        public List<UMLElementDTO> getElements() {
            return elements;
        }

        public List<UMLRelationshipDTO> getRelationships() {
            return relationships;
        }
    }
}
