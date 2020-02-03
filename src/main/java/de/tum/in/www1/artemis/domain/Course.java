package de.tum.in.www1.artemis.domain;

import static de.tum.in.www1.artemis.domain.enumeration.AssessmentType.MANUAL;
import static de.tum.in.www1.artemis.domain.enumeration.AssessmentType.SEMI_AUTOMATIC;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.view.QuizView;
import de.tum.in.www1.artemis.service.FileService;

/**
 * A Course.
 */
@Entity
@Table(name = "course")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Course implements Serializable {

    private static final long serialVersionUID = 1L;

    @Transient
    private FileService fileService = new FileService();

    @Transient
    private String prevCourseIcon;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonView(QuizView.Before.class)
    private Long id;

    @Column(name = "title")
    @JsonView(QuizView.Before.class)
    private String title;

    @Column(name = "description")
    @JsonView(QuizView.Before.class)
    @Lob
    private String description;

    @Column(name = "short_name")
    @JsonView(QuizView.Before.class)
    private String shortName;

    @Column(name = "student_group_name")
    @JsonView(QuizView.Before.class)
    private String studentGroupName;

    @Column(name = "teaching_assistant_group_name")
    @JsonView(QuizView.Before.class)
    private String teachingAssistantGroupName;

    @Column(name = "instructor_group_name")
    @JsonView(QuizView.Before.class)
    private String instructorGroupName;

    @Column(name = "start_date")
    @JsonView(QuizView.Before.class)
    private ZonedDateTime startDate;

    @Column(name = "end_date")
    @JsonView(QuizView.Before.class)
    private ZonedDateTime endDate;

    @Column(name = "online_course")
    @JsonView(QuizView.Before.class)
    private Boolean onlineCourse = false;

    @Column(name = "max_complaints")
    @JsonView(QuizView.Before.class)
    private Integer maxComplaints;

    @Column(name = "color")
    private String color;

    @Column(name = "course_icon")
    private String courseIcon;

    @Column(name = "registration_enabled")
    private Boolean registrationEnabled;

    @Column(name = "presentation_score")
    private Integer presentationScore;

    @OneToMany(mappedBy = "course", fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("course")
    private Set<Exercise> exercises = new HashSet<>();

    @OneToMany(mappedBy = "course", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = "course", allowSetters = true)
    private Set<Lecture> lectures = new HashSet<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("course")
    private Set<TutorGroup> tutorGroups = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public Course title(String title) {
        this.title = title;
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public Course description(String description) {
        this.description = description;
        return this;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getShortName() {
        return shortName;
    }

    public Course shortName(String shortName) {
        this.shortName = shortName;
        return this;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getStudentGroupName() {
        return studentGroupName;
    }

    public Course studentGroupName(String studentGroupName) {
        this.studentGroupName = studentGroupName;
        return this;
    }

    public void setStudentGroupName(String studentGroupName) {
        this.studentGroupName = studentGroupName;
    }

    public String getTeachingAssistantGroupName() {
        return teachingAssistantGroupName;
    }

    public Course teachingAssistantGroupName(String teachingAssistantGroupName) {
        this.teachingAssistantGroupName = teachingAssistantGroupName;
        return this;
    }

    public void setTeachingAssistantGroupName(String teachingAssistantGroupName) {
        this.teachingAssistantGroupName = teachingAssistantGroupName;
    }

    public String getInstructorGroupName() {
        return instructorGroupName;
    }

    public Course instructorGroupName(String instructorGroupName) {
        this.instructorGroupName = instructorGroupName;
        return this;
    }

    public void setInstructorGroupName(String instructorGroupName) {
        this.instructorGroupName = instructorGroupName;
    }

    public ZonedDateTime getStartDate() {
        return startDate;
    }

    public Course startDate(ZonedDateTime startDate) {
        this.startDate = startDate;
        return this;
    }

    public void setStartDate(ZonedDateTime startDate) {
        this.startDate = startDate;
    }

    public ZonedDateTime getEndDate() {
        return endDate;
    }

    public Course endDate(ZonedDateTime endDate) {
        this.endDate = endDate;
        return this;
    }

    public void setEndDate(ZonedDateTime endDate) {
        this.endDate = endDate;
    }

    public Boolean isOnlineCourse() {
        return onlineCourse == null ? false : onlineCourse;
    }

    public Course onlineCourse(Boolean onlineCourse) {
        this.onlineCourse = onlineCourse;
        return this;
    }

    public void setOnlineCourse(Boolean onlineCourse) {
        this.onlineCourse = onlineCourse;
    }

    public Integer getMaxComplaints() {
        return maxComplaints;
    }

    public Course maxComplaints(Integer maxComplaints) {
        this.maxComplaints = maxComplaints;
        return this;
    }

    public void setMaxComplaints(Integer maxComplaints) {
        this.maxComplaints = maxComplaints;
    }

    public String getColor() {
        return color;
    }

    public Course color(String color) {
        this.color = color;
        return this;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getCourseIcon() {
        return courseIcon;
    }

    public Course courseIcon(String courseIcon) {
        this.courseIcon = courseIcon;
        return this;
    }

    public void setCourseIcon(String courseIcon) {
        this.courseIcon = courseIcon;
    }

    public Boolean isRegistrationEnabled() {
        return registrationEnabled;
    }

    public Course registrationEnabled(Boolean registrationEnabled) {
        this.registrationEnabled = registrationEnabled;
        return this;
    }

    public void setRegistrationEnabled(Boolean registrationEnabled) {
        this.registrationEnabled = registrationEnabled;
    }

    public Integer getPresentationScore() {
        return presentationScore;
    }

    public Course presentationScore(Integer presentationScore) {
        this.presentationScore = presentationScore;
        return this;
    }

    public void setPresentationScore(Integer presentationScore) {
        this.presentationScore = presentationScore;
    }

    public Set<Exercise> getExercises() {
        return exercises;
    }

    public Course exercises(Set<Exercise> exercises) {
        this.exercises = exercises;
        return this;
    }

    public Course addExercises(Exercise exercise) {
        this.exercises.add(exercise);
        exercise.setCourse(this);
        return this;
    }

    public Course removeExercises(Exercise exercise) {
        this.exercises.remove(exercise);
        exercise.setCourse(null);
        return this;
    }

    public void setExercises(Set<Exercise> exercises) {
        this.exercises = exercises;
    }

    public Set<Lecture> getLectures() {
        return lectures;
    }

    public Course lectures(Set<Lecture> lectures) {
        this.lectures = lectures;
        return this;
    }

    public Course addLectures(Lecture lecture) {
        this.lectures.add(lecture);
        lecture.setCourse(this);
        return this;
    }

    public Course removeLectures(Lecture lecture) {
        this.lectures.remove(lecture);
        lecture.setCourse(null);
        return this;
    }

    public void setLectures(Set<Lecture> lectures) {
        this.lectures = lectures;
    }

    public Set<TutorGroup> getTutorGroups() {
        return tutorGroups;
    }

    public Course tutorGroups(Set<TutorGroup> tutorGroups) {
        this.tutorGroups = tutorGroups;
        return this;
    }

    public Course addTutorGroups(TutorGroup tutorGroup) {
        this.tutorGroups.add(tutorGroup);
        tutorGroup.setCourse(this);
        return this;
    }

    public Course removeTutorGroups(TutorGroup tutorGroup) {
        this.tutorGroups.remove(tutorGroup);
        tutorGroup.setCourse(null);
        return this;
    }

    public void setTutorGroups(Set<TutorGroup> tutorGroups) {
        this.tutorGroups = tutorGroups;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    /*
     * NOTE: The file management is necessary to differentiate between temporary and used files and to delete used files when the corresponding course is deleted or it is replaced
     * by another file. The workflow is as follows 1. user uploads a file -> this is a temporary file, because at this point the corresponding course might not exist yet. 2. user
     * saves the course -> now we move the temporary file which is addressed in courseIcon to a permanent location and update the value in courseIcon accordingly. => This happens
     * in @PrePersist and @PostPersist 3. user might upload another file to replace the existing file -> this new file is a temporary file at first 4. user saves changes (with the
     * new courseIcon pointing to the new temporary file) -> now we delete the old file in the permanent location and move the new file to a permanent location and update the value
     * in courseIcon accordingly. => This happens in @PreUpdate and uses @PostLoad to know the old path 5. When course is deleted, the file in the permanent location is deleted =>
     * This happens in @PostRemove
     */

    /**
     *Initialisation of the Course on Server start
     */
    @PostLoad
    public void onLoad() {
        // replace placeholder with actual id if necessary (this is needed because changes made in afterCreate() are not persisted)
        if (courseIcon != null && courseIcon.contains(Constants.FILEPATH_ID_PLACHEOLDER)) {
            courseIcon = courseIcon.replace(Constants.FILEPATH_ID_PLACHEOLDER, getId().toString());
        }
        prevCourseIcon = courseIcon; // save current path as old path (needed to know old path in onUpdate() and onDelete())
    }

    @PrePersist
    public void beforeCreate() {
        // move file if necessary (id at this point will be null, so placeholder will be inserted)
        courseIcon = fileService.manageFilesForUpdatedFilePath(prevCourseIcon, courseIcon, Constants.COURSE_ICON_FILEPATH, getId());
    }

    @PostPersist
    public void afterCreate() {
        // replace placeholder with actual id if necessary (id is no longer null at this point)
        if (courseIcon != null && courseIcon.contains(Constants.FILEPATH_ID_PLACHEOLDER)) {
            courseIcon = courseIcon.replace(Constants.FILEPATH_ID_PLACHEOLDER, getId().toString());
        }
    }

    @PreUpdate
    public void onUpdate() {
        // move file and delete old file if necessary
        courseIcon = fileService.manageFilesForUpdatedFilePath(prevCourseIcon, courseIcon, Constants.COURSE_ICON_FILEPATH, getId());
    }

    @PostRemove
    public void onDelete() {
        // delete old file if necessary
        fileService.manageFilesForUpdatedFilePath(prevCourseIcon, null, Constants.COURSE_ICON_FILEPATH, getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Course course = (Course) o;
        if (course.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), course.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "Course{" + "id=" + getId() + ", title='" + getTitle() + "'" + ", description='" + getDescription() + "'" + ", shortName='" + getShortName() + "'"
                + ", studentGroupName='" + getStudentGroupName() + "'" + ", teachingAssistantGroupName='" + getTeachingAssistantGroupName() + "'" + ", instructorGroupName='"
                + getInstructorGroupName() + "'" + ", startDate='" + getStartDate() + "'" + ", endDate='" + getEndDate() + "'" + ", onlineCourse='" + isOnlineCourse() + "'"
                + ", color='" + getColor() + "'" + ", courseIcon='" + getCourseIcon() + "'" + ", registrationEnabled='" + isRegistrationEnabled() + "'" + "'"
                + ", presentationScore='" + getPresentationScore() + "}";
    }

    @JsonIgnore
    public Set<Exercise> getInterestingExercisesForAssessmentDashboards() {
        return getExercises().stream()
                .filter(exercise -> exercise instanceof TextExercise || exercise instanceof ModelingExercise || exercise instanceof FileUploadExercise
                        || (exercise instanceof ProgrammingExercise && (exercise.getAssessmentType().equals(SEMI_AUTOMATIC) || exercise.getAssessmentType().equals(MANUAL))))
                .collect(Collectors.toSet());
    }
}
