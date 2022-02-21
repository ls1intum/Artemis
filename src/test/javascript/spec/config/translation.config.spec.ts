import { MissingTranslationHandlerParams } from '@ngx-translate/core';
import { MissingTranslationHandlerImpl } from 'app/core/config/translation.config';

describe('translation config', () => {
    it('should return the key with error prefix', () => {
        const missingTranslationHandlerImpl = new MissingTranslationHandlerImpl();
        expect(missingTranslationHandlerImpl.handle({ key: 'foobar' } as MissingTranslationHandlerParams)).toBe('translation-not-found[foobar]');
    });
});
