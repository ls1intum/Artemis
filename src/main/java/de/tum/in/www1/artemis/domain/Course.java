package de.tum.in.www1.artemis.domain;

import static de.tum.in.www1.artemis.config.Constants.ARTEMIS_GROUP_DEFAULT_PREFIX;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.view.QuizView;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.FileService;

/**
 * A Course.
 */
@Entity
@Table(name = "course")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Course extends DomainObject {

    @Transient
    private transient FileService fileService = new FileService();

    @Transient
    private String prevCourseIcon;

    @Column(name = "title")
    @JsonView(QuizView.Before.class)
    private String title;

    @Column(name = "description")
    @JsonView(QuizView.Before.class)
    @Lob
    private String description;

    @Column(name = "short_name", unique = true)
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

    @Column(name = "semester")
    @JsonView(QuizView.Before.class)
    private String semester;

    @Column(name = "test_course")
    @JsonView(QuizView.Before.class)
    private boolean testCourse = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "language")
    @JsonView(QuizView.Before.class)
    private Language language;

    @Column(name = "online_course")
    @JsonView(QuizView.Before.class)
    private Boolean onlineCourse = false;

    @Column(name = "max_complaints")
    @JsonView(QuizView.Before.class)
    private Integer maxComplaints;

    @Column(name = "max_team_complaints")
    @JsonView(QuizView.Before.class)
    private Integer maxTeamComplaints;

    @Column(name = "max_complaint_time_days")
    @JsonView(QuizView.Before.class)
    private int maxComplaintTimeDays;

    @Column(name = "student_questions_enabled")
    @JsonView(QuizView.Before.class)
    private boolean studentQuestionsEnabled;

    @Column(name = "color")
    private String color;

    @Column(name = "course_icon")
    private String courseIcon;

    @Column(name = "registration_enabled")
    private Boolean registrationEnabled;

    @Column(name = "registration_confirmation_message")
    private String registrationConfirmationMessage;

    @Column(name = "presentation_score")
    private Integer presentationScore;

    @OneToMany(mappedBy = "course", fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("course")
    private Set<Exercise> exercises = new HashSet<>();

    @OneToMany(mappedBy = "course", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = "course", allowSetters = true)
    private Set<Lecture> lectures = new HashSet<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("course")
    private List<LearningGoal> learningGoals = new ArrayList<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("course")
    private Set<TutorGroup> tutorGroups = new HashSet<>();

    @OneToMany(mappedBy = "course", fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("course")
    private Set<Exam> exams = new HashSet<>();

    // NOTE: Helpers variable names must be different from Getter name, so that Jackson ignores the @Transient annotation, but Hibernate still respects it
    @Transient
    private Long numberOfInstructorsTransient;

    @Transient
    private Long numberOfTeachingAssistantsTransient;

    @Transient
    private Long numberOfStudentsTransient;

    public String getTitle() {
        return title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getStudentGroupName() {
        return studentGroupName;
    }

    public void setStudentGroupName(String studentGroupName) {
        this.studentGroupName = studentGroupName;
    }

    public String getTeachingAssistantGroupName() {
        return teachingAssistantGroupName;
    }

    public void setTeachingAssistantGroupName(String teachingAssistantGroupName) {
        this.teachingAssistantGroupName = teachingAssistantGroupName;
    }

    public String getInstructorGroupName() {
        return instructorGroupName;
    }

    public void setInstructorGroupName(String instructorGroupName) {
        this.instructorGroupName = instructorGroupName;
    }

    @JsonIgnore
    public String getDefaultStudentGroupName() {
        return ARTEMIS_GROUP_DEFAULT_PREFIX + getShortName() + "-students";
    }

    @JsonIgnore
    public String getDefaultTeachingAssistantGroupName() {
        return ARTEMIS_GROUP_DEFAULT_PREFIX + getShortName() + "-tutors";
    }

    @JsonIgnore
    public String getDefaultInstructorGroupName() {
        return ARTEMIS_GROUP_DEFAULT_PREFIX + getShortName() + "-instructors";
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

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public boolean isTestCourse() {
        return testCourse;
    }

    public void setTestCourse(boolean testCourse) {
        this.testCourse = testCourse;
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }

    public Boolean isOnlineCourse() {
        return onlineCourse == null ? false : onlineCourse;
    }

    public void setOnlineCourse(Boolean onlineCourse) {
        this.onlineCourse = onlineCourse;
    }

    public Integer getMaxComplaints() {
        return maxComplaints;
    }

    public void setMaxComplaints(Integer maxComplaints) {
        this.maxComplaints = maxComplaints;
    }

    public Integer getMaxTeamComplaints() {
        return maxTeamComplaints;
    }

    public void setMaxTeamComplaints(Integer maxTeamComplaints) {
        this.maxTeamComplaints = maxTeamComplaints;
    }

    public Integer getMaxComplaintTimeDays() {
        return maxComplaintTimeDays;
    }

    public void setMaxComplaintTimeDays(Integer maxComplaintTimeDays) {
        this.maxComplaintTimeDays = maxComplaintTimeDays;
    }

    public boolean getComplaintsEnabled() {
        return this.maxComplaints > 0 && this.maxComplaintTimeDays > 0;
    }

    public boolean getStudentQuestionsEnabled() {
        return studentQuestionsEnabled;
    }

    public void setStudentQuestionsEnabled(boolean studentQuestionsEnabled) {
        this.studentQuestionsEnabled = studentQuestionsEnabled;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getCourseIcon() {
        return courseIcon;
    }

    public void setCourseIcon(String courseIcon) {
        this.courseIcon = courseIcon;
    }

    public Boolean isRegistrationEnabled() {
        return registrationEnabled;
    }

    public void setRegistrationEnabled(Boolean registrationEnabled) {
        this.registrationEnabled = registrationEnabled;
    }

    public String getRegistrationConfirmationMessage() {
        return registrationConfirmationMessage;
    }

    public void setRegistrationConfirmationMessage(String registrationConfirmationMessage) {
        this.registrationConfirmationMessage = registrationConfirmationMessage;
    }

    public Integer getPresentationScore() {
        return presentationScore;
    }

    public void setPresentationScore(Integer presentationScore) {
        this.presentationScore = presentationScore;
    }

    public Set<Exercise> getExercises() {
        return exercises;
    }

    public Course addExercises(Exercise exercise) {
        this.exercises.add(exercise);
        exercise.setCourse(this);
        return this;
    }

    public void setExercises(Set<Exercise> exercises) {
        this.exercises = exercises;
    }

    public Set<Lecture> getLectures() {
        return lectures;
    }

    public Course addLectures(Lecture lecture) {
        this.lectures.add(lecture);
        lecture.setCourse(this);
        return this;
    }

    public void setLectures(Set<Lecture> lectures) {
        this.lectures = lectures;
    }

    public Set<TutorGroup> getTutorGroups() {
        return tutorGroups;
    }

    public void setTutorGroups(Set<TutorGroup> tutorGroups) {
        this.tutorGroups = tutorGroups;
    }

    public Set<Exam> getExams() {
        return exams;
    }

    public void setExams(Set<Exam> exams) {
        this.exams = exams;
    }

    public void addExam(Exam exam) {
        this.exams.add(exam);
        if (exam.getCourse() != this) {
            exam.setCourse(this);
        }
    }
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
     * Initialisation of the Course on Server start
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
        courseIcon = fileService.manageFilesForUpdatedFilePath(prevCourseIcon, courseIcon, FilePathService.getCourseIconFilepath(), getId());
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
        courseIcon = fileService.manageFilesForUpdatedFilePath(prevCourseIcon, courseIcon, FilePathService.getCourseIconFilepath(), getId());
    }

    @PostRemove
    public void onDelete() {
        // delete old file if necessary
        fileService.manageFilesForUpdatedFilePath(prevCourseIcon, null, FilePathService.getCourseIconFilepath(), getId());
    }

    @Override
    public String toString() {
        return "Course{" + "id=" + getId() + ", title='" + getTitle() + "'" + ", description='" + getDescription() + "'" + ", shortName='" + getShortName() + "'"
                + ", studentGroupName='" + getStudentGroupName() + "'" + ", teachingAssistantGroupName='" + getTeachingAssistantGroupName() + "'" + ", instructorGroupName='"
                + getInstructorGroupName() + "'" + ", startDate='" + getStartDate() + "'" + ", endDate='" + getEndDate() + "'" + ", semester='" + getSemester() + "'" + "'"
                + ", onlineCourse='" + isOnlineCourse() + "'" + ", color='" + getColor() + "'" + ", courseIcon='" + getCourseIcon() + "'" + ", registrationEnabled='"
                + isRegistrationEnabled() + "'" + "'" + ", presentationScore='" + getPresentationScore() + "}";
    }

    public void setNumberOfInstructors(Long numberOfInstructors) {
        this.numberOfInstructorsTransient = numberOfInstructors;
    }

    public void setNumberOfTeachingAssistants(Long numberOfTeachingAssistants) {
        this.numberOfTeachingAssistantsTransient = numberOfTeachingAssistants;
    }

    public void setNumberOfStudents(Long numberOfStudents) {
        this.numberOfStudentsTransient = numberOfStudents;
    }

    public Long getNumberOfInstructors() {
        return this.numberOfInstructorsTransient;
    }

    public Long getNumberOfTeachingAssistants() {
        return this.numberOfTeachingAssistantsTransient;
    }

    public Long getNumberOfStudents() {
        return this.numberOfStudentsTransient;
    }

    public List<LearningGoal> getLearningGoals() {
        return learningGoals;
    }

    public void setLearningGoals(List<LearningGoal> learningGoals) {
        this.learningGoals = learningGoals;
    }

    public void addLearningGoal(LearningGoal learningGoal) {
        this.learningGoals.add(learningGoal);
        learningGoal.setCourse(this);
    }

    public void removeLearningGoal(LearningGoal learningGoal) {
        this.learningGoals.remove(learningGoal);
        learningGoal.setCourse(null);
    }
}
