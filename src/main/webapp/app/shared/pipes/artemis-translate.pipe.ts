import { Injectable, OnDestroy, Pipe, PipeTransform, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

@Pipe({
    name: 'artemisTranslate',
    pure: false,
})
// needed to be injectable in the notification.service
@Injectable({ providedIn: 'root' })

/**
 * a simple wrapper to prevent compile errors in IntelliJ
 */
export class ArtemisTranslatePipe implements PipeTransform, OnDestroy {
    private translatePipe = inject(TranslatePipe);

    transform(query: any, args?: any): any {
        return this.translatePipe.transform(query, args);
    }

    ngOnDestroy() {
        this.translatePipe.ngOnDestroy();
    }
}
