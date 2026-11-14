/**
 * The child route paths of a course overview (`/courses/:courseId/<path>`).
 *
 * Kept in a module without dependencies: the course overview routes import {@link CourseOverviewGuard}, and the guard
 * needs these paths, so declaring them next to the routes would make the guard depend on the routes that use it.
 */
export enum CourseOverviewRoutePath {
    HOME = 'home',
    IRIS = 'iris',
    EXERCISES = 'exercises',
    EXAMS = 'exams',
    COMPETENCIES = 'competencies',
    TUTORIAL_GROUPS = 'tutorial-groups',
    FAQ = 'faq',
    LEARNING_PATH = 'learning-path',
    LECTURES = 'lectures',
    ENROLL = 'enroll',
    ARCHIVE = 'archive',
    STATISTICS = 'statistics',
    COMMUNICATION = 'communication',
    SETTINGS = 'settings',
    TRAINING = 'training',
    TRAINING_QUIZ = 'training/quiz',
    CALENDAR = 'calendar',
}
