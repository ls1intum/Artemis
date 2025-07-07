package de.tum.cit.aet.artemis.exam.domain.room;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Table(name = "exam_room")
public class ExamRoom extends DomainObject {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "alternative_name", nullable = true)
    private String alternativeName; // JSON filename

    @Column(name = "capacity", nullable = true)
    private Integer capacity;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties(value = "examRoom")
    private List<ExamSeat> seats = new ArrayList<>();

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties(value = "examRoom")
    private List<LayoutStrategy> layoutStrategies = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAlternativeName() {
        return alternativeName;
    }

    public void setAlternativeName(String alternativeName) {
        this.alternativeName = alternativeName;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public List<ExamSeat> getSeats() {
        return seats;
    }

    public void setSeats(List<ExamSeat> seats) {
        this.seats = seats;
    }

    public List<LayoutStrategy> getLayoutStrategies() {
        return layoutStrategies;
    }

    public void setLayoutStrategies(List<LayoutStrategy> layoutStrategies) {
        this.layoutStrategies = layoutStrategies;
    }

}
