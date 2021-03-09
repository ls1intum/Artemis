import { Pipe, PipeTransform } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

@Pipe({
    name: 'artemisTranslate',
    pure: false,
})
/**
 * a simple wrapper to prevent compile errors in IntelliJ
 */
export class ArtemisTranslatePipe implements PipeTransform {
    constructor(private translatePipe: TranslatePipe) {}

    transform(query: any, args?: any): any {
        return this.translatePipe.transform(query, args);
    }
}
