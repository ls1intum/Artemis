import { Pipe, PipeTransform } from '@angular/core';
import * as moment from 'moment';

@Pipe({
    name: 'formatDate',
})
export class DatePipe implements PipeTransform {
    /**
     * Displays the date in a readable format.
     * @param date The date expression (number or string)
     */
    transform(date: any): any {
        if (date == null) {
            return 'DD MMMM YYYY, hh:mm:ss PM';
        }
        const d = new Date(date);
        if (d && !isNaN(d.getTime())) {
            return moment(d).seconds(0).format('DD MMMM YYYY, hh:mm:ss A Z');
        } else {
            return 'DD MMMM YYYY, hh:mm:ss PM';
        }
    }
}
