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
 *   {{ course.startDate | artemisDate: 'short-date' }}
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
    transform(dateTime: Date | moment.Moment | string | number | null, format: 'short' | 'long' | 'short-date' | 'long-date' | 'time' = 'long', seconds = false): string {
        // Return empty string if given dateTime equals null or is not convertible to moment.
        if (!dateTime || !moment(dateTime).isValid()) {
            return '';
        }
        this.dateTime = moment(dateTime);
        this.long = format === 'long' || format === 'long-date';
        this.showDate = format !== 'time';
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

    /**
     * Returns a localized moment.js format string.
     * WARNING: As this method is static it cannot listen to changes of the current locale itself. It also does not take into account the device width.
     * @param locale The locale string of the desired language. Defaults to 'en'.
     * @param format Format of the localized date time. Defaults to 'long'.
     * @param seconds Should seconds be displayed? Defaults to false.
     */
    static format(locale = 'en', format: 'short' | 'long' | 'short-date' | 'long-date' | 'time' = 'long', seconds = false): string {
        const long = format === 'long' || format === 'long-date';
        const showDate = format !== 'time';
        const showTime = format !== 'short-date' && format !== 'long-date';
        const dateFormat = ArtemisDatePipe.dateFormat(long, showDate, locale);
        const timeFormat = ArtemisDatePipe.timeFormat(showTime, seconds);
        return dateFormat + (dateFormat && timeFormat ? ' ' : '') + timeFormat;
    }

    private updateLocale(lang: string): void {
        if (this.locale === undefined && lang === undefined) {
            this.locale = 'en';
        }
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
        const dateFormat = ArtemisDatePipe.dateFormat(this.long, this.showDate, this.locale);
        const timeFormat = ArtemisDatePipe.timeFormat(this.showTime, this.showSeconds);
        return dateFormat + (dateFormat && timeFormat ? ' ' : '') + timeFormat;
    }

    private static dateFormat(long: boolean, showDate: boolean, locale: string): string {
        if (!showDate) {
            return '';
        }
        let format = 'll';
        if (!long) {
            switch (locale) {
                case 'de':
                    format = 'DD.MM.YYYY';
                    break;
                default:
                    format = 'YYYY-MM-DD';
            }
        }
        return format;
    }

    private static timeFormat(showTime: boolean, showSeconds: boolean): string {
        if (!showTime) {
            return '';
        }
        let format = 'HH:mm';
        if (showSeconds) {
            format = 'HH:mm:ss';
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
