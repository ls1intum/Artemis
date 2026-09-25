import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { TUM_AET_UI_TRANSLATOR } from '@tumaet/ui-angular';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideArtemisTumAetUiTranslator } from './artemis-tumaet-ui-translator';

describe('provideArtemisTumAetUiTranslator', () => {
    it('bridges translations and language-change invalidation to the package contract', () => {
        TestBed.configureTestingModule({
            providers: [provideArtemisTumAetUiTranslator(), { provide: TranslateService, useClass: MockTranslateService }],
        });

        const translator = TestBed.inject(TUM_AET_UI_TRANSLATOR);
        const translateService = TestBed.inject(TranslateService);

        expect(translator.translate('tumAetUi.datePicker.dialog')).toBe('global.datePicker.dialog');
        expect(translator.translationChanges?.()).toBeUndefined();

        translateService.use('de');
        expect(translator.translationChanges?.()).toEqual({ lang: 'de' });
    });
});
