package de.tum.cit.aet.artemis.core.service.featureusage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The curated list of features whose usage Artemis tracks.
 * <p>
 * This is the taxonomy the admin page navigates: a module (derived from the controller's package) contains areas, an area
 * contains features, and a feature is served by one or more controllers. It lives in one file on purpose. The point of the
 * list is that somebody can read the whole intended taxonomy at once and judge whether it is the right one, which is not
 * possible when the same information is spread across two hundred annotations.
 * <p>
 * A feature is usually one controller. Where several controllers are one feature to a user, they are grouped: the five
 * programming-exercise CRUD controllers are an implementation split, not five things an instructor does.
 * <p>
 * <b>Completeness is enforced, not hoped for.</b> {@code FeatureUsageCatalogueTest} fails when a controller is missing
 * here and when an entry names a controller that no longer exists, so adding a controller forces a deliberate decision
 * about which feature it belongs to, and renaming one cannot silently drop it out of the report.
 * <p>
 * Keyed by simple class name rather than by class literal because
 * {@code ArchitectureTest.testNoRestControllersImported} forbids importing a {@code @RestController}. The same test that
 * enforces completeness also protects against the typo that a string key would otherwise allow.
 * <p>
 * A method that needs its own feature, because one controller genuinely serves two, can override all of this with
 * {@link FeatureUsage}.
 */
public final class FeatureUsageCatalogue {

    /** Controller simple name to {@code area/feature}. */
    private static final Map<String, String> LABELS_BY_CONTROLLER = new HashMap<>();

    static {
        // ===== account =====
        feature("account", "self-service", "AccountResource", "AccountLegacyResource");
        feature("account", "passkeys", "PasskeyResource");
        feature("account", "access-tokens", "TokenResource");
        feature("users", "user-directory", "UserResource");
        feature("users", "user-administration", "AdminUserResource");
        feature("organizations", "organizations", "OrganizationResource");

        // ===== admin =====
        feature("monitoring", "audit-log", "AdminAuditResource");
        feature("monitoring", "server-logs", "AdminLogResource");
        feature("monitoring", "metrics", "AdminMetricsResource");
        feature("monitoring", "websocket-broker", "AdminWebsocketResource");
        feature("monitoring", "usage-statistics", "AdminStatisticsResource");
        feature("monitoring", "feature-usage", "AdminFeatureUsageResource");
        feature("monitoring", "scheduled-jobs", "AdminScheduleResource");
        feature("dependencies", "software-bill-of-materials", "AdminSbomResource");
        feature("courses", "course-administration", "AdminCourseResource");
        feature("courses", "course-requests", "AdminCourseRequestResource");
        feature("data-privacy", "data-cleanup", "AdminCleanupResource");
        feature("data-privacy", "data-exports", "AdminDataExportResource");
        feature("legal", "imprint", "AdminImprintResource");
        feature("legal", "privacy-statement", "AdminPrivacyStatementResource");
        feature("configuration", "feature-toggles", "AdminFeatureToggleResource");
        feature("organizations", "organization-administration", "AdminOrganizationResource");
        feature("build-system", "build-queue-administration", "AdminBuildJobQueueResource");

        // ===== assessment =====
        feature("grading", "results", "ResultResource");
        feature("grading", "long-feedback", "LongFeedbackTextResource");
        feature("grading", "grading-scale", "GradingScaleResource", "GradeStepResource");
        feature("grading", "bonus", "BonusResource");
        feature("grading", "participant-scores", "ParticipantScoreResource");
        feature("complaints", "complaints", "ComplaintResource");
        feature("complaints", "complaint-responses", "ComplaintResponseResource");
        feature("tutor-training", "example-submissions", "ExampleSubmissionResource");
        feature("tutor-training", "tutor-participation", "TutorParticipationResource");
        feature("tutor-training", "tutor-effort", "TutorEffortResource");
        feature("feedback", "student-ratings", "RatingResource");

        // ===== athena =====
        feature("feedback-suggestions", "feedback-suggestions", "AthenaResource");
        feature("feedback-suggestions", "internal-callbacks", "AthenaInternalResource");

        // ===== atlas =====
        feature("competencies", "competencies", "CompetencyResource", "CourseCompetencyResource");
        feature("competencies", "prerequisites", "PrerequisiteResource");
        feature("competencies", "standardized-competencies", "StandardizedCompetencyResource", "AdminStandardizedCompetencyResource");
        feature("learning-paths", "learning-paths", "LearningPathResource");
        feature("learner-profile", "learner-profile", "LearnerProfileResource", "CourseLearnerProfileResource");
        feature("ai", "competency-orchestration", "CompetencyOrchestrationResource");
        feature("ai", "atlas-agent", "AtlasAgentResource");
        feature("research", "science-events", "ScienceResource");
        feature("research", "science-settings", "ScienceSettingsResource");

        // ===== calendar =====
        feature("calendar", "calendar-events", "CalendarResource", "LegacyCalendarResource");

        // ===== communication =====
        feature("conversations", "conversations", "ConversationResource");
        feature("conversations", "channels", "ChannelResource");
        feature("conversations", "group-chats", "GroupChatResource");
        feature("conversations", "one-to-one-chats", "OneToOneChatResource");
        feature("posts", "messages", "ConversationMessageResource");
        feature("posts", "answers", "AnswerMessageResource");
        feature("posts", "reactions", "ReactionResource");
        feature("posts", "forwarding", "ForwardedMessageResource");
        feature("posts", "saved-posts", "SavedPostResource");
        feature("content", "faq", "FaqResource");
        feature("content", "link-previews", "LinkPreviewResource");
        feature("mobile-apps", "app-site-association", "AppleAppSiteAssociationResource", "AndroidAppSiteAssociationResource");

        // ===== core =====
        feature("files", "file-access", "FileResource");
        feature("data-privacy", "data-exports", "DataExportResource");
        feature("public", "account", "PublicAccountResource");
        feature("public", "imprint", "PublicImprintResource");
        feature("public", "privacy-statement", "PublicPrivacyStatementResource");
        feature("authentication", "jwt-tokens", "PublicUserJwtResource");
        feature("statistics", "statistics", "StatisticsResource");
        feature("sharing", "sharing-platform", "SharingSupportResource");

        // ===== course =====
        feature("management", "course-management", "CourseManagementResource", "CourseUpdateResource");
        feature("management", "archive", "CourseArchiveResource");
        feature("management", "material-import", "CourseMaterialImportResource");
        feature("management", "course-requests", "CourseRequestResource");
        feature("student-view", "course-overview", "CourseOverviewResource");
        feature("student-view", "enrollment", "CourseAccessResource");
        feature("analytics", "course-statistics", "CourseStatsResource");

        // ===== exam =====
        feature("authoring", "exam-management", "ExamResource", "AdminExamResource");
        feature("authoring", "exercise-groups", "ExerciseGroupResource");
        feature("authoring", "registration", "ExamUserResource");
        feature("conduction", "student-exam", "StudentExamResource");
        feature("rooms", "room-management", "ExamRoomManagementResource", "AdminExamRoomManagementResource");
        feature("rooms", "seat-distribution", "ExamRoomDistributionResource");

        // ===== exercise =====
        feature("management", "exercise-management", "ExerciseResource", "AdminExerciseResource");
        feature("management", "versions", "ExerciseVersionResource");
        feature("management", "consistency-check", "ConsistencyCheckResource");
        feature("management", "review", "ExerciseReviewResource");
        feature("management", "problem-statement", "ProblemStatementRenderingResource");
        feature("participation", "participations", "ParticipationResource", "ParticipationRetrievalResource", "ParticipationUpdateResource", "ParticipationDeletionResource");
        feature("participation", "teams", "TeamResource");
        feature("submission", "submissions", "SubmissionResource");
        feature("analytics", "score-charts", "ExerciseScoresChartResource");

        // ===== fileupload =====
        feature("authoring", "exercise-management", "FileUploadExerciseResource");
        feature("participation", "submissions", "FileUploadSubmissionResource");
        feature("assessment", "manual-assessment", "FileUploadAssessmentResource");

        // ===== globalsearch =====
        feature("search", "global-search", "GlobalSearchResource");

        // ===== hyperion =====
        feature("authoring-assistance", "problem-statement", "HyperionProblemStatementResource");
        feature("authoring-assistance", "faq-rewrite", "HyperionFaqResource");
        feature("authoring-assistance", "quiz-generation", "HyperionQuizQuestionGenerationResource");
        feature("authoring-assistance", "code-generation", "HyperionCodeGenerationResource");

        // ===== iris =====
        feature("chat", "chat-sessions", "IrisChatSessionResource");
        feature("chat", "messages", "IrisMessageResource");
        feature("chat", "tutor-suggestions", "IrisTutorSuggestionSessionResource");
        feature("chat", "availability", "IrisResource");
        feature("configuration", "settings", "IrisSettingsResource");
        feature("memory", "memory", "IrisMemoryResource");
        feature("search", "lecture-search", "IrisGlobalSearchResource");
        feature("monitoring", "dashboard", "IrisAdminDashboardResource");
        feature("internal", "pyris-status-updates", "PyrisInternalStatusUpdateResource");

        // ===== lecture =====
        feature("authoring", "lectures", "LectureResource");
        feature("authoring", "attachments", "AttachmentResource");
        feature("units", "unit-management", "LectureUnitResource");
        feature("units", "attachment-video-units", "AttachmentVideoUnitResource");
        feature("units", "text-units", "TextUnitResource");
        feature("units", "online-units", "OnlineUnitResource");
        feature("units", "exercise-units", "ExerciseUnitResource");
        feature("ai", "transcription", "LectureTranscriptionResource");

        // ===== localci =====
        feature("build-system", "build-queue", "BuildJobQueueResource");
        feature("build-system", "build-logs", "BuildLogResource");
        feature("build-system", "build-plans", "BuildPlanResource", "PublicBuildPlanResource");
        feature("build-system", "build-phase-templates", "BuildPhasesTemplateResource");
        feature("build-system", "after-due-date-builds", "AutomaticAfterDueDateResource");

        // ===== localvc =====
        feature("access", "ssh-fingerprints", "SshFingerprintsProviderResource");

        // ===== lti =====
        feature("lti", "launch", "LtiResource", "PublicLtiResource");
        feature("lti", "platform-configuration", "AdminLtiConfigurationResource");
        feature("lti", "oauth2-keys", "PublicOAuth2JWKSResource");

        // ===== modeling =====
        feature("authoring", "exercise-management", "ModelingExerciseResource");
        feature("participation", "submissions", "ModelingSubmissionResource");
        feature("assessment", "manual-assessment", "ModelingAssessmentResource");
        feature("diagrams", "apollon-diagrams", "ApollonDiagramResource");
        feature("diagrams", "apollon-conversion", "ApollonConversionResource");

        // ===== notification =====
        feature("course-notifications", "notifications", "CourseNotificationResource");
        feature("course-notifications", "read-status", "UserCourseNotificationStatusResource");
        feature("settings", "course-settings", "UserCourseNotificationSettingResource");
        feature("settings", "global-settings", "GlobalNotificationSettingResource");
        feature("delivery", "push-devices", "PushNotificationResource");
        feature("system-notifications", "system-notifications", "SystemNotificationResource", "PublicSystemNotificationResource");
        feature("system-notifications", "administration", "AdminSystemNotificationResource");

        // ===== plagiarism =====
        feature("detection", "plagiarism-checks", "PlagiarismResource");
        feature("cases", "plagiarism-cases", "PlagiarismCaseResource");
        feature("cases", "posts", "PlagiarismPostResource");
        feature("cases", "answer-posts", "PlagiarismAnswerPostResource");

        // ===== programming =====
        feature("authoring", "exercise-management", "ProgrammingExerciseCreationResource", "ProgrammingExerciseUpdateResource", "ProgrammingExercisePartialUpdateResource",
                "ProgrammingExerciseDeletionResource", "ProgrammingExerciseRetrievalResource");
        feature("authoring", "import-export", "ProgrammingExerciseExportImportResource");
        feature("authoring", "sharing", "ExerciseSharingResource");
        feature("authoring", "tasks", "ProgrammingExerciseTaskResource");
        feature("authoring", "uml-rendering", "PlantUmlResource");
        feature("configuration", "test-cases", "ProgrammingExerciseTestCaseResource");
        feature("configuration", "grading", "ProgrammingExerciseGradingResource");
        feature("configuration", "static-code-analysis", "StaticCodeAnalysisResource");
        feature("configuration", "submission-policy", "SubmissionPolicyResource");
        feature("configuration", "auxiliary-repositories", "AuxiliaryRepositoryResource");
        feature("participation", "participations", "ProgrammingExerciseParticipationResource");
        feature("participation", "online-editor", "RepositoryProgrammingExerciseParticipationResource");
        feature("participation", "online-ide", "TheiaConfigurationResource");
        feature("participation", "ide-settings", "IdeSettingsResource");
        feature("submission", "submissions", "ProgrammingSubmissionResource");
        feature("submission", "build-results", "PublicProgrammingExerciseResultResource");
        feature("assessment", "manual-assessment", "ProgrammingAssessmentResource");
        feature("integrity", "plagiarism", "ProgrammingExercisePlagiarismResource");
        feature("repositories", "test-repository", "TestRepositoryResource");
        feature("repositories", "export", "ProgrammingExerciseRepositoryExportResource");
        feature("access", "vcs-access-tokens", "RepositoryVcsAccessTokenResource", "VcsAccessTokenOverviewResource");
        feature("access", "ssh-keys", "SshPublicKeysResource");

        // ===== quiz =====
        feature("authoring", "exercise-management", "QuizExerciseResource", "QuizExerciseCreationUpdateResource", "QuizExerciseDeletionResource", "QuizExerciseRetrievalResource");
        feature("conduction", "batches", "QuizExerciseBatchResource");
        feature("conduction", "participation", "QuizParticipationResource");
        feature("conduction", "submissions", "QuizSubmissionResource");
        feature("evaluation", "evaluation", "QuizExerciseEvaluationResource");
        feature("practice", "training", "QuizTrainingResource");

        // ===== text =====
        feature("authoring", "exercise-management", "TextExerciseResource", "TextExerciseCreationUpdateResource");
        feature("authoring", "import-export", "TextExerciseExportImportResource");
        feature("participation", "submissions", "TextSubmissionResource");
        feature("assessment", "manual-assessment", "TextAssessmentResource");
        feature("assessment", "assessment-analytics", "TextAssessmentEventResource", "AdminTextAssessmentEventResource");
        feature("integrity", "plagiarism", "TextExercisePlagiarismResource");

        // ===== tutorialgroup =====
        feature("management", "tutorial-groups", "TutorialGroupResource");
        feature("management", "sessions", "TutorialGroupSessionResource");
        feature("management", "free-periods", "TutorialGroupFreePeriodResource");
        feature("management", "configuration", "TutorialGroupsConfigurationResource");

        // ===== videosource =====
        feature("video", "tum-live", "TumLiveResource");
    }

    private FeatureUsageCatalogue() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Registers one feature and the controllers that serve it.
     *
     * @param area        the area within the module that groups this feature
     * @param feature     the feature, in kebab-case and unique within the module
     * @param controllers the simple names of the controllers that make it up
     */
    private static void feature(String area, String feature, String... controllers) {
        for (String controller : controllers) {
            LABELS_BY_CONTROLLER.put(controller, area + '/' + feature);
        }
    }

    /**
     * Returns the {@code area/feature} label of a controller.
     *
     * @param controllerSimpleName the simple class name of the controller
     * @return the label, or empty if the controller is not catalogued
     */
    public static Optional<String> labelFor(String controllerSimpleName) {
        return Optional.ofNullable(LABELS_BY_CONTROLLER.get(controllerSimpleName));
    }

    /**
     * Every catalogued controller, so the test can compare the list against the controllers that actually exist.
     *
     * @return the simple names of all catalogued controllers
     */
    public static Set<String> catalogedControllers() {
        return Set.copyOf(LABELS_BY_CONTROLLER.keySet());
    }
}
