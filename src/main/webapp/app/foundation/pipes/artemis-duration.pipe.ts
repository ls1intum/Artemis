import { Pipe, PipeTransform } from '@angular/core';
import dayjs from 'dayjs/esm';

@Pipe({ name: 'durationTo' })
export class DurationPipe implements PipeTransform {
    /**
     * Calculate the duration of an event from the diff of two dates.
     * @param startDate The start date time of the event. Must be convertible to dayjs().
     * @param endDate The end date time of the event. Must be convertible to dayjs().
     * @returns string 'HH:mm'
     */
    transform(startDate: Date | dayjs.Dayjs | string | number | null | undefined, endDate: Date | dayjs.Dayjs | string | number | null | undefined): string {
        if (!endDate || !dayjs(endDate).isValid() || !startDate || !dayjs(startDate).isValid()) {
            return '';
        }
        if (dayjs(endDate).diff(startDate, 'hours') < 24) {
            return dayjs.utc(dayjs(endDate, 'DD/MM/YYYY HH:mm:ss').diff(dayjs(startDate, 'DD/MM/YYYY HH:mm'))).format('HH:mm');
        } else {
            const ms = dayjs(endDate, 'DD/MM/YYYY HH:mm:ss').diff(dayjs(startDate, 'DD/MM/YYYY HH:mm:ss'));
            const duration = dayjs.duration(ms);
            return Math.floor(duration.asHours()) + dayjs.utc(ms).format(':mm');
        }
    }
}
