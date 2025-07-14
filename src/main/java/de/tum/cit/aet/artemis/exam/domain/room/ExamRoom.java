package de.tum.cit.aet.artemis.exam.domain.room;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.context.annotation.Conditional;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;

@Conditional(ExamEnabled.class)
@Entity
@Table(name = "exam_room")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExamRoom extends DomainObject {

    /**
     * The verbose room number.
     */
    @Column(name = "long_room_number", nullable = false)
    private String longRoomNumber;

    /**
     * The short room number.
     */
    @Column(name = "short_room_number", nullable = false)
    private String shortRoomNumber;

    /**
     * The name of the exam room.
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * An alternative name for the exam room, doesn't need to exist.
     */
    @Column(name = "alternative_name", nullable = true)
    private String alternativeName;

    /**
     * The building where the exam room resides inside.
     */
    @Column(name = "building", nullable = false)
    private String building;

    /**
     * Maximum capacity of the room. Doesn't need to exist, i.e., be pre-calculated.
     */
    @Column(name = "capacity", nullable = true)
    private Integer capacity;

    /**
     * All seats of this exam room.
     */
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonManagedReference
    private List<ExamSeat> seats = new ArrayList<>();

    /**
     * All layout strategies for this exam room.
     */
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonManagedReference
    private List<LayoutStrategy> layoutStrategies = new ArrayList<>();

    /**
     * All exam users that sit in this room for any exam.
     */
    @OneToMany(mappedBy = "plannedRoomEntity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonManagedReference
    private Set<ExamUser> examRoomUsers = new HashSet<>();

    /* Getters & Setters */
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

    public String getLongRoomNumber() {
        return longRoomNumber;
    }

    public void setLongRoomNumber(String longRoomNumber) {
        this.longRoomNumber = longRoomNumber;
    }

    public String getShortRoomNumber() {
        return shortRoomNumber;
    }

    public void setShortRoomNumber(String shortRoomNumber) {
        this.shortRoomNumber = shortRoomNumber;
    }

    public String getBuilding() {
        return building;
    }

    public void setBuilding(String building) {
        this.building = building;
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

    public Set<ExamUser> getExamRoomUsers() {
        return examRoomUsers;
    }

    public void setExamRoomUsers(Set<ExamUser> examRoomUsers) {
        this.examRoomUsers = examRoomUsers;
    }
    /* Getters & Setters End */

}
