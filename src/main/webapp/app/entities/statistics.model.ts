import { Color, ScaleType } from '@swimlane/ngx-charts';

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

export enum GraphColors {
    LIGHT_GREY = 'rgba(153,153,153,1)',
    GREY = 'rgba(127,127,127,255)',
    DARK_BLUE = 'rgba(53,61,71,1)',
    BLUE = 'rgba(93,138,201,1)',
    LIGHT_BLUE = 'rgba(135, 206, 250, 1)',
    TRANSPARENT = 'rgba(93,138,201,0)',
    GREEN = 'rgba(40,164,40,1)',
    RED = 'rgba(204,0,0,1)',
    YELLOW = 'rgba(230, 174, 6, 1)',
}

export const ngxColor = {
    name: 'Statistics',
    selectable: true,
    group: ScaleType.Ordinal,
    domain: [GraphColors.DARK_BLUE],
} as Color;
