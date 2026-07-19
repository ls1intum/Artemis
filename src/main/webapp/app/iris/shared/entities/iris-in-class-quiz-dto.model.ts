import dayjs from 'dayjs/esm';

export interface IrisInClassQuizDTO {
    timerExpiresAt: dayjs.Dayjs;
    timeLimit: number;
}
