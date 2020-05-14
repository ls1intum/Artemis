import { OnDestroy, Pipe, PipeTransform } from '@angular/core';
import { LangChangeEvent, TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import * as moment from 'moment';

/**
 * Format a given date time that must be convertible to a moment object to a localized date time
 * string based on the current language setting.
 * This pipe is stateful (pure = false) so that it can adapt to changes of the current locale.
 * Usage:
 *   dateTime | artemisDate:format:time:seconds
 * Examples (for locale == 'en'):
 *   {{ course.startDate | artemisDate }}
 *   formats to: Dec 17, 2019 12:43:05 AM
 *   {{ course.startDate | artemisDate:'short' }}
 *   formats to: 17/12/19 00:43:05
 */
@Pipe({
    name: 'artemisDate',
    pure: false,
})
export class ArtemisDatePipe implements PipeTransform, OnDestroy {
    private dateTime: moment.Moment;
    private short = false;
    private time = true;
    private seconds = true;
    private locale: string;
    private localizedDateTime: string;
    private onLangChange: Subscription | undefined;

    constructor(private translateService: TranslateService) {}

    /**
     * Format a given dateTime to a localized date time string based on the current language setting.
     * @param dateTime The date time that should be formatted. Must be convertible to moment().
     * @param format Defines the length of the localized format. Defaults to 'long'.
     * @param time Should the time be displayed? Defaults to true.
     * @param seconds Should seconds be displayed? Defaults to true.
     * @return string
     */
    transform(dateTime: Date | moment.Moment | string | number | null, format: 'short' | 'long' = 'long', time = true, seconds = true): string {
        // Return empty string if given dateTime is not convertible to moment or equals null.
        if (dateTime === null || !moment(dateTime).isValid()) {
            return '';
        }
        this.dateTime = moment(dateTime);
        this.short = format === 'short';
        this.time = time;
        this.seconds = seconds;

        // Set locale to current language.
        this.updateLocale(this.translateService.currentLang);

        // Clean up existing subscription to onLangChange.
        this.cleanUpSubscription();

        // Subscribe to onLangChange event, in case the language changes.
        if (!this.onLangChange) {
            this.onLangChange = this.translateService.onLangChange.subscribe((event: LangChangeEvent) => this.updateLocale(event.lang));
        }

        return this.localizedDateTime;
    }

    private updateLocale(lang: string): void {
        if (lang !== this.locale) {
            this.locale = lang;
            this.updateLocalizedDateTime();
        }
    }

    private updateLocalizedDateTime(): void {
        this.dateTime.locale(this.locale);
        this.localizedDateTime = this.dateTime.format(this.format());
    }

    private format(): string {
        // Evaluate date format.
        let dateFormat = 'll';
        if (this.short) {
            switch (this.locale) {
                case 'de':
                    dateFormat = 'D.M.YY';
                    break;
                default:
                    dateFormat = 'D/M/YY';
            }
        }

        // Return date format immediately if time is set to false.
        if (!this.time) {
            return dateFormat;
        }

        // Evaluate time format.
        let timeFormat = 'LTS';
        if (!this.short && !this.seconds) {
            timeFormat = 'LT';
        } else if (this.short && this.seconds) {
            timeFormat = 'HH:mm:ss';
        } else if (this.short && !this.seconds) {
            timeFormat = 'HH:mm';
        }
        return dateFormat + ' ' + timeFormat;
    }

    private cleanUpSubscription(): void {
        if (typeof this.onLangChange !== 'undefined') {
            this.onLangChange.unsubscribe();
            this.onLangChange = undefined;
        }
    }

    /**
     * Unsubscribe from onLangChange event of translation service on pipe destruction.
     */
    ngOnDestroy(): void {
        this.cleanUpSubscription();
    }
}
