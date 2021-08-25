package de.tum.in.www1.artemis.service.dto.uml;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude
public class UMLRelationshipDTO extends UMLElementDTO {

    private List<Coordinates> path;

    private RelationshipAnchor source;

    private RelationshipAnchor target;

    public UMLRelationshipDTO(String id, String name, String type, Bounds bounds, List<Coordinates> path, RelationshipAnchor source, RelationshipAnchor target) {
        super(id, name, type, bounds);
        this.path = path;
        this.source = source;
        this.target = target;
    }

    public List<Coordinates> getPath() {
        return path;
    }

    public void setPath(List<Coordinates> path) {
        this.path = path;
    }

    public RelationshipAnchor getSource() {
        return source;
    }

    public void setSource(RelationshipAnchor source) {
        this.source = source;
    }

    public RelationshipAnchor getTarget() {
        return target;
    }

    public void setTarget(RelationshipAnchor target) {
        this.target = target;
    }

    @JsonInclude
    public static final class Coordinates {

        private int x;

        private int y;

        public Coordinates(int x, int y) {
            this.x = x;
            this.y = y;
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
    }

    @JsonInclude
    public static final class RelationshipAnchor {

        private String direction;

        private String element;

        public RelationshipAnchor(String direction, String element) {
            this.direction = direction;
            this.element = element;
        }

        public String getDirection() {
            return direction;
        }

        public void setDirection(String direction) {
            this.direction = direction;
        }

        public String getElement() {
            return element;
        }

        public void setElement(String element) {
            this.element = element;
        }
    }
}
