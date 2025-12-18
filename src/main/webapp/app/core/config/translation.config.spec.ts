import { MissingTranslationHandlerParams } from '@ngx-translate/core';
import { missingTranslationHandler, translateHttpLoaderProviders } from 'app/core/config/translation.config';

describe('translation config', () => {
    it('should return the key with error prefix', () => {
        const missingTranslationHandlerImpl = missingTranslationHandler();
        expect(missingTranslationHandlerImpl.handle({ key: 'foobar' } as MissingTranslationHandlerParams)).toBe('translation-not-found[foobar]');
    });

    it('translateHttpLoaderProviders should return provider array', () => {
        expect(translateHttpLoaderProviders).toBeDefined();
        expect(translateHttpLoaderProviders).toBeArray();
    });
});
