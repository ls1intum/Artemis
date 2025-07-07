package de.tum.cit.aet.artemis.exam.domain.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Table(name = "layout_strategy")
public class LayoutStrategy extends DomainObject {

    @Column(name = "name", nullable = false)
    private String name; // e.g., "default", "wide", "corona"

    @Column(name = "type", nullable = false)
    private String type; // e.g., "fixed_selection", "relative_distance"

    @ManyToOne
    @JoinColumn(name = "room_id", nullable = false)
    private ExamRoom room;

    @Column(name = "parameters", columnDefinition = "json", nullable = false)
    private String parametersJson;

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

    public ExamRoom getRoom() {
        return room;
    }

    public void setRoom(ExamRoom room) {
        this.room = room;
    }

    public String getParametersJson() {
        return parametersJson;
    }

    public void setParametersJson(String parametersJson) {
        this.parametersJson = parametersJson;
    }
}
