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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.context.annotation.Conditional;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import de.tum.cit.aet.artemis.core.domain.AbstractAuditingEntity;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.dto.room.ExamSeatDTO;

@Conditional(ExamEnabled.class)
@Entity
@Table(name = "exam_room")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExamRoom extends AbstractAuditingEntity {

    /**
     * The room number, e.g., '123.EG.01' or '123.456.78.9'. This is a room's unique identifier.
     */
    @Column(name = "room_number", nullable = false, length = 255, unique = true)
    private String roomNumber;

    /**
     * An alternative room number, if it exists, e.g., '00.02.001' or 'BC2 0.01.17@8102'.
     * <p/>
     * Used to improve auto-complete.
     */
    @Column(name = "alternative_room_number", nullable = true, length = 255)
    private String alternativeRoomNumber;

    /**
     * The name of the exam room, e.g. 'Wilhelm-Nusselt-Hörsaal' or 'Friedrich L. Bauer Hörsaal"
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * An alternative name for the exam room, if it exists, e.g., 'N1179' or 'HS1'.
     * <p/>
     * Used to improve auto-complete.
     */
    @Column(name = "alternative_name", nullable = true, length = 255)
    private String alternativeName;

    /**
     * The building where the exam room resides inside, e.g., 'N1', 'Z1', or 'Galileo'.
     */
    @Column(name = "building", nullable = false, length = 255)
    private String building;

    /**
     * All seats of this exam room.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exam_seats", columnDefinition = "json", nullable = false)
    private List<ExamSeatDTO> seats = new ArrayList<>();

    /**
     * All layout strategies for this exam room.
     * <p/>
     * A layout strategy describes how students can be distributed throughout this exam room.
     */
    @OneToMany(mappedBy = "examRoom", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonManagedReference
    private List<LayoutStrategy> layoutStrategies = new ArrayList<>();

    /**
     * All exams this exam room is used in.
     */
    @OneToMany(mappedBy = "examRoom", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonManagedReference("examRoomExamAssignments_room")
    private Set<ExamRoomExamAssignment> examRoomExamAssignments = new HashSet<>();

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

    public List<ExamSeatDTO> getSeats() {
        return seats;
    }

    public void setSeats(List<ExamSeatDTO> seats) {
        this.seats = seats;
    }

    public List<LayoutStrategy> getLayoutStrategies() {
        return layoutStrategies;
    }

    public void setLayoutStrategies(List<LayoutStrategy> layoutStrategies) {
        this.layoutStrategies = layoutStrategies;
    }

    public Set<ExamRoomExamAssignment> getExamRoomAssignments() {
        return examRoomExamAssignments;
    }

    public void setExamRoomAssignments(Set<ExamRoomExamAssignment> examRoomExamAssignments) {
        this.examRoomExamAssignments = examRoomExamAssignments;
    }

}
