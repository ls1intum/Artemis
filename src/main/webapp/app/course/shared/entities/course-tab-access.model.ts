/**
 * Lightweight per-tab access flags for the course overview, returned by `GET api/course/courses/{courseId}/access`
 * and used by the {@link CourseOverviewGuard} to decide tab access without loading the full course.
 *
 * The server uses `@JsonInclude(NON_EMPTY)`, so all boolean flags (including `false`) are always present in the
 * response. The EXERCISES tab is always accessible and therefore has no flag.
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
