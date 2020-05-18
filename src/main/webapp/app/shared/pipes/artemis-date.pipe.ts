import { OnDestroy, Pipe, PipeTransform } from '@angular/core';
import { LangChangeEvent, TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import * as moment from 'moment';

/**
 * Format a given date time that must be convertible to a moment object to a localized date time
 * string based on the current language setting. Always returns the short format on mobile devices.
 * This pipe is stateful (pure = false) so that it can adapt to changes of the current locale.
 * Usage:
 *   dateTime | artemisDate:format:seconds
 * Examples (for locale == 'en'):
 *   {{ course.startDate | artemisDate }}
 *   formats to: Dec 17, 2019 12:43 AM
 *   {{ course.startDate | artemisDate:'short-date' }}
 *   formats to: 17/12/19
 */
@Pipe({
    name: 'artemisDate',
    pure: false,
})
export class ArtemisDatePipe implements PipeTransform, OnDestroy {
    private dateTime: moment.Moment;
    private locale: string;
    private localizedDateTime: string;
    private onLangChange: Subscription | undefined;
    private long = true;
    private showDate = true;
    private showTime = true;
    private showSeconds = false;
    private static mobileDeviceSize = 768;

    constructor(private translateService: TranslateService) {}

    /**
     * Format a given dateTime to a localized date time string based on the current language setting.
     * @param dateTime The date time that should be formatted. Must be convertible to moment().
     * @param format Format of the localized date time. Defaults to 'long'.
     * @param seconds Should seconds be displayed? Defaults to false.
     */
    transform(
        dateTime: Date | moment.Moment | string | number | null,
        format: 'short' | 'long' | 'short-date' | 'long-date' | 'short-time' | 'long-time' = 'long',
        seconds = false,
    ): string {
        // Return empty string if given dateTime equals null or is not convertible to moment.
        if (dateTime === null || !moment(dateTime).isValid()) {
            return '';
        }
        this.dateTime = moment(dateTime);
        this.long = format === 'long' || format === 'long-date' || format === 'long-time';
        this.showDate = format !== 'short-time' && format !== 'long-time';
        this.showTime = format !== 'short-date' && format !== 'long-date';
        this.showSeconds = seconds;

        // Evaluate the format length based on the current window width.
        this.formatLengthBasedOnWindowWidth(window.innerWidth);

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
        const dateFormat = this.dateFormat();
        const timeFormat = this.timeFormat();
        return dateFormat + (dateFormat && timeFormat ? ' ' : '') + timeFormat;
    }

    private dateFormat(): string {
        if (!this.showDate) {
            return '';
        }
        let format = 'll';
        if (!this.long) {
            switch (this.locale) {
                case 'de':
                    format = 'D.M.YY';
                    break;
                default:
                    format = 'D/M/YY';
            }
        }
        return format;
    }

    private timeFormat(): string {
        if (!this.showTime) {
            return '';
        }
        let format = 'LTS';
        if (this.long && !this.showSeconds) {
            format = 'LT';
        } else if (!this.long && this.showSeconds) {
            format = 'H:m:s';
        } else if (!this.long && !this.showSeconds) {
            format = 'H:m';
        }
        return format;
    }

    private formatLengthBasedOnWindowWidth(windowWidth: number): void {
        if (windowWidth <= ArtemisDatePipe.mobileDeviceSize) {
            this.long = false;
        }
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
