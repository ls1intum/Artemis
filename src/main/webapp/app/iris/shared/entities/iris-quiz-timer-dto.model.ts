import dayjs from 'dayjs/esm';

export interface IrisQuizTimerDTO {
    timerExpiresAt: dayjs.Dayjs;
    timeLimit: number;
}
