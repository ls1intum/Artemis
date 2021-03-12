import { Pipe, PipeTransform } from '@angular/core';
import { TimeAgoPipe } from 'ngx-moment';

@Pipe({
    name: 'artemisTimeAgo',
    pure: false,
})
/**
 * a simple wrapper to prevent compile errors in IntelliJ
 */
export class ArtemisTimeAgoPipe implements PipeTransform {
    constructor(private timeAgoPipe: TimeAgoPipe) {}

    transform(value: moment.MomentInput, omitSuffix?: boolean, formatFn?: (m: moment.Moment) => string): string {
        return this.timeAgoPipe.transform(value, omitSuffix, formatFn);
    }
}
