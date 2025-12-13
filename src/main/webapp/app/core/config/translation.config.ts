import { MissingTranslationHandler, MissingTranslationHandlerParams } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
import { I18N_HASH } from 'app/core/environments/environment';

export const translationNotFoundMessage = 'translation-not-found';

export class MissingTranslationHandlerImpl implements MissingTranslationHandler {
    handle(params: MissingTranslationHandlerParams): string {
        const key = params.key;
        return `${translationNotFoundMessage}[${key}]`;
    }
}

export const translateHttpLoaderProviders = provideTranslateHttpLoader({
    prefix: 'i18n/',
    suffix: `.json?_=${I18N_HASH}`,
});

export function missingTranslationHandler(): MissingTranslationHandler {
    return new MissingTranslationHandlerImpl();
}
