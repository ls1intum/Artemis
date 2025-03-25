import { HttpClient } from '@angular/common/http';
import { MissingTranslationHandler, MissingTranslationHandlerParams, TranslateLoader } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';
import { I18N_HASH } from 'app/core/environments/environment';

export const translationNotFoundMessage = 'translation-not-found';

export class MissingTranslationHandlerImpl implements MissingTranslationHandler {
    handle(params: MissingTranslationHandlerParams): string {
        const key = params.key;
        return `${translationNotFoundMessage}[${key}]`;
    }
}

export function translatePartialLoader(http: HttpClient): TranslateLoader {
    return new TranslateHttpLoader(http, 'i18n/', `.json?_=${I18N_HASH}`);
}

export function missingTranslationHandler(): MissingTranslationHandler {
    return new MissingTranslationHandlerImpl();
}
