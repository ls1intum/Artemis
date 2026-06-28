import { ChangeDetectorRef, Injectable, OnDestroy, Pipe, PipeTransform, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';

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
export class ArtemisTranslatePipe implements PipeTransform, OnDestroy {
    private translateService = inject(TranslateService);
    // Optional: absent when the pipe is injected as a service (e.g. notification.service) rather than used in a template.
    private changeDetectorRef = inject(ChangeDetectorRef, { optional: true });

    private onLangChangeSubscription?: Subscription;

    transform(query: string | undefined | null, args?: any): string {
        if (!query || query.length === 0) {
            return query ?? '';
        }
        // The impure pipe only re-runs when change detection runs. On OnPush components with no other
        // activity (e.g. the logged-out landing page) switching language would otherwise leave stale text,
        // so mark the host for check when the active language changes — like the upstream ngx-translate pipe.
        if (this.changeDetectorRef && !this.onLangChangeSubscription) {
            this.onLangChangeSubscription = this.translateService.onLangChange.subscribe(() => this.changeDetectorRef!.markForCheck());
        }
        const translation = this.translateService.instant(query, args);
        return translation !== undefined ? translation : query;
    }

    ngOnDestroy(): void {
        this.onLangChangeSubscription?.unsubscribe();
    }
}
