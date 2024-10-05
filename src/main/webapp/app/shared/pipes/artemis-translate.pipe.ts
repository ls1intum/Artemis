import { ChangeDetectorRef, OnDestroy, Pipe, PipeTransform, inject } from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

@Pipe({
    name: 'artemisTranslate',
    pure: false,
})
/**
 * a simple wrapper to prevent compile errors in IntelliJ
 */
export class ArtemisTranslatePipe implements PipeTransform, OnDestroy {
    private translatePipe: TranslatePipe;
    constructor() {
        const translateService = inject(TranslateService);
        const changeDetectorRef = inject(ChangeDetectorRef);

        this.translatePipe = new TranslatePipe(translateService, changeDetectorRef);
    }

    transform(query: any, args?: any): any {
        return this.translatePipe.transform(query, args);
    }

    ngOnDestroy() {
        this.translatePipe.ngOnDestroy();
    }
}
