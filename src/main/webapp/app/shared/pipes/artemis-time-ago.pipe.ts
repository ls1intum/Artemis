import { ChangeDetectorRef, NgZone, OnDestroy, Pipe, PipeTransform, inject } from '@angular/core';
import dayjs from 'dayjs/esm';
import { isDate } from 'app/shared/util/utils';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisServerDateService } from 'app/shared/service/server-date.service';

@Pipe({
    name: 'artemisTimeAgo',
    pure: false,
})
export class ArtemisTimeAgoPipe implements PipeTransform, OnDestroy {
    private cdRef = inject(ChangeDetectorRef);
    private ngZone = inject(NgZone);
    private translateService = inject(TranslateService);
    private serverDateService = inject(ArtemisServerDateService);

    private currentTimer: number | null;

    private lastTime: number;
    private lastValue: dayjs.ConfigType;
    private lastOmitSuffix?: boolean;
    private lastLocale: string;
    private lastText: string;
    private formatFn: (m: dayjs.Dayjs) => string;

    format(date: dayjs.Dayjs) {
        return date.locale(this.lastLocale).from(this.serverDateService.now(), this.lastOmitSuffix);
    }

    transform(value: dayjs.ConfigType, omitSuffix?: boolean, formatFn?: (m: dayjs.Dayjs) => string): string {
        if (this.hasChanged(value, omitSuffix)) {
            this.lastTime = getTime(value);
            this.lastValue = value;
            this.lastOmitSuffix = omitSuffix;
            this.lastLocale = this.translateService.getCurrentLang();
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
        return getTime(value) !== this.lastTime || this.translateService.getCurrentLang() !== this.lastLocale || omitSuffix !== this.lastOmitSuffix;
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
