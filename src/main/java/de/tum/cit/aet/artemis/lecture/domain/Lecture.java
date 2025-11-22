package de.tum.cit.aet.artemis.lecture.domain;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
import jakarta.validation.constraints.NotNull;

import org.hibernate.Hibernate;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    @Column(name = "is_tutorial_lecture")
    private boolean isTutorialLecture;

    @OneToMany(mappedBy = "lecture", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnoreProperties(value = "lecture", allowSetters = true)
    private Set<Attachment> attachments = new HashSet<>();

    /**
     * The lecture units of this lecture.
     * <p>
     * Note: We use a Set here to avoid issues with Hibernate and JPA when managing the collection.
     * A list without @OrderColumn is treated as Bag and would lead to issues when fetching data like lecture.lectureUnits.competencyLinks
     * A Set prevents this, a LinkedHashSet is used to maintain insertion order.
     * <p>
     * Note: We cannot use @OrderColumn here because this leads to issues when saving the lecture (there were ugly workarounds needed in the past) and could potentially lead to
     * null values in the order column which leads to unexpected behavior and unresolvable bugs in the user interface. This has happened in the past, therefore, we decided to
     * NOT rely on @OrderColumn anymore.
     * <p>
     * Instead, we manage the order manually via the lectureUnitOrder field in LectureUnit, other developers who use lecture and lectureUnit do not need to worry about it as
     * long as they use the provided methods to add/remove/reorder lecture units.
     *
     */
    @OneToMany(mappedBy = "lecture", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lectureUnitOrder ASC") // DB → Java: always ordered by that column
    @JsonIgnoreProperties("lecture")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<LectureUnit> lectureUnits = new LinkedHashSet<>();

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
     * <p>
     * Use {@link #addLectureUnit}, {@link #removeLectureUnit}, {@link #setLectureUnits}, or {@link #reorderLectureUnits} to modify the lecture units.
     *
     * @return the lecture units
     */
    public List<LectureUnit> getLectureUnits() {
        if (Hibernate.isInitialized(lectureUnits)) {
            return List.copyOf(lectureUnits);
        }
        // when not initialized, return an empty list to avoid LazyInitializationExceptions
        return List.of();
    }

    /**
     * Reorder the lecture units based on the given list of ordered IDs.
     * Makes sure to update the order after reordering.
     *
     * @param orderedIds the list of lecture unit IDs in the desired order
     */
    public void reorderLectureUnits(@NotNull List<Long> orderedIds) {
        List<LectureUnit> sorted = lectureUnits.stream().sorted(Comparator.comparing(unit -> orderedIds.indexOf(unit.getId()))).toList();
        lectureUnits.clear();
        lectureUnits.addAll(sorted);
        updateLectureUnitOrder();
    }

    /**
     * Set the lecture units, replacing any existing ones.
     * Makes sure to update back-references and order.
     *
     * @param lectureUnits the new list of lecture units
     */
    public void setLectureUnits(@Nullable List<LectureUnit> lectureUnits) {
        // this if statement is important to avoid issues when setting lazy loaded lectureUnits to null or empty which would not work in the else statement
        if (lectureUnits == null || lectureUnits.isEmpty()) {
            this.lectureUnits = new LinkedHashSet<>();
        }
        else {
            this.lectureUnits.clear();
            for (LectureUnit lectureUnit : lectureUnits) {
                addLectureUnit(lectureUnit);        // ensures back-reference and collection management
            }
            updateLectureUnitOrder();
        }
    }

    /**
     * Add a lecture unit to the end of the list.
     * Makes sure to update back-references and order.
     *
     * @param lectureUnit the lecture unit to add
     */
    public void addLectureUnit(@Nullable LectureUnit lectureUnit) {
        if (lectureUnit == null) {
            return;
        }
        lectureUnits.add(lectureUnit);              // order is implicit by position
        lectureUnit.setLecture(this);
        updateLectureUnitOrder();
    }

    /**
     * Remove a lecture unit from the list.
     * Makes sure to update back-references and order.
     *
     * @param lectureUnit the lecture unit to remove
     */
    public void removeLectureUnit(@Nullable LectureUnit lectureUnit) {
        if (lectureUnit == null) {
            return;
        }
        lectureUnits.remove(lectureUnit);
        lectureUnit.setLecture(null);
        updateLectureUnitOrder();
    }

    /**
     * Remove a lecture unit by its ID.
     * Makes sure to update back-references and order.
     *
     * @param lectureUnitId the ID of the lecture unit to remove
     */
    public void removeLectureUnitById(@Nullable Long lectureUnitId) {
        if (lectureUnitId == null) {
            return;
        }

        // find the unit to remove
        LectureUnit toRemove = lectureUnits.stream().filter(unit -> unit != null && lectureUnitId.equals(unit.getId())).findFirst().orElse(null);

        removeLectureUnit(toRemove);
    }

    /**
     * Update the lectureUnitOrder field of all lecture units based on their current position in the set.
     * This method is called before persisting or updating the Lecture entity to ensure consistency.
     * It can also be called manually after reordering the lecture units or adding/removing some.
     */
    @PrePersist
    @PreUpdate
    public void updateLectureUnitOrder() {
        if (Hibernate.isInitialized(lectureUnits)) {
            int order = 0;
            for (LectureUnit unit : lectureUnits) {
                if (unit == null) {
                    continue;
                }
                unit.setLectureUnitOrder(order++);
            }
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

    @JsonProperty("isTutorialLecture")
    public boolean isTutorialLecture() {
        return isTutorialLecture;
    }

    public void setIsTutorialLecture(boolean isTutorialLecture) {
        this.isTutorialLecture = isTutorialLecture;
    }
}
