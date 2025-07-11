package de.tum.cit.aet.artemis.exam.domain.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.springframework.context.annotation.Conditional;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;

@Conditional(ExamEnabled.class)
@Entity
@Table(name = "layout_strategy")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LayoutStrategy extends DomainObject {

    /**
     * The name of the layout strategy. Can be anything, but common ones are "default", "wide", and "corona"
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * The type of this layout strategy. May be one of {@link LayoutStrategyType}.
     */
    @Column(name = "type", nullable = false)
    private LayoutStrategyType type;

    /**
     * The room this layout strategy belongs to. One room may have multiple layout strategies.
     */
    @ManyToOne
    @JoinColumn(name = "room_id", nullable = false)
    private ExamRoom room;

    /**
     * The parameters of the layout strategy, i.e. the data that tells the strategy how to distribute the students.
     * Contents of this differ as the {@link LayoutStrategy#type} differs.
     */
    @Column(name = "parameters", columnDefinition = "json", nullable = false)
    private String parametersJson;

    /* Getters & Setters */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LayoutStrategyType getType() {
        return type;
    }

    public void setType(LayoutStrategyType type) {
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
    /* Getters & Setters End */
}
