import { Pipe, PipeTransform, inject } from '@angular/core';
import { TUM_AET_UI_TRANSLATOR, TumAetUiTranslationParams } from './tumaet-ui-translations';

@Pipe({
    name: 'tumAetUiTranslate',
    pure: false,
})
export class TumAetUiTranslatePipe implements PipeTransform {
    private readonly translator = inject(TUM_AET_UI_TRANSLATOR);

    transform(key: null | string | undefined, params?: TumAetUiTranslationParams): string {
        if (!key) {
            return '';
        }
        this.translator.translationChanges?.();
        return this.translator.translate(key, params);
    }
}
