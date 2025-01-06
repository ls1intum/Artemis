import { ChangeDetectorRef, OnDestroy, Pipe, PipeTransform, inject } from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

@Pipe({
    name: 'artemisTranslate',
    pure: false,
    standalone: true,
})
/**
 * a simple wrapper to prevent compile errors in IntelliJ
 */
export class ArtemisTranslatePipe implements PipeTransform, OnDestroy {
    private translatePipe: TranslatePipe;

    /**
     * We cannot inject() ChangeDetectorRef here, since this is a pipe (other than a component where its possible)
     * However, in this case due to being a wrapper class for TranslatePipe, which implements the same constructor
     * this exception should be okay.
     */
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
