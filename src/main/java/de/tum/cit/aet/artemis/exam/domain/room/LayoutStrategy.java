package de.tum.cit.aet.artemis.exam.domain.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.context.annotation.Conditional;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;

@Conditional(ExamEnabled.class)
@Entity
@Table(name = "layout_strategy")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LayoutStrategy extends DomainObject {

    /**
     * The name of the layout strategy. Can be anything, but common ones are "default", "wide", and "corona"
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * The type of this layout strategy. May be one of {@link LayoutStrategyType}.
     */
    @Enumerated(EnumType.STRING)  // for human readability in the DB
    @Column(name = "type", nullable = false, length = 50)
    private LayoutStrategyType type;

    /**
     * The capacity of this layout strategy, i.e., how many students can be at max seated using this strategy.
     */
    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    /**
     * The room this layout strategy belongs to. One room may have multiple layout strategies.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_room_id", nullable = false)
    @JsonBackReference
    private ExamRoom examRoom;

    /**
     * The parameters of the layout strategy, i.e., the data that tells the strategy how to distribute the students.
     * Contents of this differ as the {@link LayoutStrategy#type} differs.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "json", nullable = false)
    private String parametersJson;

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

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public ExamRoom getExamRoom() {
        return examRoom;
    }

    public void setExamRoom(ExamRoom room) {
        this.examRoom = room;
    }

    public String getParametersJson() {
        return parametersJson;
    }

    public void setParametersJson(String parametersJson) {
        this.parametersJson = parametersJson;
    }
}
