import { ChangeDetectorRef, NgZone, OnDestroy, Pipe, PipeTransform } from '@angular/core';
import { TimeAgoPipe } from 'ngx-moment';

@Pipe({
    name: 'artemisTimeAgo',
    pure: false,
})
/**
 * a simple wrapper to prevent compile errors in IntelliJ
 */
export class ArtemisTimeAgoPipe implements PipeTransform, OnDestroy {
    private timeAgoPipe: TimeAgoPipe;

    constructor(cdRef: ChangeDetectorRef, ngZone: NgZone) {
        this.timeAgoPipe = new TimeAgoPipe(cdRef, ngZone);
    }

    transform(value: moment.MomentInput, omitSuffix?: boolean, formatFn?: (m: moment.Moment) => string): string {
        return this.timeAgoPipe.transform(value, omitSuffix, formatFn);
    }

    ngOnDestroy() {
        this.timeAgoPipe.ngOnDestroy();
    }
}
