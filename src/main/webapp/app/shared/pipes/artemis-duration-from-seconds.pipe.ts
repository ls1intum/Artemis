import { OnDestroy, Pipe, PipeTransform } from '@angular/core';
import { LangChangeEvent, TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';

@Pipe({
    name: 'artemisDurationFromSeconds',
    pure: false,
})
export class ArtemisDurationFromSecondsPipe implements PipeTransform, OnDestroy {
    private readonly secondsInDay = 60 * 60 * 24;
    private readonly secondsInHour = 60 * 60;
    private readonly secondsInMinute = 60;

    private seconds: number;
    private locale: string;
    private localizedDuration: string;
    private onLangChange?: Subscription;

    constructor(private translateService: TranslateService) {}

    /**
     * Convert seconds to a human-readable duration format "d day(s) hh:mm:ss".
     * The days and hours are left out if their value is zero
     * @param seconds {number}
     */
    transform(seconds: number): string {
        this.seconds = seconds;

        const days = Math.floor(seconds / this.secondsInDay);
        const hours = Math.floor((seconds % this.secondsInDay) / this.secondsInHour);
        const minutes = Math.floor((seconds % this.secondsInHour) / this.secondsInMinute);
        seconds = seconds % this.secondsInMinute;

        let timeString = this.transformDays(days);

        if (days > 0 || hours > 0) {
            timeString += this.addLeadingZero(hours) + ':';
        }

        timeString += this.addLeadingZero(minutes) + ':';
        timeString += this.addLeadingZero(seconds);

        return timeString;
    }

    private addLeadingZero(number: number): string {
        let numberOut = number.toString();
        if (number < 10) {
            numberOut = '0' + numberOut;
        }
        return numberOut;
    }

    private transformDays(days: number): string {
        if (days === 0) {
            return '';
        }
        // Set locale to current language.
        this.updateLocale(this.translateService.currentLang);

        // Clean up existing subscription to onLangChange and subscribe to onLangChange event, in case the language changes.
        this.cleanUpSubscription();
        if (!this.onLangChange) {
            this.onLangChange = this.translateService.onLangChange.subscribe((event: LangChangeEvent) => this.updateLocale(event.lang));
        }

        return days + this.getDayString(days);
    }

    private getDayString(days: number): string {
        if (this.locale === 'de') {
            return days > 1 ? ' Tage ' : ' Tag ';
        } else {
            return days > 1 ? ' days ' : ' day ';
        }
    }

    private updateLocale(lang: string): void {
        if (this.locale === undefined && lang === undefined) {
            this.locale = 'en';
        }
        if (lang !== this.locale) {
            this.locale = lang;
            this.localizedDuration = this.transform(this.seconds);
        }
    }

    private cleanUpSubscription(): void {
        if (this.onLangChange !== undefined) {
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
