import { Renderer2, RendererFactory2 } from '@angular/core';
import { TestBed, inject } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { LocaleConversionService } from 'app/shared/service/locale-conversion.service';
import { MockProvider } from 'ng-mocks';
import { SessionStorageService } from 'ngx-webstorage';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';

describe('Language Helper', () => {
    const renderer2: Renderer2 = {
        setAttribute: () => {},
    } as unknown as Renderer2;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                JhiLanguageHelper,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
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
        };

        const languageChangeSpy = jest.spyOn(service, 'getNavigatorReference').mockReturnValue(navigator);
        expect(service.determinePreferredLanguage()).toBe('de');
        expect(languageChangeSpy).toHaveBeenCalledOnce();
    }));

    it('determinePreferredLanguage should return english if no other language matches', inject([JhiLanguageHelper], (service: JhiLanguageHelper) => {
        const navigator = {
            languages: ['elvish', 'orcish'],
        };

        const languageChangeSpy = jest.spyOn(service, 'getNavigatorReference').mockReturnValue(navigator);
        expect(service.determinePreferredLanguage()).toBe('en');
        expect(languageChangeSpy).toHaveBeenCalledOnce();
    }));
});
