package de.tum.cit.aet.artemis.lecture.domain;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.annotation.Nullable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.Hibernate;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A Lecture.
 */
@Entity
@Table(name = "lecture")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Lecture extends DomainObject {

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "start_date")
    private ZonedDateTime startDate;

    @Column(name = "end_date")
    private ZonedDateTime endDate;

    /**
     * @deprecated This property is deprecated because it serves no practical purpose.
     *             Lecture contents (such as units and attachments) now have their own release dates,
     *             which control their visibility. There is no reason to hide when or if
     *             a lecture itself will occur.
     */
    @Deprecated
    @Column(name = "visible_date")
    private ZonedDateTime visibleDate;

    @OneToMany(mappedBy = "lecture", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnoreProperties(value = "lecture", allowSetters = true)
    private Set<Attachment> attachments = new HashSet<>();

    @OneToMany(mappedBy = "lecture", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lectureUnitOrder ASC") // DB → Java: always ordered by that column
    @JsonIgnoreProperties("lecture")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private List<LectureUnit> lectureUnits = new ArrayList<>();

    @ManyToOne
    @JsonIgnoreProperties(value = { "lectures", "exercises", "posts" }, allowSetters = true)
    private Course course;

    /**
     * Used for receiving the value from client.
     */
    @Transient
    private String channelNameTransient;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ZonedDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(ZonedDateTime startDate) {
        this.startDate = startDate;
    }

    public ZonedDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(ZonedDateTime endDate) {
        this.endDate = endDate;
    }

    public ZonedDateTime getVisibleDate() {
        return visibleDate;
    }

    /**
     * @param visibleDate the visibleDate to set for this lecture
     * @deprecated The visibleDate property of lectures is deprecated as it serves no practical purpose.
     *             Lecture contents (such as units and attachments) now have their own release dates,
     *             which control their visibility. There is no reason to hide when or if
     *             a lecture itself will occur.
     */
    @Deprecated
    public void setVisibleDate(ZonedDateTime visibleDate) {
        this.visibleDate = visibleDate;
    }

    public Set<Attachment> getAttachments() {
        return attachments;
    }

    public void addAttachments(Attachment attachment) {
        this.attachments.add(attachment);
        attachment.setLecture(this);
    }

    public void setAttachments(Set<Attachment> attachments) {
        this.attachments = attachments;
    }

    /**
     * Get an unmodifiable list of lecture units when the objects are initialized by Hibernate.
     * This is important so that external code does not modify the list without updating the back-references and order.
     *
     * @return the lecture units
     */
    public List<LectureUnit> getLectureUnits() {
        if (Hibernate.isInitialized(lectureUnits)) {
            return Collections.unmodifiableList(lectureUnits);
        }
        return lectureUnits;
    }

    public void reorderLectureUnits(List<Long> orderedIds) {
        lectureUnits.sort(Comparator.comparing(unit -> orderedIds.indexOf(unit.getId())));
        updateLectureUnitOrder();
    }

    public void setLectureUnits(List<LectureUnit> lectureUnits) {
        this.lectureUnits.clear();
        if (lectureUnits != null) {
            for (LectureUnit lectureUnit : lectureUnits) {
                addLectureUnit(lectureUnit);        // ensures back-reference and collection management
            }
        }
        updateLectureUnitOrder();
    }

    public void addLectureUnit(@Nullable LectureUnit lectureUnit) {
        if (lectureUnit == null) {
            return;
        }
        lectureUnits.add(lectureUnit);              // order is implicit by position
        lectureUnit.setLecture(this);
        updateLectureUnitOrder();
    }

    public void removeLectureUnit(@Nullable LectureUnit lectureUnit) {
        if (lectureUnit == null) {
            return;
        }
        lectureUnits.remove(lectureUnit);
        lectureUnit.setLecture(null);
        updateLectureUnitOrder();
    }

    public void removeLectureUnitById(@Nullable Long lectureUnitId) {
        if (lectureUnitId == null) {
            return;
        }

        // find the unit to remove
        LectureUnit toRemove = lectureUnits.stream().filter(unit -> unit != null && lectureUnitId.equals(unit.getId())).findFirst().orElse(null);

        removeLectureUnit(toRemove);
    }

    @PrePersist
    @PreUpdate
    public void updateLectureUnitOrder() {
        for (int i = 0; i < lectureUnits.size(); i++) {
            // or through package-private setter:
            lectureUnits.get(i).setLectureUnitOrder(i);
        }
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    @Override
    public String toString() {
        /* The visibleDate property of the Lecture entity is deprecated. We’re keeping the related logic temporarily to monitor for user feedback before full removal */
        /* TODO: #11479 - remove visibleDate from the string representation OR leave as is */
        return "Lecture{" + "id=" + getId() + ", title='" + getTitle() + "'" + ", description='" + getDescription() + "'" + ", visibleDate='" + getVisibleDate() + "'"
                + ", startDate='" + getStartDate() + "'" + ", endDate='" + getEndDate() + "'" + "}";
    }

    public String getChannelName() {
        return channelNameTransient;
    }

    public void setChannelName(String channelNameTransient) {
        this.channelNameTransient = channelNameTransient;
    }

    /**
     * check if students are allowed to see this lecture.
     *
     * @deprecated The visibleDate property of lectures is deprecated as it serves no practical purpose.
     *             Lecture contents (such as units and attachments) now have their own release dates,
     *             which control their visibility. There is no reason to hide when or if
     *             a lecture itself will occur.
     *
     * @return true, if students are allowed to see this lecture, otherwise false
     */
    @Deprecated
    public boolean isVisibleToStudents() {
        if (visibleDate == null) {  // no visible date means the lecture is visible to students
            return true;
        }
        return visibleDate.isBefore(ZonedDateTime.now());
    }
}
