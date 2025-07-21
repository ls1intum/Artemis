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

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import de.tum.cit.aet.artemis.core.domain.AbstractAuditingEntity;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;

@Conditional(ExamEnabled.class)
@Entity
@Table(name = "exam_room")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExamRoom extends AbstractAuditingEntity {

    /**
     * The room number, e.g. 123.EG.01, or 123.456.78.9
     */
    @Column(name = "room_number", nullable = false, length = 50)
    private String roomNumber;

    /**
     * An alternative room number, if it exists. Used to improve auto-complete.
     */
    @Column(name = "alternative_room_number", nullable = true, length = 50)
    private String alternativeRoomNumber;

    /**
     * The name of the exam room.
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * An alternative name for the exam room, doesn't need to exist. Used to improve auto-complete.
     */
    @Column(name = "alternative_name", nullable = true, length = 255)
    private String alternativeName;

    /**
     * The building where the exam room resides inside.
     */
    @Column(name = "building", nullable = false, length = 255)
    private String building;

    /**
     * All seats of this exam room.
     */
    @OneToMany(mappedBy = "examRoom", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonManagedReference
    private List<ExamSeat> seats = new ArrayList<>();

    /**
     * All layout strategies for this exam room.
     */
    @OneToMany(mappedBy = "examRoom", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
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

    @OneToMany(mappedBy = "examRoom", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonBackReference
    private Set<ExamRoomAssignment> examRoomAssignments = new HashSet<>();

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

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String longRoomNumber) {
        this.roomNumber = longRoomNumber;
    }

    public String getAlternativeRoomNumber() {
        return alternativeRoomNumber;
    }

    public void setAlternativeRoomNumber(String shortRoomNumber) {
        this.alternativeRoomNumber = shortRoomNumber;
    }

    public String getBuilding() {
        return building;
    }

    public void setBuilding(String building) {
        this.building = building;
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

    public Set<ExamRoomAssignment> getExamRoomAssignments() {
        return examRoomAssignments;
    }

    public void setExamRoomAssignments(Set<ExamRoomAssignment> examRoomAssignments) {
        this.examRoomAssignments = examRoomAssignments;
    }

    /* Getters & Setters End */

}
