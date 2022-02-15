import { Pipe, PipeTransform } from '@angular/core';

import dayjs from 'dayjs/esm';

@Pipe({
    name: 'duration',
})
// create a duration with the length of time in milliseconds
// Note: if you want to create a duration with a unit of measurement other than milliseconds, you need to use a separate (custom) pipe https://day.js.org/docs/en/durations/creating
export class DurationPipe implements PipeTransform {
    transform(value: any): string {
        if (value) {
            return dayjs.duration(value).humanize();
        }
        return '';
    }
}
