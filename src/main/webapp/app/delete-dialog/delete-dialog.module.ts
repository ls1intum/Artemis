import { NgModule } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArtemisSharedModule } from 'app/shared';
import { DeleteDialogComponent } from 'app/delete-dialog/delete-dialog.component';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [DeleteDialogComponent],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
    exports: [DeleteDialogComponent],
})
export class ArtemisDeleteDialogModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
