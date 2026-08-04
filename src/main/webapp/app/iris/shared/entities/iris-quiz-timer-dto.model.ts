import dayjs from 'dayjs/esm';

/**
 * Timer configuration for the ask-user-mode quiz, as received from the server.
 */
export interface IrisQuizTimerDTO {
    timerExpiresAt: dayjs.Dayjs;
    timeLimit: number;
}
