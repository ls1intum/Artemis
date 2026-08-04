import dayjs from 'dayjs/esm';

/**
 * Timer configuration for an in-class ask-user-mode quiz, as received from the server.
 */
export interface IrisInClassQuizDTO {
    timerExpiresAt: dayjs.Dayjs;
    timeLimit: number;
}
