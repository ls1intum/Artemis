import { HttpClient } from '@angular/common/http';
import { MissingTranslationHandlerParams } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';
import { missingTranslationHandler, translatePartialLoader } from 'app/core/config/translation.config';

describe('translation config', () => {
    it('should return the key with error prefix', () => {
        const missingTranslationHandlerImpl = missingTranslationHandler();
        expect(missingTranslationHandlerImpl.handle({ key: 'foobar' } as MissingTranslationHandlerParams)).toBe('translation-not-found[foobar]');
    });

    it('translatePartialLoader should return a TranslateHttpLoader', () => {
        expect(translatePartialLoader({} as HttpClient)).toBeInstanceOf(TranslateHttpLoader);
    });
});
