/**
 * Lightweight per-tab access flags for the course overview, returned by `GET api/course/courses/{courseId}/access`
 * and used by the {@link CourseOverviewGuard} to decide tab access without loading the full course.
 *
 * All flags are optional: the server omits `false` flags from the payload (`@JsonInclude(NON_DEFAULT)`), so an absent
 * flag means "no access" — the correct default. The EXERCISES tab is always accessible and therefore has no flag.
 */
export interface CourseTabAccess {
    lecturesEnabled?: boolean;
    examsVisible?: boolean;
    competenciesOrPrerequisites?: boolean;
    tutorialGroups?: boolean;
    dashboardEnabled?: boolean;
    irisEnabled?: boolean;
    faqAccepted?: boolean;
    learningPathsEnabled?: boolean;
    communicationEnabled?: boolean;
    trainingEnabled?: boolean;
}
