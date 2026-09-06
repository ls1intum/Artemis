package de.tum.cit.aet.artemis.course.domain;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.account.domain.Organization;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.UserCourseRole;
import de.tum.cit.aet.artemis.core.util.CanonicalFileUriConverter;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
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
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Course extends DomainObject {

    public static final String ENTITY_NAME = "course";

    private static final int DEFAULT_COMPLAINT_TEXT_LIMIT = 2000;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "short_name", unique = true)
    private String shortName;

    @Column(name = "start_date")
    private ZonedDateTime startDate;

    @Column(name = "end_date")
    private ZonedDateTime endDate;

    @Column(name = "enrollment_start_date")
    private ZonedDateTime enrollmentStartDate;

    @Column(name = "enrollment_end_date")
    private ZonedDateTime enrollmentEndDate;

    @Column(name = "unenrollment_end_date")
    private ZonedDateTime unenrollmentEndDate;

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
    @JoinColumn(name = "online_course_configuration_id")
    private OnlineCourseConfiguration onlineCourseConfiguration;

    // Lazy on purpose: the course table is already wide and these values are only needed in specific flows. Note that
    // getCourseConfiguration() returns null while the association is uninitialized, so every flow that needs it must
    // fetch it deliberately. The ones that do: the instructor course-settings read path
    // (findWithEagerOnlineCourseConfigurationAndTutorialGroupConfigurationById), the course update path (which attaches
    // it via CourseConfigurationRepository.findByCourseId so applyTo updates it in place) and the data-retention cleanup
    // queries. Do NOT add it to any other course query or entity graph.
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "course_configuration_id")
    private CourseConfiguration courseConfiguration;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "info_sharing_config", nullable = false)
    private CourseInformationSharingConfiguration courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING; // default value

    // TODO: move this into a separate entity to avoid it is loaded whenever the course is loaded
    @Column(name = "info_sharing_messaging_code_of_conduct")
    private String courseInformationSharingMessagingCodeOfConduct;

    @Column(name = "max_complaints", nullable = false)
    private Integer maxComplaints = 3;  // default value

    @Column(name = "max_team_complaints", nullable = false)
    private Integer maxTeamComplaints = 3;  // default value

    @Column(name = "max_complaint_time_days", nullable = false)
    private int maxComplaintTimeDays = 7;   // default value

    @Column(name = "max_request_more_feedback_time_days", nullable = false)
    private int maxRequestMoreFeedbackTimeDays = 7;   // default value

    @Column(name = "max_complaint_text_limit")
    private int maxComplaintTextLimit = DEFAULT_COMPLAINT_TEXT_LIMIT;

    @Column(name = "max_complaint_response_text_limit")
    private int maxComplaintResponseTextLimit = DEFAULT_COMPLAINT_TEXT_LIMIT;

    @Column(name = "color")
    private String color;

    @Column(name = "course_icon", length = 256)
    @Convert(converter = CanonicalFileUriConverter.class)
    private String courseIcon;

    @Column(name = "registration_enabled") // TODO: rename column in database
    private Boolean enrollmentEnabled;

    @Column(name = "registration_confirmation_message") // TODO: rename column in database
    private String enrollmentConfirmationMessage;

    @Column(name = "unenrollment_enabled")
    private boolean unenrollmentEnabled = false;

    @Column(name = "onboarding_done", nullable = false)
    private boolean onboardingDone = false;

    @Column(name = "presentation_score")
    private Integer presentationScore;

    @Column(name = "course_archive_path")
    private String courseArchivePath;

    @Column(name = "max_points")
    private Integer maxPoints;

    @Column(name = "accuracy_of_scores", nullable = false)
    private Integer accuracyOfScores = 1; // default value

    @JsonIgnore
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "athena_config_id")
    private CourseAthenaConfig athenaConfig;

    /**
     * Note: Currently just used in the scope of the tutorial groups feature
     */
    @Column(name = "time_zone")
    private String timeZone;

    @OneToMany(mappedBy = "course", fetch = FetchType.LAZY)
    @JsonIgnoreProperties("course")
    private Set<Exercise> exercises = new HashSet<>();

    // Unidirectional Course -> ExerciseVariantGroup: the course owns its variant groups (FK course_id lives on
    // exercise_variant_group), which lets empty groups exist in exercise management before any exercise is added.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Set<ExerciseVariantGroup> exerciseVariantGroups = new HashSet<>();

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
    @JsonIgnoreProperties("course")
    private Set<Exam> exams = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "course_organization", joinColumns = { @JoinColumn(name = "course_id", referencedColumnName = "id") }, inverseJoinColumns = {
            @JoinColumn(name = "organization_id", referencedColumnName = "id") })
    @JsonIgnoreProperties("course")
    private Set<Organization> organizations = new HashSet<>();

    @OneToMany(mappedBy = "course", fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<UserCourseRole> courseRoles = new HashSet<>();

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
    private Long numberOfAcceptedFaqsTransient;

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

    public Long getNumberOfAcceptedFaqs() {
        return numberOfAcceptedFaqsTransient;
    }

    public void setNumberOfAcceptedFaqs(Long numberOfAcceptedFaqs) {
        this.numberOfAcceptedFaqsTransient = numberOfAcceptedFaqs;
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

    public CourseConfiguration getCourseConfiguration() {
        return Hibernate.isInitialized(courseConfiguration) ? courseConfiguration : null;
    }

    public void setCourseConfiguration(CourseConfiguration courseConfiguration) {
        this.courseConfiguration = courseConfiguration;
    }

    /**
     * Whether the course is grade-relevant, driving how long its student data is retained before the GDPR cleanup resets
     * it. A course without an explicit {@link CourseConfiguration} (i.e. one that was never edited) is treated as
     * grade-relevant, matching the safe default. This is null-safe with respect to the lazy association: it only reflects
     * the flag when the configuration has been initialized.
     *
     * @return {@code true} if the course is grade-relevant or has no explicit configuration, {@code false} if an
     *         instructor opted out
     */
    public boolean isGradeRelevant() {
        CourseConfiguration configuration = getCourseConfiguration();
        return configuration == null || configuration.isGradeRelevant();
    }

    /**
     * Whether the course is under a data-retention hold, which suspends the GDPR cleanup of its student data for as long
     * as it lasts (e.g. a pending objection or legal proceeding). A course without an explicit
     * {@link CourseConfiguration} is not held. This is null-safe with respect to the lazy association: it only reflects
     * the flag when the configuration has been initialized.
     *
     * @return {@code true} if an administrator or instructor placed the course under a retention hold
     */
    public boolean isDataRetentionHold() {
        CourseConfiguration configuration = getCourseConfiguration();
        return configuration != null && configuration.isDataRetentionHold();
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

    public boolean isOnboardingDone() {
        return onboardingDone;
    }

    public void setOnboardingDone(boolean onboardingDone) {
        this.onboardingDone = onboardingDone;
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

    public Set<ExerciseVariantGroup> getExerciseVariantGroups() {
        return exerciseVariantGroups;
    }

    public void addExerciseVariantGroup(ExerciseVariantGroup exerciseVariantGroup) {
        this.exerciseVariantGroups.add(exerciseVariantGroup);
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

    public Set<UserCourseRole> getCourseRoles() {
        return courseRoles;
    }

    public void setCourseRoles(Set<UserCourseRole> courseRoles) {
        this.courseRoles = courseRoles;
    }

    public Set<Prerequisite> getPrerequisites() {
        return prerequisites;
    }

    public void setPrerequisites(Set<Prerequisite> prerequisites) {
        this.prerequisites = prerequisites;
    }

    @Override
    public String toString() {
        return "Course{" + "id=" + getId() + ", title='" + getTitle() + "'" + ", description='" + getDescription() + "'" + ", shortName='" + getShortName() + "'" + ", startDate='"
                + getStartDate() + "'" + ", endDate='" + getEndDate() + "'" + ", enrollmentStartDate='" + getEnrollmentStartDate() + "'" + ", enrollmentEndDate='"
                + getEnrollmentEndDate() + "'" + ", unenrollmentEndDate='" + getUnenrollmentEndDate() + "'" + ", semester='" + getSemester() + "'" + "'" + ", onlineCourse='"
                + isOnlineCourse() + "'" + ", color='" + getColor() + "'" + ", courseIcon='" + getCourseIcon() + "'" + ", enrollmentEnabled='" + isEnrollmentEnabled() + "'"
                + ", unenrollmentEnabled='" + isUnenrollmentEnabled() + "'" + ", presentationScore='" + getPresentationScore() + "'" + "}";
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

    /**
     * Flat accessor for the auto-orchestration kill switch stored on the {@link CourseConfiguration}, mirroring
     * {@link #isGradeRelevant()}. Used by the course update flow to detect admin-only changes. This is null-safe with
     * respect to the lazy association: it only reflects the flag when the configuration has been initialized.
     *
     * @return whether auto-orchestration is enabled for this course, {@code false} when the configuration is absent or not loaded
     */
    public boolean getAutoOrchestratorEnabled() {
        CourseConfiguration configuration = getCourseConfiguration();
        return configuration != null && configuration.isAutoOrchestratorEnabled();
    }

    /**
     * Flat accessor for the per-course debounce-window override stored on the {@link CourseConfiguration}.
     *
     * @return the override in seconds, or {@code null} when unset / not loaded (global default applies)
     */
    public Integer getDebounceWindowSecondsOverride() {
        CourseConfiguration configuration = getCourseConfiguration();
        return configuration == null ? null : configuration.getDebounceWindowSecondsOverride();
    }

    /**
     * Flat accessor for the per-course daily-cap override stored on the {@link CourseConfiguration}.
     *
     * @return the override, or {@code null} when unset / not loaded (global default applies)
     */
    public Integer getMaxDailyOrchestrationOverride() {
        CourseConfiguration configuration = getCourseConfiguration();
        return configuration == null ? null : configuration.getMaxDailyOrchestrationOverride();
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

    public CourseAthenaConfig getAthenaConfig() {
        return athenaConfig;
    }

    public void setAthenaConfig(CourseAthenaConfig athenaConfig) {
        this.athenaConfig = athenaConfig;
    }

    @JsonProperty("athenaGradingFeedbackEnabled")
    public boolean isAthenaGradingFeedbackEnabled() {
        return athenaConfig != null && Hibernate.isInitialized(athenaConfig) && athenaConfig.isGradingFeedbackEnabled();
    }

    @JsonProperty("athenaFormativeFeedbackEnabled")
    public boolean isAthenaFormativeFeedbackEnabled() {
        return athenaConfig != null && Hibernate.isInitialized(athenaConfig) && athenaConfig.isFormativeFeedbackEnabled();
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
}
