import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
    name: 'findLanguageFromKey',
    standalone: false,
})
export class FindLanguageFromKeyPipe implements PipeTransform {
    private languages: any = {
        en: { name: 'English' },
        de: { name: 'Deutsch' },
        // jhipster-needle-i18n-language-key-pipe - JHipster will add/remove languages in this object
    };
    transform(lang: string): string {
        return this.languages[lang].name;
    }
}
