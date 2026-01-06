package de.tum.cit.aet.artemis.core.domain;

import static de.tum.cit.aet.artemis.core.config.Constants.ARTEMIS_GROUP_DEFAULT_PREFIX;
import static de.tum.cit.aet.artemis.core.config.Constants.SHORT_NAME_PATTERN;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.Hibernate;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;

/**
 * A Course.
 */
@Entity
@Table(name = "course")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Course extends DomainObject {

    public static final String ENTITY_NAME = "course";

    @Column(name = "title")
    private String title;

    @Column(name = "short_name", unique = true)
    private String shortName;

    @Column(name = "student_group_name")
    private String studentGroupName;

    @Column(name = "teaching_assistant_group_name")
    private String teachingAssistantGroupName;

    @Column(name = "editor_group_name")
    private String editorGroupName;

    @Column(name = "instructor_group_name")
    private String instructorGroupName;

    @Column(name = "start_date")
    private ZonedDateTime startDate;

    @Column(name = "end_date")
    private ZonedDateTime endDate;

    @Column(name = "semester")
    private String semester;

    @Column(name = "test_course", nullable = false)
    private boolean testCourse = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "language")
    private Language language;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_programming_language")
    private ProgrammingLanguage defaultProgrammingLanguage;

    @Column(name = "online_course")
    private Boolean onlineCourse = false;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "online_course_configuration_id", unique = true)
    private OnlineCourseConfiguration onlineCourseConfiguration;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_configuration_id", nullable = false, unique = true)
    private CourseEnrollmentConfiguration enrollmentConfiguration;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "complaint_configuration_id", nullable = false, unique = true)
    private CourseComplaintConfiguration complaintConfiguration;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "extended_settings_id", nullable = false, unique = true)
    private CourseExtendedSettings extendedSettings;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "info_sharing_config", nullable = false)
    private CourseInformationSharingConfiguration courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING; // default value

    @Column(name = "color")
    private String color;

    @Column(name = "course_icon")
    private String courseIcon;

    @Column(name = "faq_enabled")
    private boolean faqEnabled = false;

    @Column(name = "presentation_score")
    private Integer presentationScore;

    @Column(name = "max_points")
    private Integer maxPoints;

    @Column(name = "accuracy_of_scores", nullable = false)
    private Integer accuracyOfScores = 1; // default value

    @Column(name = "restricted_athena_modules_access", nullable = false)
    private boolean restrictedAthenaModulesAccess = false; // default is false

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

    @Column(name = "student_course_analytics_dashboard_enabled", nullable = false)
    private boolean studentCourseAnalyticsDashboardEnabled = false;

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

    @OneToMany(mappedBy = "course", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("course")
    @OrderBy("title")
    private Set<Prerequisite> prerequisites = new HashSet<>();

    @OneToOne(cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "tutorial_groups_configuration_id")
    @JsonIgnoreProperties("course")
    private TutorialGroupsConfiguration tutorialGroupsConfiguration;

    // NOTE: Helpers variable names must be different from Getter name, so that Jackson ignores the @Transient annotation, but Hibernate still respects it
    @Transient
    private Long numberOfInstructorsTransient;

    @Transient
    private Long numberOfEditorsTransient;

    @Transient
    private Long numberOfTeachingAssistantsTransient;

    @Transient
    private Long numberOfStudentsTransient;

    @Transient
    private Long numberOfTutorialGroupsTransient;

    @Transient
    private Long numberOfCompetenciesTransient;

    @Transient
    private Long numberOfPrerequisitesTransient;

    @Transient
    private boolean trainingEnabledTransient;

    public boolean isTrainingEnabled() {
        return trainingEnabledTransient;
    }

    public void setTrainingEnabled(boolean trainingEnabled) {
        this.trainingEnabledTransient = trainingEnabled;
    }

    public Long getNumberOfTutorialGroups() {
        return numberOfTutorialGroupsTransient;
    }

    public Long getNumberOfCompetencies() {
        return numberOfCompetenciesTransient;
    }

    public Long getNumberOfPrerequisites() {
        return numberOfPrerequisitesTransient;
    }

    public void setNumberOfTutorialGroups(Long numberOfTutorialGroups) {
        this.numberOfTutorialGroupsTransient = numberOfTutorialGroups;
    }

    public void setNumberOfCompetencies(Long numberOfCompetencies) {
        this.numberOfCompetenciesTransient = numberOfCompetencies;
    }

    public void setNumberOfPrerequisites(Long numberOfPrerequisites) {
        this.numberOfPrerequisitesTransient = numberOfPrerequisites;
    }

    public String getTitle() {
        return title;
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

    public CourseEnrollmentConfiguration getEnrollmentConfiguration() {
        return Hibernate.isInitialized(enrollmentConfiguration) ? enrollmentConfiguration : null;
    }

    public void setEnrollmentConfiguration(CourseEnrollmentConfiguration enrollmentConfiguration) {
        this.enrollmentConfiguration = enrollmentConfiguration;
        if (enrollmentConfiguration != null) {
            enrollmentConfiguration.setCourse(this);
        }
    }

    public CourseComplaintConfiguration getComplaintConfiguration() {
        return Hibernate.isInitialized(complaintConfiguration) ? complaintConfiguration : null;
    }

    public void setComplaintConfiguration(CourseComplaintConfiguration complaintConfiguration) {
        this.complaintConfiguration = complaintConfiguration;
        if (complaintConfiguration != null) {
            complaintConfiguration.setCourse(this);
        }
    }

    public CourseExtendedSettings getExtendedSettings() {
        return Hibernate.isInitialized(extendedSettings) ? extendedSettings : null;
    }

    public void setExtendedSettings(CourseExtendedSettings extendedSettings) {
        this.extendedSettings = extendedSettings;
        if (extendedSettings != null) {
            extendedSettings.setCourse(this);
        }
    }

    /**
     * Initialize required configurations with default values before persisting a course so the non-null
     * constraints on the foreign keys are satisfied.
     */
    @PrePersist
    private void initializeConfigurations() {
        if (enrollmentConfiguration == null) {
            setEnrollmentConfiguration(new CourseEnrollmentConfiguration());
        }
        if (complaintConfiguration == null) {
            setComplaintConfiguration(new CourseComplaintConfiguration());
        }
        if (extendedSettings == null) {
            setExtendedSettings(new CourseExtendedSettings());
        }
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

    public boolean isFaqEnabled() {
        return faqEnabled;
    }

    public void setFaqEnabled(boolean faqEnabled) {
        this.faqEnabled = faqEnabled;
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

    public Set<Prerequisite> getPrerequisites() {
        return prerequisites;
    }

    public void setPrerequisites(Set<Prerequisite> prerequisites) {
        this.prerequisites = prerequisites;
    }

    @Override
    public String toString() {
        return "Course{" + "id=" + getId() + ", title='" + getTitle() + "'" + ", shortName='" + getShortName() + "'" + ", studentGroupName='" + getStudentGroupName() + "'"
                + ", teachingAssistantGroupName='" + getTeachingAssistantGroupName() + "'" + ", editorGroupName='" + getEditorGroupName() + "'" + ", instructorGroupName='"
                + getInstructorGroupName() + "'" + ", startDate='" + getStartDate() + "'" + ", endDate='" + getEndDate() + "'" + ", semester='" + getSemester() + "'"
                + ", onlineCourse='" + isOnlineCourse() + "'" + ", color='" + getColor() + "'" + ", courseIcon='" + getCourseIcon() + "'" + ", presentationScore='"
                + getPresentationScore() + "'" + ", faqEnabled='" + isFaqEnabled() + "'" + "}";
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

    public boolean getStudentCourseAnalyticsDashboardEnabled() {
        return studentCourseAnalyticsDashboardEnabled;
    }

    public void setStudentCourseAnalyticsDashboardEnabled(boolean studentCourseAnalyticsDashboardEnabled) {
        this.studentCourseAnalyticsDashboardEnabled = studentCourseAnalyticsDashboardEnabled;
    }

    public Set<LearningPath> getLearningPaths() {
        return learningPaths;
    }

    public void setLearningPaths(Set<LearningPath> learningPaths) {
        this.learningPaths = learningPaths;
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

    public boolean getRestrictedAthenaModulesAccess() {
        return restrictedAthenaModulesAccess;
    }

    public void setRestrictedAthenaModulesAccess(boolean restrictedAthenaModulesAccess) {
        this.restrictedAthenaModulesAccess = restrictedAthenaModulesAccess;
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
        boolean enrollmentEnabled = getEnrollmentConfiguration() != null && Boolean.TRUE.equals(getEnrollmentConfiguration().isEnrollmentEnabled());
        if (isOnlineCourse() && enrollmentEnabled) {
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
     * Validates if the start and end dates of the course fulfill all requirements.
     */
    public void validateStartAndEndDate() {
        if (getStartDate() != null && getEndDate() != null && !getStartDate().isBefore(getEndDate())) {
            throw new BadRequestAlertException("For Courses, the start date has to be before the end date", ENTITY_NAME, "invalidCourseStartDate", true);
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
}
