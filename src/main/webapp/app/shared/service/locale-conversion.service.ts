import { Injectable } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';

@Injectable({
    providedIn: 'root',
})
export class LocaleConversionService {
    locale = this.getLang(); // default value, will be overridden by the current language of Artemis

    constructor(private languageService: JhiLanguageService) {
        this.locale = this.languageService.getCurrentLanguage();
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
    private getLang() {
        if (navigator.languages !== undefined) {
            return navigator.languages[0];
        } else {
            return navigator.language;
        }
    }
}
