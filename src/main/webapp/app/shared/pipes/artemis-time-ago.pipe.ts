import { Pipe, ChangeDetectorRef, PipeTransform, OnDestroy, NgZone } from '@angular/core';
import dayjs from 'dayjs';
import { isDate } from 'app/shared/util/utils';

@Pipe({
    name: 'artemisTimeAgo',
    pure: false,
})
export class ArtemisTimeAgoPipe implements PipeTransform, OnDestroy {
    private currentTimer: number | null;

    private lastTime: Number;
    private lastValue: dayjs.ConfigType;
    private lastOmitSuffix?: boolean;
    private lastLocale: string;
    private lastText: string;
    private formatFn: (m: dayjs.Dayjs) => string;

    constructor(private cdRef: ChangeDetectorRef, private ngZone: NgZone) {}

    format(m: dayjs.Dayjs) {
        return m.from(dayjs(), this.lastOmitSuffix);
    }

    transform(value: dayjs.ConfigType, omitSuffix?: boolean, formatFn?: (m: dayjs.Dayjs) => string): string {
        if (this.hasChanged(value, omitSuffix)) {
            this.lastTime = getTime(value);
            this.lastValue = value;
            this.lastOmitSuffix = omitSuffix;
            this.lastLocale = getLocale(value);
            this.formatFn = formatFn || this.format.bind(this);
            this.removeTimer();
            this.createTimer();
            this.lastText = this.formatFn(dayjs(value));
        } else {
            this.createTimer();
        }

        return this.lastText;
    }

    ngOnDestroy(): void {
        this.removeTimer();
    }

    private createTimer() {
        if (this.currentTimer) {
            return;
        }

        const dayjsInstance = dayjs(this.lastValue);
        const timeToUpdate = getSecondsUntilUpdate(dayjsInstance) * 1000;

        this.currentTimer = this.ngZone.runOutsideAngular(() => {
            if (typeof window !== 'undefined') {
                return window.setTimeout(() => {
                    this.lastText = this.formatFn(dayjs(this.lastValue));

                    this.currentTimer = null;
                    this.ngZone.run(() => this.cdRef.markForCheck());
                }, timeToUpdate);
            } else {
                return null;
            }
        });
    }

    private removeTimer() {
        if (this.currentTimer) {
            window.clearTimeout(this.currentTimer);
            this.currentTimer = null;
        }
    }

    private hasChanged(value: dayjs.ConfigType, omitSuffix?: boolean): boolean {
        return getTime(value) !== this.lastTime || getLocale(value) !== this.lastLocale || omitSuffix !== this.lastOmitSuffix;
    }
}

function getTime(value: dayjs.ConfigType): number {
    if (isDate(value)) {
        return (value as Date).getTime();
    } else if (dayjs.isDayjs(value)) {
        return value.valueOf();
    } else {
        return dayjs(value).valueOf();
    }
}

function getSecondsUntilUpdate(dayjsInstance: dayjs.Dayjs) {
    const howOld = Math.abs(dayjs().diff(dayjsInstance, 'minute'));
    if (howOld < 1) {
        return 1;
    } else if (howOld < 60) {
        return 30;
    } else if (howOld < 180) {
        return 300;
    } else {
        return 3600;
    }
}

function getLocale(value: dayjs.ConfigType): string {
    return dayjs.isDayjs(value) ? value.locale() : dayjs.locale();
}
