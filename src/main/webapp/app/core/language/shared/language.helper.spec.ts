import { TestBed, inject } from '@angular/core/testing';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import { Title } from '@angular/platform-browser';
import { LocaleConversionService } from 'app/shared/service/locale-conversion.service';
import { MockProvider } from 'ng-mocks';
import { Renderer2, RendererFactory2 } from '@angular/core';

describe('Language Helper', () => {
    const renderer2: Renderer2 = {
        setAttribute: () => {},
    } as unknown as Renderer2;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                JhiLanguageHelper,
                { provide: TranslateService, useClass: MockTranslateService },
                SessionStorageService,
                { provide: Router, useClass: MockRouter },
                MockProvider(LocaleConversionService),
                MockProvider(Title),
                MockProvider(RendererFactory2, {
                    createRenderer: () => renderer2,
                }),
            ],
        });
    });

    it('determinePreferredLanguages should respect user preference', inject([JhiLanguageHelper], (service: JhiLanguageHelper) => {
        const navigator = {
            languages: ['de', 'en'],
        } as unknown as Navigator;

        const languageChangeSpy = jest.spyOn(service, 'getNavigatorReference').mockReturnValue(navigator);
        expect(service.determinePreferredLanguage()).toBe('de');
        expect(languageChangeSpy).toHaveBeenCalledOnce();
    }));

    it('determinePreferredLanguage should return english if no other language matches', inject([JhiLanguageHelper], (service: JhiLanguageHelper) => {
        const navigator = {
            languages: ['elvish', 'orcish'],
        } as unknown as Navigator;

        const languageChangeSpy = jest.spyOn(service, 'getNavigatorReference').mockReturnValue(navigator);
        expect(service.determinePreferredLanguage()).toBe('en');
        expect(languageChangeSpy).toHaveBeenCalledOnce();
    }));
});
