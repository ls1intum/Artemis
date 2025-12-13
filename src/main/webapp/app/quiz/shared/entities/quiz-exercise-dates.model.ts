import dayjs from 'dayjs/esm';

export interface QuizExerciseDates {
    releaseDate?: dayjs.Dayjs;
    startDate?: dayjs.Dayjs;
    dueDate?: dayjs.Dayjs;
}
