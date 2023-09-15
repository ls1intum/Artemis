package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;
import java.util.*;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;

import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.metis.Post;

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

    @Column(name = "visible_date")
    private ZonedDateTime visibleDate;

    @OneToMany(mappedBy = "lecture", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonIgnoreProperties(value = "lecture", allowSetters = true)
    private Set<Attachment> attachments = new HashSet<>();

    @OneToMany(mappedBy = "lecture", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderColumn(name = "lecture_unit_order")
    @JsonIgnoreProperties("lecture")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private List<LectureUnit> lectureUnits = new ArrayList<>();

    @OneToMany(mappedBy = "lecture", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIncludeProperties({ "id" })
    private Set<Post> posts = new HashSet<>();

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

    public List<LectureUnit> getLectureUnits() {
        return lectureUnits;
    }

    public void setLectureUnits(List<LectureUnit> lectureUnits) {
        this.lectureUnits = lectureUnits;
    }

    public void addLectureUnit(LectureUnit lectureUnit) {
        this.lectureUnits.add(lectureUnit);
        lectureUnit.setLecture(this);
    }

    public Set<Post> getPosts() {
        return posts;
    }

    public void setPosts(Set<Post> posts) {
        this.posts = posts;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    @Override
    public String toString() {
        return "Lecture{" + "id=" + getId() + ", title='" + getTitle() + "'" + ", description='" + getDescription() + "'" + ", visibleDate='" + getVisibleDate() + "'"
                + ", startDate='" + getStartDate() + "'" + ", endDate='" + getEndDate() + "'" + "}";
    }

    public enum LectureSearchColumn {

        ID("id"), TITLE("title"), COURSE_TITLE("course.title"), SEMESTER("course.semester");

        private final String mappedColumnName;

        LectureSearchColumn(String mappedColumnName) {
            this.mappedColumnName = mappedColumnName;
        }

        public String getMappedColumnName() {
            return mappedColumnName;
        }
    }

    public String getChannelName() {
        return channelNameTransient;
    }

    public void setChannelName(String channelNameTransient) {
        this.channelNameTransient = channelNameTransient;
    }

    /**
     * check if students are allowed to see this lecture
     *
     * @return true, if students are allowed to see this lecture, otherwise false
     */
    public boolean isVisibleToStudents() {
        if (visibleDate == null) {  // no visible date means the lecture is visible to students
            return true;
        }
        return visibleDate.isBefore(ZonedDateTime.now());
    }
}
