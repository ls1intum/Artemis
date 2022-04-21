import { Theme } from 'app/core/theme/theme.service';

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
 * Graph colors, defined as colors to use in the default theme.
 */
export enum GraphColors {
    LIGHT_GREY = 'LIGHT_GREY',
    GREY = 'GREY',
    DARK_BLUE = 'DARK_BLUE',
    BLUE = 'BLUE',
    LIGHT_BLUE = 'LIGHT_BLUE',
    GREEN = 'GREEN',
    RED = 'RED',
    YELLOW = 'YELLOW',
    BLACK = 'BLACK',
}

/**
 * Returns the correct color to use in a graph.
 * Might return a different color definition than the GraphColors name might suggest to fit the theme, especially the background color of the page.
 * @param theme the current theme
 * @param color the default color
 */
export function getGraphColorForTheme(theme: Theme, color: GraphColors): string {
    switch (theme) {
        case Theme.LIGHT:
            switch (color) {
                case 'LIGHT_GREY':
                    return 'rgba(153,153,153,1)';
                case 'GREY':
                    return 'rgba(127,127,127,255)';
                case 'DARK_BLUE':
                    return 'rgba(53,61,71,1)';
                case 'BLUE':
                    return 'rgba(93,138,201,1)';
                case 'LIGHT_BLUE':
                    return 'rgba(135, 206, 250, 1)';
                case 'GREEN':
                    return 'rgba(40,164,40,1)';
                case 'RED':
                    return 'rgba(204,0,0,1)';
                case 'YELLOW':
                    return 'rgba(230, 174, 6, 1)';
                case 'BLACK':
                    return 'rgba(53,61,71,1)';
            }
            break;
        case Theme.DARK:
            switch (color) {
                case 'LIGHT_GREY':
                    return 'rgb(182,182,182)';
                case 'GREY':
                    return 'rgb(150,150,150)';
                case 'DARK_BLUE':
                    return 'rgb(159,186,248)';
                case 'BLUE':
                    return 'rgba(93,138,201,1)';
                case 'LIGHT_BLUE':
                    return 'rgba(135, 206, 250, 1)';
                case 'GREEN':
                    return 'rgba(40,164,40,1)';
                case 'RED':
                    return 'rgba(204,0,0,1)';
                case 'YELLOW':
                    return 'rgba(230, 174, 6, 1)';
                case 'BLACK':
                    return 'rgb(229,229,229)';
            }
    }
    throw new Error(`Unknown combination of theme and color: ${theme?.identifier} ${color}`);
}
