import { Pipe, PipeTransform } from '@angular/core';
import { ArtemisDatePipe, DateFormat, DateType } from 'app/shared/pipes/artemis-date.pipe';

@Pipe({
    name: 'artemisDateRange',
    pure: false,
})
export class ArtemisDateRangePipe implements PipeTransform {
    constructor(private artemisDatePipe: ArtemisDatePipe) {}
    transform(range: [DateType, DateType] | undefined | null, format: DateFormat = 'long-date', timeZone: string | undefined = undefined, weekDay = false): string {
        if (!range) {
            return '';
        }
        const startTransformed = this.artemisDatePipe.transform(range[0], format, false, timeZone, weekDay);
        const endTransformed = this.artemisDatePipe.transform(range[1], format, false, timeZone, weekDay);

        return `${startTransformed} - ${endTransformed}`;
    }
}
