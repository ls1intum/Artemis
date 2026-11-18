package de.tum.cit.aet.artemis.core.service.featureusage;

/**
 * The product areas that group the {@link UserFeature}s on the admin feature usage page.
 * <p>
 * An area is what a user would call a part of Artemis ("Exams", "Communication"), not a code module: the features of one
 * area are often implemented across several modules, and the page shows those modules one level further down. The display
 * names live in the client translations ({@code artemisApp.featureUsage.catalog.area.*}) and in the email messages
 * ({@code email.featureUsageDigest.area.*}); {@code FeatureUsageAnnotationTest} fails when one of them is missing.
 */
public enum ProductArea {
    COURSES, COURSE_MANAGEMENT, EXERCISES, PROGRAMMING, QUIZ, TEXT_MODELING_UPLOAD, ASSESSMENT, EXAMS, LECTURES, COMMUNICATION, NOTIFICATIONS, TUTORIAL_GROUPS, COMPETENCIES,
    AI_LEARNERS, AI_AUTHORING, INTEGRITY, ACCOUNT, INTEGRATIONS, BUILD_SYSTEM, ADMINISTRATION
}
