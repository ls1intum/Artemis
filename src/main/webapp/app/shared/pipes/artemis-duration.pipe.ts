import { Pipe, PipeTransform } from '@angular/core';
import * as moment from 'moment';

@Pipe({ name: 'durationTo' })
export class DurationPipe implements PipeTransform {
    /**
     * Calculate the duration of an event from the diff of two dates.
     * @param startDate The start date time of the event. Must be convertible to moment().
     * @param endDate The end date time of the event. Must be convertible to moment().
     * @returns string 'HH:mm'
     */
    transform(startDate: Date | moment.Moment | string | number | null | undefined, endDate: Date | moment.Moment | string | number | null | undefined): string {
        if (!endDate || !moment(endDate).isValid() || !startDate || !moment(startDate).isValid()) {
            return '';
        }
        if (moment(endDate).diff(startDate, 'hours') < 24) {
            return moment.utc(moment(endDate, 'DD/MM/YYYY HH:mm:ss').diff(moment(startDate, 'DD/MM/YYYY HH:mm'))).format('HH:mm');
        } else {
            const ms = moment(endDate, 'DD/MM/YYYY HH:mm:ss').diff(moment(startDate, 'DD/MM/YYYY HH:mm:ss'));
            const d = moment.duration(ms);
            return Math.floor(d.asHours()) + moment.utc(ms).format(':mm');
        }
    }
}
