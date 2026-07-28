import { Pipe, PipeTransform, inject } from '@angular/core';
import { TUM_UI_TRANSLATOR, TumUiTranslationParams } from './tum-ui-translations';

@Pipe({
    name: 'tumUiTranslate',
    pure: false,
})
export class TumUiTranslatePipe implements PipeTransform {
    private readonly translator = inject(TUM_UI_TRANSLATOR);

    transform(key: null | string | undefined, params?: TumUiTranslationParams): string {
        if (!key) {
            return '';
        }
        this.translator.changes?.();
        return this.translator.translate(key, params);
    }
}
