import { Injectable, Pipe, PipeTransform, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';

@Pipe({
    name: 'artemisTranslate',
    pure: false,
})
// needed to be injectable in the notification.service
@Injectable({ providedIn: 'root' })

/**
 * a simple wrapper to prevent compile errors in IntelliJ
 * Uses TranslateService.instant() for synchronous translation
 */
export class ArtemisTranslatePipe implements PipeTransform {
    private translateService = inject(TranslateService);
    // Reading this signal in transform() ties the host view's change detection to language switches,
    // so OnPush/static views (e.g. the logged-out landing page) re-render when the active language
    // changes — required under zoneless change detection. toSignal self-cleans via DestroyRef.
    private languageChange = toSignal(this.translateService.onLangChange);

    transform(query: string | undefined | null, args?: any): string {
        if (!query || query.length === 0) {
            return query ?? '';
        }
        this.languageChange();
        const translation = this.translateService.instant(query, args);
        return translation !== undefined ? translation : query;
    }
}
