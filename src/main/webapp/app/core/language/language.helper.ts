import { Injectable, Renderer2, RendererFactory2 } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';

import { LANGUAGES } from './language.constants';
import { BehaviorSubject, Observable } from 'rxjs';
import { SessionStorageService } from 'ngx-webstorage';
import { LocaleConversionService } from 'app/shared/service/locale-conversion.service';

@Injectable({ providedIn: 'root' })
export class JhiLanguageHelper {
    private renderer: Renderer2;
    private _language: BehaviorSubject<string>;

    constructor(
        private translateService: TranslateService,
        private localeConversionService: LocaleConversionService,
        private titleService: Title,
        private router: Router,
        rootRenderer: RendererFactory2,
        private sessionStorage: SessionStorageService,
    ) {
        this._language = new BehaviorSubject<string>(this.translateService.currentLang);
        this.renderer = rootRenderer.createRenderer(document.querySelector('html'), null);
        this.init();
    }

    /**
     * Get all supported ISO_639-1 language codes.
     */
    getAll(): string[] {
        return LANGUAGES;
    }

    get language(): Observable<string> {
        return this._language.asObservable();
    }

    /**
     * Update the window title using params in the following
     * order:
     * 1. titleKey parameter
     * 2. $state.$current.data.pageTitle (current state page title)
     * 3. 'global.title'
     */
    updateTitle(titleKey?: string) {
        if (!titleKey) {
            titleKey = this.getPageTitle(this.router.routerState.snapshot.root);
        }

        this.translateService.get(titleKey).subscribe((title) => {
            this.titleService.setTitle(title);
        });
    }

    private init() {
        this.translateService.onLangChange.subscribe(() => {
            const languageKey = this.translateService.currentLang;
            this._language.next(languageKey);
            this.localeConversionService.locale = languageKey;
            this.sessionStorage.store('locale', languageKey);
            this.renderer.setAttribute(document.querySelector('html'), 'lang', this.translateService.currentLang);
            this.updateTitle();
        });
    }

    private getPageTitle(routeSnapshot: ActivatedRouteSnapshot) {
        let title: string = routeSnapshot.data && routeSnapshot.data['pageTitle'] ? routeSnapshot.data['pageTitle'] : 'artemisApp';
        if (routeSnapshot.firstChild) {
            title = this.getPageTitle(routeSnapshot.firstChild) || title;
        }
        return title;
    }

    public determinePreferredLanguage(): string {
        // In the languages array the languages are ordered by preference with the most preferred language first.
        for (let i = 0; i < navigator.languages.length; i++) {
            // return the language with the highest preference
            if (this.getNavigatorReference().languages[i].startsWith('en')) {
                return 'en';
            }
            if (this.getNavigatorReference().languages[i].startsWith('de')) {
                return 'de';
            }
        }
        // english as fallback
        return 'en';
    }

    public getNavigatorReference(): any {
        return navigator;
    }
}
