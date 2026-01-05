import { Injectable, Pipe, PipeTransform, inject } from '@angular/core';
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

    transform(query: string | undefined | null, args?: any): string {
        if (!query || query.length === 0) {
            return query ?? '';
        }
        const translation = this.translateService.instant(query, args);
        return translation !== undefined ? translation : query;
    }
}
