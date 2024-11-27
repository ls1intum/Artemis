import { ChangeDetectorRef, OnDestroy, Pipe, PipeTransform } from '@angular/core';
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

    // We cannot inject() ChangeDetectorRef here
    constructor(translateService: TranslateService, changeDetectorRef: ChangeDetectorRef) {
        this.translatePipe = new TranslatePipe(translateService, changeDetectorRef);
    }

    transform(query: any, args?: any): any {
        return this.translatePipe.transform(query, args);
    }

    ngOnDestroy() {
        this.translatePipe.ngOnDestroy();
    }
}
