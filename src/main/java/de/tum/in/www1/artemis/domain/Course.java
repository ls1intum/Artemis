package de.tum.in.www1.artemis.domain;

import static de.tum.in.www1.artemis.config.Constants.*;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;

import javax.persistence.*;

import org.hibernate.Hibernate;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.domain.enumeration.CourseInformationSharingConfiguration;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSettings;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupsConfiguration;
import de.tum.in.www1.artemis.domain.view.QuizView;
import de.tum.in.www1.artemis.service.EntityFileService;
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

    private static final int DEFAULT_COMPLAINT_TEXT_LIMIT = 2000;

    @Transient
    private final transient FilePathService filePathService = new FilePathService();

    @Transient
    private final transient FileService fileService = new FileService();

    @Transient
    private final transient EntityFileService entityFileService = new EntityFileService(fileService, filePathService);

    @Transient
    private String prevCourseIcon;

    @Column(name = "title")
    @JsonView(QuizView.Before.class)
    private String title;

    @Column(name = "description")
    @JsonView(QuizView.Before.class)
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

    @Column(name = "enrollment_start_date")
    @JsonView(QuizView.Before.class)
    private ZonedDateTime enrollmentStartDate;

    @Column(name = "enrollment_end_date")
    @JsonView(QuizView.Before.class)
    private ZonedDateTime enrollmentEndDate;

    @Column(name = "unenrollment_end_date")
    @JsonView(QuizView.Before.class)
    private ZonedDateTime unenrollmentEndDate;

    @Column(name = "semester")
    @JsonView(QuizView.Before.class)
    private String semester;

    @Column(name = "test_course", nullable = false)
    @JsonView({ QuizView.Before.class })
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

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "online_course_configuration_id")
    private OnlineCourseConfiguration onlineCourseConfiguration;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "info_sharing_config", nullable = false)
    @JsonView(QuizView.Before.class)
    private CourseInformationSharingConfiguration courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING; // default value

    @Column(name = "info_sharing_messaging_code_of_conduct")
    private String courseInformationSharingMessagingCodeOfConduct;

    @Column(name = "max_complaints", nullable = false)
    @JsonView(QuizView.Before.class)
    private Integer maxComplaints = 3;  // default value

    @Column(name = "max_team_complaints", nullable = false)
    @JsonView(QuizView.Before.class)
    private Integer maxTeamComplaints = 3;  // default value

    @Column(name = "max_complaint_time_days", nullable = false)
    @JsonView(QuizView.Before.class)
    private int maxComplaintTimeDays = 7;   // default value

    @Column(name = "max_request_more_feedback_time_days", nullable = false)
    @JsonView(QuizView.Before.class)
    private int maxRequestMoreFeedbackTimeDays = 7;   // default value

    @Column(name = "max_complaint_text_limit")
    @JsonView(QuizView.Before.class)
    private int maxComplaintTextLimit = DEFAULT_COMPLAINT_TEXT_LIMIT;

    @Column(name = "max_complaint_response_text_limit")
    @JsonView(QuizView.Before.class)
    private int maxComplaintResponseTextLimit = DEFAULT_COMPLAINT_TEXT_LIMIT;

    @OneToMany(mappedBy = "course", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("course")
    private Set<Post> posts = new HashSet<>();

    @Column(name = "color")
    private String color;

    @Column(name = "course_icon")
    private String courseIcon;

    @Column(name = "registration_enabled") // TODO: rename column in database
    private Boolean enrollmentEnabled;

    @Column(name = "registration_confirmation_message") // TODO: rename column in database
    private String enrollmentConfirmationMessage;

    @Column(name = "unenrollment_enabled")
    private boolean unenrollmentEnabled = false;

    @Column(name = "presentation_score")
    private Integer presentationScore;

    @Column(name = "course_archive_path")
    private String courseArchivePath;

    @Column(name = "max_points")
    private Integer maxPoints;

    @Column(name = "accuracy_of_scores", nullable = false)
    @JsonView(QuizView.Before.class)
    private Integer accuracyOfScores = 1; // default value

    /**
     * Note: Currently just used in the scope of the tutorial groups feature
     */
    @Column(name = "time_zone")
    private String timeZone;

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
    private Set<Competency> competencies = new HashSet<>();

    @Column(name = "learning_paths_enabled", nullable = false)
    private boolean learningPathsEnabled = false;

    @OneToMany(mappedBy = "course", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("course")
    private Set<LearningPath> learningPaths = new HashSet<>();

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
    private Set<Competency> prerequisites = new HashSet<>();

    @OneToOne(cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "tutorial_groups_configuration_id")
    @JsonIgnoreProperties("course")
    private TutorialGroupsConfiguration tutorialGroupsConfiguration;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "iris_settings_id")
    private IrisSettings irisSettings;

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

    public ZonedDateTime getEnrollmentStartDate() {
        return enrollmentStartDate;
    }

    public void setEnrollmentStartDate(ZonedDateTime enrollmentStartDate) {
        this.enrollmentStartDate = enrollmentStartDate;
    }

    public ZonedDateTime getEnrollmentEndDate() {
        return enrollmentEndDate;
    }

    public void setEnrollmentEndDate(ZonedDateTime enrollmentEndDate) {
        this.enrollmentEndDate = enrollmentEndDate;
    }

    /**
     * Determine whether the current date is within the enrollment period (after start, before end).
     *
     * @return true if the current date is within the enrollment period, false otherwise
     */
    @JsonIgnore
    public boolean enrollmentIsActive() {
        ZonedDateTime now = ZonedDateTime.now();
        return (getEnrollmentStartDate() == null || getEnrollmentStartDate().isBefore(now)) && (getEnrollmentEndDate() == null || getEnrollmentEndDate().isAfter(now));
    }

    public ZonedDateTime getUnenrollmentEndDate() {
        return unenrollmentEndDate;
    }

    public void setUnenrollmentEndDate(ZonedDateTime unenrollmentEndDate) {
        this.unenrollmentEndDate = unenrollmentEndDate;
    }

    /**
     * Determine whether the current date is within the unenrollment period (after start, before end).
     * <p>
     * The unenrollment period starts with the enrollment start date and ends with the unenrollment end date if present,
     * otherwise the course end date will be used as the end of the period.
     *
     * @return true if the current date is within the unenrollment period, false otherwise
     */
    @JsonIgnore
    public boolean unenrollmentIsActive() {
        ZonedDateTime now = ZonedDateTime.now();
        final boolean startCondition = getEnrollmentStartDate() == null || getEnrollmentStartDate().isBefore(now);
        final boolean endCondition = (getUnenrollmentEndDate() == null && getEndDate() == null) || (getUnenrollmentEndDate() == null && getEndDate().isAfter(now))
                || (getUnenrollmentEndDate() != null && getUnenrollmentEndDate().isAfter(now));
        return startCondition && endCondition;
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

    public boolean isOnlineCourse() {
        return Boolean.TRUE.equals(onlineCourse);
    }

    public void setOnlineCourse(boolean onlineCourse) {
        this.onlineCourse = onlineCourse;
    }

    public OnlineCourseConfiguration getOnlineCourseConfiguration() {
        return Hibernate.isInitialized(onlineCourseConfiguration) ? onlineCourseConfiguration : null;
    }

    public void setOnlineCourseConfiguration(OnlineCourseConfiguration onlineCourseConfiguration) {
        this.onlineCourseConfiguration = onlineCourseConfiguration;
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

    @JsonIgnore
    public int getMaxComplaintTextLimitForExercise(Exercise exercise) {
        if (exercise.isExamExercise()) {
            return Math.max(DEFAULT_COMPLAINT_TEXT_LIMIT, getMaxComplaintTextLimit());
        }
        return getMaxComplaintTextLimit();
    }

    public int getMaxComplaintResponseTextLimit() {
        return maxComplaintResponseTextLimit;
    }

    public void setMaxComplaintResponseTextLimit(int maxComplaintResponseTextLimit) {
        this.maxComplaintResponseTextLimit = maxComplaintResponseTextLimit;
    }

    @JsonIgnore
    public int getMaxComplaintResponseTextLimitForExercise(Exercise exercise) {
        if (exercise.isExamExercise()) {
            return Math.max(DEFAULT_COMPLAINT_TEXT_LIMIT, getMaxComplaintResponseTextLimit());
        }
        return getMaxComplaintResponseTextLimit();
    }

    public boolean getComplaintsEnabled() {
        // maxComplaintTimeDays must be larger than zero,
        // and then either maxComplaints, maxTeamComplaints is larger than zero
        // See CourseResource for more details on the validation
        return this.maxComplaintTimeDays > 0;
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

    public Boolean isEnrollmentEnabled() {
        return enrollmentEnabled;
    }

    public void setEnrollmentEnabled(Boolean enrollmentEnabled) {
        this.enrollmentEnabled = enrollmentEnabled;
    }

    public String getEnrollmentConfirmationMessage() {
        return enrollmentConfirmationMessage;
    }

    public void setEnrollmentConfirmationMessage(String enrollmentConfirmationMessage) {
        this.enrollmentConfirmationMessage = enrollmentConfirmationMessage;
    }

    public boolean isUnenrollmentEnabled() {
        return unenrollmentEnabled;
    }

    public void setUnenrollmentEnabled(boolean unenrollmentEnabled) {
        this.unenrollmentEnabled = unenrollmentEnabled;
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

    public Set<Competency> getPrerequisites() {
        return prerequisites;
    }

    public void setPrerequisites(Set<Competency> prerequisites) {
        this.prerequisites = prerequisites;
    }

    public void addPrerequisite(Competency competency) {
        this.prerequisites.add(competency);
        competency.getConsecutiveCourses().add(this);
    }

    public void removePrerequisite(Competency competency) {
        this.prerequisites.remove(competency);
        competency.getConsecutiveCourses().remove(this);
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
        if (courseIcon != null) {
            courseIcon = entityFileService.moveTempFileBeforeEntityPersistence(courseIcon, FilePathService.getCourseIconFilePath(), false);
        }
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
        courseIcon = entityFileService.handlePotentialFileUpdateBeforeEntityPersistence(getId(), prevCourseIcon, courseIcon, FilePathService.getCourseIconFilePath(), false);
    }

    @PostRemove
    public void onDelete() {
        if (prevCourseIcon != null) {
            fileService.schedulePathForDeletion(Path.of(prevCourseIcon), 0);
        }
    }

    @Override
    public String toString() {
        return "Course{" + "id=" + getId() + ", title='" + getTitle() + "'" + ", description='" + getDescription() + "'" + ", shortName='" + getShortName() + "'"
                + ", studentGroupName='" + getStudentGroupName() + "'" + ", teachingAssistantGroupName='" + getTeachingAssistantGroupName() + "'" + ", editorGroupName='"
                + getEditorGroupName() + "'" + ", instructorGroupName='" + getInstructorGroupName() + "'" + ", startDate='" + getStartDate() + "'" + ", endDate='" + getEndDate()
                + "'" + ", enrollmentStartDate='" + getEnrollmentStartDate() + "'" + ", enrollmentEndDate='" + getEnrollmentEndDate() + "'" + ", unenrollmentEndDate='"
                + getUnenrollmentEndDate() + "'" + ", semester='" + getSemester() + "'" + "'" + ", onlineCourse='" + isOnlineCourse() + "'" + ", color='" + getColor() + "'"
                + ", courseIcon='" + getCourseIcon() + "'" + ", enrollmentEnabled='" + isEnrollmentEnabled() + "'" + ", unenrollmentEnabled='" + isUnenrollmentEnabled() + "'"
                + ", presentationScore='" + getPresentationScore() + "'" + "}";
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

    public Set<Competency> getCompetencies() {
        return competencies;
    }

    public void setCompetencies(Set<Competency> competencies) {
        this.competencies = competencies;
    }

    public boolean getLearningPathsEnabled() {
        return learningPathsEnabled;
    }

    public void setLearningPathsEnabled(boolean learningPathsEnabled) {
        this.learningPathsEnabled = learningPathsEnabled;
    }

    public Set<LearningPath> getLearningPaths() {
        return learningPaths;
    }

    public void setLearningPaths(Set<LearningPath> learningPaths) {
        this.learningPaths = learningPaths;
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

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    /**
     * Validates that only one of onlineCourse and enrollmentEnabled is selected
     */
    public void validateOnlineCourseAndEnrollmentEnabled() {
        if (isOnlineCourse() && isEnrollmentEnabled()) {
            throw new BadRequestAlertException("Online course and enrollment enabled cannot be active at the same time", ENTITY_NAME, "onlineCourseEnrollmentEnabledInvalid", true);
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

    public void validateEnrollmentConfirmationMessage() {
        if (getEnrollmentConfirmationMessage() != null && getEnrollmentConfirmationMessage().length() > 2000) {
            throw new BadRequestAlertException("Confirmation enrollment message must be shorter than 2000 characters", ENTITY_NAME, "confirmationEnrollmentMessageInvalid", true);
        }
    }

    /**
     * Validates if the start and end dates of the course fulfill all requirements.
     */
    public void validateStartAndEndDate() {
        if (getStartDate() != null && getEndDate() != null && !getStartDate().isBefore(getEndDate())) {
            throw new BadRequestAlertException("For Courses, the start date has to be before the end date", ENTITY_NAME, "invalidCourseStartDate", true);
        }
    }

    /**
     * Validates if the start and end date to enroll in the course fulfill all requirements.
     * <p>
     * The enrollment period is considered valid if
     * <ul>
     * <li>start and end date of the course are set and valid ({@link #validateStartAndEndDate()})</li>
     * <li>start and end date of the enrollment period are in the correct order,</li>
     * <li>and the start and end date of the enrollment is before the end date of the course.</li>
     * </ul>
     *
     * @throws BadRequestAlertException
     */
    public void validateEnrollmentStartAndEndDate() {
        if (getEnrollmentStartDate() == null || getEnrollmentEndDate() == null) {
            return;
        }
        final String errorKey = "enrollmentPeriodInvalid";
        if (!getEnrollmentStartDate().isBefore(getEnrollmentEndDate())) {
            throw new BadRequestAlertException("Enrollment start date must be before the end date.", ENTITY_NAME, errorKey, true);
        }

        if (getStartDate() == null || getEndDate() == null) {
            throw new BadRequestAlertException("Enrollment can not be set if the course has no assigned start and end date.", ENTITY_NAME, errorKey, true);
        }

        validateStartAndEndDate();

        if (getEnrollmentStartDate().isAfter(getStartDate())) {
            throw new BadRequestAlertException("Enrollment start date can not be after the start date of the course.", ENTITY_NAME, errorKey, true);
        }

        if (getEnrollmentEndDate().isAfter(getEndDate())) {
            throw new BadRequestAlertException("Enrollment end can not be after the end date of the course.", ENTITY_NAME, errorKey, true);
        }
    }

    /**
     * Validates if the end date to unenroll from the course fulfills all requirements.
     * <p>
     * The unenrollment end date is considered valid if
     * <ul>
     * <li>start and end date of the enrollment period are set and valid ({@link #validateEnrollmentStartAndEndDate()})</li>
     * <li>the enrollment period ends before the unenrollment end date,</li>
     * <li>and the end date for unenrollment is not after the end date of the course.</li>
     * </ul>
     *
     * @throws BadRequestAlertException
     */
    public void validateUnenrollmentEndDate() {
        if (getUnenrollmentEndDate() == null) {
            return;
        }

        validateEnrollmentStartAndEndDate();

        final String errorKey = "unenrollmentEndDateInvalid";

        if (getEnrollmentStartDate() == null || getEnrollmentEndDate() == null) {
            throw new BadRequestAlertException("Unenrollment end date requires a configured enrollment period.", ENTITY_NAME, errorKey, true);
        }

        if (!getEnrollmentEndDate().isBefore(getUnenrollmentEndDate())) {
            throw new BadRequestAlertException("End date for enrollment must be before the end date to unenroll.", ENTITY_NAME, errorKey, true);
        }

        if (getUnenrollmentEndDate().isAfter(getEndDate())) {
            throw new BadRequestAlertException("End date for enrollment can not be after the end date of the course.", ENTITY_NAME, errorKey, true);
        }
    }

    /**
     * We want to add users to a group, however different courses might have different courseGroupNames, therefore we
     * use this method to return the customized courseGroup name
     *
     * @param courseGroup the courseGroup we want to add the user to
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

    public TutorialGroupsConfiguration getTutorialGroupsConfiguration() {
        return tutorialGroupsConfiguration;
    }

    public void setTutorialGroupsConfiguration(TutorialGroupsConfiguration tutorialGroupsConfiguration) {
        this.tutorialGroupsConfiguration = tutorialGroupsConfiguration;
    }

    public CourseInformationSharingConfiguration getCourseInformationSharingConfiguration() {
        return courseInformationSharingConfiguration;
    }

    public void setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration courseInformationSharingConfiguration) {
        this.courseInformationSharingConfiguration = courseInformationSharingConfiguration;
    }

    public String getCourseInformationSharingMessagingCodeOfConduct() {
        return this.courseInformationSharingMessagingCodeOfConduct;
    }

    public void setCourseInformationSharingMessagingCodeOfConduct(String courseInformationSharingMessagingCodeOfConduct) {
        this.courseInformationSharingMessagingCodeOfConduct = courseInformationSharingMessagingCodeOfConduct;
    }

    public IrisSettings getIrisSettings() {
        return irisSettings;
    }

    public void setIrisSettings(IrisSettings irisSettings) {
        this.irisSettings = irisSettings;
    }
}
