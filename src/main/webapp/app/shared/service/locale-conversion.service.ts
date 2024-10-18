import { Injectable, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

@Injectable({
    providedIn: 'root',
})
export class LocaleConversionService {
    private translateService = inject(TranslateService);

    locale = LocaleConversionService.getLang(); // default value, will be overridden by the current language of Artemis

    constructor() {
        this.locale = this.translateService.currentLang;
    }

    /**
     * Convert a number value to a locale string.
     * @param value
     * @param maximumFractionDigits
     */
    toLocaleString(value: number, maximumFractionDigits = 1) {
        const options: Intl.NumberFormatOptions = {
            maximumFractionDigits,
        };

        if (isNaN(value)) {
            return '-';
        } else {
            return value.toLocaleString(this.locale, options);
        }
    }

    /**
     * Convert a number value to a locale string with a % added at the end.
     * @param value
     * @param maximumFractionDigits
     */
    toLocalePercentageString(value: number, maximumFractionDigits = 1) {
        const options: Intl.NumberFormatOptions = {
            maximumFractionDigits,
        };

        if (isNaN(value)) {
            return '-';
        } else {
            return value.toLocaleString(this.locale, options) + '%';
        }
    }

    /**
     * Get the language set by the user.
     */
    private static getLang() {
        if (navigator.languages !== undefined) {
            return navigator.languages[0];
        } else {
            return navigator.language;
        }
    }
}
