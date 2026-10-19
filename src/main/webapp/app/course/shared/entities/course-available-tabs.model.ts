/**
 * Which course overview tabs are available to the current user, returned by
 * `GET api/course/courses/{courseId}/available-tabs`.
 *
 * This is the single source of truth for tab availability: the course sidebar renders its entries from it and the
 * {@link CourseOverviewGuard} decides from it whether a tab may be opened. The exercises tab is always available and
 * therefore has no flag.
 *
 * All flags are always present in the response — the server serialises the record without any `@JsonInclude` filter.
 */
export interface CourseAvailableTabs {
    lectures: boolean;
    exams: boolean;
    competencies: boolean;
    tutorialGroups: boolean;
    iris: boolean;
    faq: boolean;
    learningPaths: boolean;
    communication: boolean;
    training: boolean;
}
