import { Injectable, Pipe, PipeTransform, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { EMPTY } from 'rxjs';

@Pipe({
    name: 'artemisTranslate',
    pure: false,
})
// needed to be injectable in the notification.service
@Injectable({ providedIn: 'root' })
export class ArtemisTranslatePipe implements PipeTransform {
    private translateService = inject(TranslateService);

    // Reading this signal in transform() re-renders the host view on language change (required under zoneless change detection).
    private languageChange = toSignal(this.translateService.onLangChange ?? EMPTY);

    transform(query: string | undefined | null, args?: any): string {
        if (!query || query.length === 0) {
            return query ?? '';
        }
        this.languageChange();
        const translation = this.translateService.instant(query, args);
        return translation !== undefined ? translation : query;
    }
}
