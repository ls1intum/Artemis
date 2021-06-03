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
    QUESTIONS_ASKED = 'QUESTIONS_ASKED',
    QUESTIONS_ANSWERED = 'QUESTIONS_ANSWERED',
}

export enum StatisticsView {
    ARTEMIS = 'ARTEMIS',
    COURSE = 'COURSE',
    EXERCISE = 'EXERCISE',
    EXAM = 'EXAM',
}

export enum GraphColors {
    DARK_BLUE = 'rgba(53,61,71,1)',
    BLUE = 'rgba(93,138,201,1)',
    TRANSPARENT = 'rgba(93,138,201,0)',
    BLUE_TRANSPARENT = 'rgba(93,138,201,0.5)',
    RED_TRANSPARENT = 'rgba(93,138,201,0.5)',
    GREEN_TRANSPARENT = 'rgba(93,138,201,0.5)',
}
