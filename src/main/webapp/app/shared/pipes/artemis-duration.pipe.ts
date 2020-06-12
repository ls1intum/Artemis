import { Pipe, PipeTransform } from '@angular/core';
import * as moment from 'moment';

@Pipe({ name: 'durationTo' })
export class DurationPipe implements PipeTransform {
    /**
     * Calculate the duration of an event from the diff of two dates.
     * @param startDate The start date time of the event. Must be convertible to moment().
     * @param endDate The end date time of the event. Must be convertible to moment().
     * @param format Format of the localized date time. Defaults to 'long'.
     * @returns string 'HH:mm'
     */
    transform(
        startDate: Date | moment.Moment | string | number | null,
        endDate: Date | moment.Moment | string | number | null,
        format: 'short' | 'long' | 'short-date' | 'long-date' | 'time' = 'long',
    ): string {
        if (!endDate || !moment(endDate).isValid() || !startDate || !moment(startDate).isValid()) {
            return '';
        }
        if (moment(endDate).diff(startDate, 'hours') < 24) {
            console.log(moment(endDate).diff(startDate, 'hours'));
            return moment.utc(moment(endDate, 'DD/MM/YYYY HH:mm:ss').diff(moment(startDate, 'DD/MM/YYYY HH:mm'))).format('H:mm');
        } else {
            console.log(moment(endDate).diff(startDate, 'hours'));
            const ms = moment(endDate, 'DD/MM/YYYY HH:mm:ss').diff(moment(startDate, 'DD/MM/YYYY HH:mm:ss'));
            const d = moment.duration(ms);
            return Math.floor(d.asHours()) + moment.utc(ms).format(':mm');
        }
    }
}
