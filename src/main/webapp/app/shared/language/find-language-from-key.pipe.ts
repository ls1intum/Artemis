import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'findLanguageFromKey' })
export class FindLanguageFromKeyPipe implements PipeTransform {
    private languages: any = {
        en: { name: 'English' },
        de: { name: 'Deutsch' },
        // jhipster-needle-i18n-language-key-pipe - JHipster will add/remove languages in this object
    };

    /**
     * @function transform
     * Returns the string value of a language
     * @param lang { string }
     */
    transform(lang: string): string {
        return this.languages[lang].name;
    }
}
