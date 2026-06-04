import dayjs from 'dayjs/esm';

export class IrisQuizTimerDTO {
    timerExpiresAt: dayjs.Dayjs;
    timeLimit: number;
}
