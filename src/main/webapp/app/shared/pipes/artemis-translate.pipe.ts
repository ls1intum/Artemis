import { ChangeDetectorRef, OnDestroy, Pipe, PipeTransform } from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

@Pipe({
    name: 'artemisTranslate',
    pure: false,
})
/**
 * A simple wrapper to prevent compile errors in IntelliJ
 */
export class ArtemisTranslatePipe implements PipeTransform, OnDestroy {
    private translatePipe: TranslatePipe;

    // Note: we cannot use inject here, for some reason it does not work
    constructor(translateService: TranslateService, changeDetectorRef: ChangeDetectorRef) {
        this.translatePipe = new TranslatePipe(translateService, changeDetectorRef);
    }

    transform(query?: string, args?: any): string | undefined {
        if (!query || !query.length) {
            return query;
        }
        // NOTE: the underlying implementation returns a string even though the method return type is specified as any
        return this.translatePipe.transform(query, args) as string;
    }

    ngOnDestroy() {
        this.translatePipe.ngOnDestroy();
    }
}
