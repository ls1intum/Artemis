import { Provider } from '@angular/core';
import { MissingTranslationHandler, MissingTranslationHandlerParams } from '@ngx-translate/core';
import { splitTranslateLoaderProviders } from 'app/core/config/split-translate-loader';

export const translationNotFoundMessage = 'translation-not-found';

export class MissingTranslationHandlerImpl implements MissingTranslationHandler {
    handle(params: MissingTranslationHandlerParams): string {
        const key = params.key;
        return `${translationNotFoundMessage}[${key}]`;
    }
}

/* Re-exported under the historical name to keep `app.config.ts` and other call sites untouched
   after we replaced the plain http-loader with a landing/full split loader. */
export const translateHttpLoaderProviders: Provider[] = splitTranslateLoaderProviders;

export function missingTranslationHandler(): MissingTranslationHandler {
    return new MissingTranslationHandlerImpl();
}
