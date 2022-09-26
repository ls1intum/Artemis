package de.tum.in.www1.artemis.domain;

import static de.tum.in.www1.artemis.config.Constants.ARTEMIS_GROUP_DEFAULT_PREFIX;
import static de.tum.in.www1.artemis.config.Constants.COMPLAINT_RESPONSE_TEXT_LIMIT;
import static de.tum.in.www1.artemis.config.Constants.COMPLAINT_TEXT_LIMIT;
import static de.tum.in.www1.artemis.config.Constants.SHORT_NAME_PATTERN;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.view.QuizView;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

/**
 * A Course.
 */
@Entity
@Table(name = "course")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Course extends DomainObject {

    public static final String ENTITY_NAME = "course";

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

    @Column(name = "editor_group_name")
    @JsonView(QuizView.Before.class)
    private String editorGroupName;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "default_programming_language")
    @JsonView(QuizView.Before.class)
    private ProgrammingLanguage defaultProgrammingLanguage;

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

    @Column(name = "max_complaint_text_limit")
    @JsonView(QuizView.Before.class)
    private int maxComplaintTextLimit;

    @Column(name = "max_complaint_response_text_limit")
    @JsonView(QuizView.Before.class)
    private int maxComplaintResponseTextLimit;

    @Column(name = "posts_enabled")
    @JsonView(QuizView.Before.class)
    private boolean postsEnabled;

    @OneToMany(mappedBy = "course", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("course")
    private Set<Post> posts = new HashSet<>();

    @Column(name = "max_request_more_feedback_time_days")
    @JsonView(QuizView.Before.class)
    private int maxRequestMoreFeedbackTimeDays;

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

    @Column(name = "course_archive_path")
    private String courseArchivePath;

    @Column(name = "max_points")
    private Integer maxPoints;

    @Column(name = "accuracy_of_scores")
    @JsonView(QuizView.Before.class)
    private Integer accuracyOfScores;

    @OneToMany(mappedBy = "course", fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("course")
    private Set<Exercise> exercises = new HashSet<>();

    @OneToMany(mappedBy = "course", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = "course", allowSetters = true)
    private Set<Lecture> lectures = new HashSet<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("course")
    @OrderBy("title")
    private Set<LearningGoal> learningGoals = new HashSet<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = "course", allowSetters = true)
    @OrderBy("title")
    private Set<TutorialGroup> tutorialGroups = new HashSet<>();

    @OneToMany(mappedBy = "course", fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("course")
    private Set<Exam> exams = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "course_organization", joinColumns = { @JoinColumn(name = "course_id", referencedColumnName = "id") }, inverseJoinColumns = {
            @JoinColumn(name = "organization_id", referencedColumnName = "id") })
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("course")
    private Set<Organization> organizations = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "learning_goal_course", joinColumns = @JoinColumn(name = "course_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "learning_goal_id", referencedColumnName = "id"))
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("consecutiveCourses")
    private Set<LearningGoal> prerequisites = new HashSet<>();

    // NOTE: Helpers variable names must be different from Getter name, so that Jackson ignores the @Transient annotation, but Hibernate still respects it
    @Transient
    private Long numberOfInstructorsTransient;

    @Transient
    private Long numberOfEditorsTransient;

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

    public String getEditorGroupName() {
        return editorGroupName;
    }

    public void setEditorGroupName(String editorGroupName) {
        this.editorGroupName = editorGroupName;
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
    public String getDefaultEditorGroupName() {
        return ARTEMIS_GROUP_DEFAULT_PREFIX + getShortName() + "-editors";
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

    public ProgrammingLanguage getDefaultProgrammingLanguage() {
        return defaultProgrammingLanguage;
    }

    public void setDefaultProgrammingLanguage(ProgrammingLanguage defaultProgrammingLanguage) {
        this.defaultProgrammingLanguage = defaultProgrammingLanguage;
    }

    public Boolean isOnlineCourse() {
        return Boolean.TRUE.equals(onlineCourse);
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

    public int getMaxComplaintTimeDays() {
        return maxComplaintTimeDays;
    }

    public void setMaxComplaintTimeDays(int maxComplaintTimeDays) {
        this.maxComplaintTimeDays = maxComplaintTimeDays;
    }

    public int getMaxComplaintTextLimit() {
        return maxComplaintTextLimit;
    }

    public void setMaxComplaintTextLimit(int maxComplaintTextLimit) {
        this.maxComplaintTextLimit = maxComplaintTextLimit;
    }

    public int getMaxComplaintResponseTextLimit() {
        return maxComplaintResponseTextLimit;
    }

    public void setMaxComplaintResponseTextLimit(int maxComplaintResponseTextLimit) {
        this.maxComplaintResponseTextLimit = maxComplaintResponseTextLimit;
    }

    public boolean getComplaintsEnabled() {
        // maxComplaintTimeDays must be larger than zero,
        // and then either maxComplaints, maxTeamComplaints is larger than zero
        // See CourseResource for more details on the validation
        return this.maxComplaintTimeDays > 0;
    }

    public boolean getPostsEnabled() {
        return postsEnabled;
    }

    public void setPostsEnabled(boolean postsEnabled) {
        this.postsEnabled = postsEnabled;
    }

    public Set<Post> getPosts() {
        return posts;
    }

    public void setPosts(Set<Post> posts) {
        this.posts = posts;
    }

    public boolean getRequestMoreFeedbackEnabled() {
        return maxRequestMoreFeedbackTimeDays > 0;
    }

    public int getMaxRequestMoreFeedbackTimeDays() {
        return maxRequestMoreFeedbackTimeDays;
    }

    public void setMaxRequestMoreFeedbackTimeDays(int maxRequestMoreFeedbackTimeDays) {
        this.maxRequestMoreFeedbackTimeDays = maxRequestMoreFeedbackTimeDays;
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

    public void addLectures(Lecture lecture) {
        this.lectures.add(lecture);
        lecture.setCourse(this);
    }

    public void setLectures(Set<Lecture> lectures) {
        this.lectures = lectures;
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

    public Set<Organization> getOrganizations() {
        return organizations;
    }

    public void setOrganizations(Set<Organization> organizations) {
        this.organizations = organizations;
    }

    public Set<LearningGoal> getPrerequisites() {
        return prerequisites;
    }

    public void setPrerequisites(Set<LearningGoal> prerequisites) {
        this.prerequisites = prerequisites;
    }

    public void addPrerequisite(LearningGoal learningGoal) {
        this.prerequisites.add(learningGoal);
        learningGoal.getConsecutiveCourses().add(this);
    }

    public void removePrerequisite(LearningGoal learningGoal) {
        this.prerequisites.remove(learningGoal);
        learningGoal.getConsecutiveCourses().remove(this);
    }

    /*
     * NOTE: The file management is necessary to differentiate between temporary and used files and to delete used files when the corresponding course is deleted, or it is replaced
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
        if (courseIcon != null && courseIcon.contains(Constants.FILEPATH_ID_PLACEHOLDER)) {
            courseIcon = courseIcon.replace(Constants.FILEPATH_ID_PLACEHOLDER, getId().toString());
        }
        prevCourseIcon = courseIcon; // save current path as old path (needed to know old path in onUpdate() and onDelete())
    }

    @PrePersist
    public void beforeCreate() {
        // move file if necessary (id at this point will be null, so placeholder will be inserted)
        courseIcon = fileService.manageFilesForUpdatedFilePath(prevCourseIcon, courseIcon, FilePathService.getCourseIconFilePath(), getId());
    }

    @PostPersist
    public void afterCreate() {
        // replace placeholder with actual id if necessary (id is no longer null at this point)
        if (courseIcon != null && courseIcon.contains(Constants.FILEPATH_ID_PLACEHOLDER)) {
            courseIcon = courseIcon.replace(Constants.FILEPATH_ID_PLACEHOLDER, getId().toString());
        }
    }

    @PreUpdate
    public void onUpdate() {
        // move file and delete old file if necessary
        courseIcon = fileService.manageFilesForUpdatedFilePath(prevCourseIcon, courseIcon, FilePathService.getCourseIconFilePath(), getId());
    }

    @PostRemove
    public void onDelete() {
        // delete old file if necessary
        fileService.manageFilesForUpdatedFilePath(prevCourseIcon, null, FilePathService.getCourseIconFilePath(), getId());
    }

    @Override
    public String toString() {
        return "Course{" + "id=" + getId() + ", title='" + getTitle() + "'" + ", description='" + getDescription() + "'" + ", shortName='" + getShortName() + "'"
                + ", studentGroupName='" + getStudentGroupName() + "'" + ", teachingAssistantGroupName='" + getTeachingAssistantGroupName() + "'" + ", editorGroupName='"
                + getEditorGroupName() + "'" + ", instructorGroupName='" + getInstructorGroupName() + "'" + ", startDate='" + getStartDate() + "'" + ", endDate='" + getEndDate()
                + "'" + ", semester='" + getSemester() + "'" + "'" + ", onlineCourse='" + isOnlineCourse() + "'" + ", color='" + getColor() + "'" + ", courseIcon='"
                + getCourseIcon() + "'" + ", registrationEnabled='" + isRegistrationEnabled() + "'" + "'" + ", presentationScore='" + getPresentationScore() + "}";
    }

    public void setNumberOfInstructors(Long numberOfInstructors) {
        this.numberOfInstructorsTransient = numberOfInstructors;
    }

    public void setNumberOfEditors(Long numberOfEditors) {
        this.numberOfEditorsTransient = numberOfEditors;
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

    public Long getNumberOfEditors() {
        return this.numberOfEditorsTransient;
    }

    public Long getNumberOfTeachingAssistants() {
        return this.numberOfTeachingAssistantsTransient;
    }

    public Long getNumberOfStudents() {
        return this.numberOfStudentsTransient;
    }

    public Set<LearningGoal> getLearningGoals() {
        return learningGoals;
    }

    public void setLearningGoals(Set<LearningGoal> learningGoals) {
        this.learningGoals = learningGoals;
    }

    public boolean hasCourseArchive() {
        return courseArchivePath != null && !courseArchivePath.isEmpty();
    }

    public String getCourseArchivePath() {
        return courseArchivePath;
    }

    public void setCourseArchivePath(String courseArchiveUrl) {
        this.courseArchivePath = courseArchiveUrl;
    }

    public Integer getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(Integer maxPoints) {
        this.maxPoints = maxPoints;
    }

    public Integer getAccuracyOfScores() {
        return accuracyOfScores;
    }

    public void setAccuracyOfScores(Integer accuracyOfScores) {
        this.accuracyOfScores = accuracyOfScores;
    }

    public Set<TutorialGroup> getTutorialGroups() {
        return tutorialGroups;
    }

    public void setTutorialGroups(Set<TutorialGroup> tutorialGroups) {
        this.tutorialGroups = tutorialGroups;
    }

    public void validateOnlineCourseAndRegistrationEnabled() {
        if (isOnlineCourse() && isRegistrationEnabled()) {
            throw new BadRequestAlertException("Online course and registration enabled cannot be active at the same time", ENTITY_NAME, "onlineCourseRegistrationEnabledInvalid",
                    true);
        }
    }

    /**
     * Validates that the accuracy of the scores is between 0 and 5
     */
    public void validateAccuracyOfScores() {
        if (getAccuracyOfScores() == null) {
            throw new BadRequestAlertException("The course needs to specify the accuracy of scores", ENTITY_NAME, "accuracyOfScoresNotSet", true);
        }
        if (getAccuracyOfScores() < 0 || getAccuracyOfScores() > 5) {
            throw new BadRequestAlertException("The accuracy of scores defined for the course is either negative or uses too many decimal places (more than five)", ENTITY_NAME,
                    "accuracyOfScoresInvalid", true);
        }
    }

    /**
     * Validates that the short name of the course follows SHORT_NAME_PATTERN
     */
    public void validateShortName() {
        // Check if the course shortname matches regex
        Matcher shortNameMatcher = SHORT_NAME_PATTERN.matcher(getShortName());
        if (!shortNameMatcher.matches()) {
            throw new BadRequestAlertException("The shortname is invalid", ENTITY_NAME, "shortnameInvalid", true);
        }
    }

    /**
     * validates that the configuration for complaints and more feedback requests is correct
     */
    public void validateComplaintsAndRequestMoreFeedbackConfig() {
        if (getMaxComplaints() == null) {
            // set the default value to prevent null pointer exceptions
            setMaxComplaints(3);
        }
        if (getMaxTeamComplaints() == null) {
            // set the default value to prevent null pointer exceptions
            setMaxTeamComplaints(3);
        }
        if (getMaxComplaints() < 0) {
            throw new BadRequestAlertException("Max Complaints cannot be negative", ENTITY_NAME, "maxComplaintsInvalid", true);
        }
        if (getMaxTeamComplaints() < 0) {
            throw new BadRequestAlertException("Max Team Complaints cannot be negative", ENTITY_NAME, "maxTeamComplaintsInvalid", true);
        }
        if (getMaxComplaintTimeDays() < 0) {
            throw new BadRequestAlertException("Max Complaint Days cannot be negative", ENTITY_NAME, "maxComplaintDaysInvalid", true);
        }
        if (getMaxComplaintTextLimit() < 0) {
            throw new BadRequestAlertException("Max Complaint text limit cannot be negative", ENTITY_NAME, "maxComplaintTextLimitInvalid", true);
        }
        if (getMaxComplaintTextLimit() > COMPLAINT_TEXT_LIMIT) {
            throw new BadRequestAlertException("Max Complaint response text limit cannot be above " + COMPLAINT_TEXT_LIMIT + " characters.", ENTITY_NAME,
                    "maxComplaintTextLimitInvalid", true);
        }
        if (getMaxComplaintResponseTextLimit() < 0) {
            throw new BadRequestAlertException("Max Complaint response text limit cannot be negative", ENTITY_NAME, "maxComplaintResponseTextLimitInvalid", true);
        }
        if (getMaxComplaintResponseTextLimit() > COMPLAINT_RESPONSE_TEXT_LIMIT) {
            throw new BadRequestAlertException("Max Complaint response text limit cannot be above " + COMPLAINT_RESPONSE_TEXT_LIMIT + " characters.", ENTITY_NAME,
                    "maxComplaintResponseTextLimitInvalid", true);
        }
        if (getMaxRequestMoreFeedbackTimeDays() < 0) {
            throw new BadRequestAlertException("Max Request More Feedback Days cannot be negative", ENTITY_NAME, "maxRequestMoreFeedbackDaysInvalid", true);
        }
        if (getMaxComplaintTimeDays() == 0 && (getMaxComplaints() != 0 || getMaxTeamComplaints() != 0)) {
            throw new BadRequestAlertException("If complaints or more feedback requests are allowed, the complaint time in days must be positive.", ENTITY_NAME,
                    "complaintsConfigInvalid", true);
        }
        if (getMaxComplaintTimeDays() != 0 && getMaxComplaints() == 0 && getMaxTeamComplaints() == 0) {
            throw new BadRequestAlertException("If no complaints or more feedback requests are allowed, the complaint time in days should be set to zero.", ENTITY_NAME,
                    "complaintsConfigInvalid", true);
        }
    }

    public void validateRegistrationConfirmationMessage() {
        if (getRegistrationConfirmationMessage() != null && getRegistrationConfirmationMessage().length() > 2000) {
            throw new BadRequestAlertException("Confirmation registration message must be shorter than 2000 characters", ENTITY_NAME, "confirmationRegistrationMessageInvalid",
                    true);
        }
    }

    /**
     * Returns true if the start and end date of the course fulfill all requirements
     * @return true if the dates are valid
     */
    public boolean isValidStartAndEndDate() {
        return getStartDate() == null || getEndDate() == null || this.getEndDate().isAfter(this.getStartDate());
    }

    /**
     * We want to add users to a group, however different courses might have different courseGroupNames, therefore we
     * use this method to return the customized courseGroup name
     *
     * @param courseGroup the courseGroup we want to add the user to
     *
     * @return the customized userGroupName
     */
    public String defineCourseGroupName(String courseGroup) {
        return switch (courseGroup) {
            case "students" -> getStudentGroupName();
            case "tutors" -> getTeachingAssistantGroupName();
            case "instructors" -> getInstructorGroupName();
            case "editors" -> getEditorGroupName();
            default -> throw new IllegalArgumentException("The course group does not exist");
        };
    }
}
