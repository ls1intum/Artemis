import { Pipe, PipeTransform } from '@angular/core';
import { LangChangeEvent } from '@ngx-translate/core';
import { BehaviorSubject, Observable, of, Subject } from 'rxjs';
import { LANGUAGES } from 'app/core/language/shared/language.constants';
import { ActivatedRouteSnapshot } from '@angular/router';

export const TRANSLATED_STRING = '';

export class MockTranslateService {
    onLangChangeSubject: Subject<LangChangeEvent> = new Subject();
    onTranslationChangeSubject: Subject<string> = new Subject();
    onDefaultLangChangeSubject: Subject<string> = new Subject();
    isLoadedSubject: BehaviorSubject<boolean> = new BehaviorSubject(true);

    onLangChange: Observable<LangChangeEvent> = this.onLangChangeSubject.asObservable();
    onTranslationChange: Observable<string> = this.onTranslationChangeSubject.asObservable();
    onDefaultLangChange: Observable<string> = this.onDefaultLangChangeSubject.asObservable();
    isLoaded: Observable<boolean> = this.isLoadedSubject.asObservable();

    currentLang: string;

    languages: string[] = ['de'];

    get(content: string): Observable<string> {
        return of(TRANSLATED_STRING + content);
    }

    use(lang: string): void {
        this.currentLang = lang;
        this.onLangChangeSubject.next({ lang } as LangChangeEvent);
    }

    addLangs(langs: string[]): void {
        this.languages = [...this.languages, ...langs];
    }

    getBrowserLang(): string {
        return '';
    }

    getLangs(): string[] {
        return this.languages;
    }

    getTranslation(): Observable<any> {
        return of({});
    }

    instant(key: string | string[], interpolateParams?: object): string {
        return TRANSLATED_STRING + key.toString();
    }

    setDefaultLang(lang: string): void {
        this.onDefaultLangChangeSubject.next(lang);
    }
}

export class MockLanguageHelper {
    private _language: BehaviorSubject<string> = new BehaviorSubject('en');

    /**
     * Get all supported ISO_639-1 language codes.
     */
    getAll(): string[] {
        return LANGUAGES;
    }

    get language(): Observable<string> {
        return this._language.asObservable();
    }
    updateTitle(titleKey?: string) {}

    // @ts-ignore
    private getPageTitle(routeSnapshot: ActivatedRouteSnapshot) {
        return '';
    }

    public determinePreferredLanguage(): string {
        return 'en';
    }
}

@Pipe({
    name: 'artemisTranslate',
})
export class TranslatePipeMock implements PipeTransform {
    public name = 'artemisTranslate';

    public transform(query: string, ...args: any[]): any {
        return query + (args && args.length > 0 ? ': ' + JSON.stringify(args) : '');
    }
}
