import dayjs from 'dayjs';

export enum IrisProactiveEventDisableDuration {
    THIRTY_MINUTES,
    ONE_HOUR,
    ONE_DAY,
    FOREVER,
    CUSTOM,
}

export class IrisDisableProactiveEventsDTO {
    duration: IrisProactiveEventDisableDuration | null;
    endTime: dayjs.Dayjs | null;
}

export class IrisDisableProactiveEventsResponseDTO {
    disabledUntil: number | null;
}
