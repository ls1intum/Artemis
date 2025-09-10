export enum SpanType {
    DAY = 'DAY',
    WEEK = 'WEEK',
    MONTH = 'MONTH',
    QUARTER = 'QUARTER',
    YEAR = 'YEAR',
}

export enum Graphs {
    // User statistics
    SUBMISSIONS = 'SUBMISSIONS',
    ACTIVE_USERS = 'ACTIVE_USERS',
    LOGGED_IN_USERS = 'LOGGED_IN_USERS',
    RELEASED_EXERCISES = 'RELEASED_EXERCISES',
    EXERCISES_DUE = 'EXERCISES_DUE',
    CONDUCTED_EXAMS = 'CONDUCTED_EXAMS',
    EXAM_PARTICIPATIONS = 'EXAM_PARTICIPATIONS',
    EXAM_REGISTRATIONS = 'EXAM_REGISTRATIONS',
    ACTIVE_TUTORS = 'ACTIVE_TUTORS',
    CREATED_RESULTS = 'CREATED_RESULTS',
    CREATED_FEEDBACKS = 'CREATED_FEEDBACKS',

    // Course overview
    ACTIVE_STUDENTS = 'ACTIVE_STUDENTS',

    // Course Statistics
    AVERAGE_SCORE = 'AVERAGE_SCORE',
    POSTS = 'POSTS',
    RESOLVED_POSTS = 'RESOLVED_POSTS',
}

export enum StatisticsView {
    ARTEMIS = 'ARTEMIS',
    COURSE = 'COURSE',
    EXERCISE = 'EXERCISE',
    EXAM = 'EXAM',
}

/**
 * Graph colors using CSS variables.
 * See theme variables scss files for exact colors.
 * Color names refer to the default theme; might not return what you expect in other themes
 * to account for background colors etc.
 */
export enum GraphColors {
    LIGHT_GREY = 'var(--graph-light-grey)',
    GREY = 'var(--graph-grey)',
    DARK_BLUE = 'var(--graph-dark-blue)',
    BLUE = 'var(--graph-blue)',
    LIGHT_BLUE = 'var(--graph-light-blue)',
    GREEN = 'var(--graph-green)',
    RED = 'var(--graph-red)',
    YELLOW = 'var(--graph-yellow)',
    BLACK = 'var(--graph-black)',
}
