import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { ArTEMiSResultModule } from 'app/entities/result';
import { fileUploadSubmissionRoute } from './file-upload-submission.route';
import { FileUploadSubmissionComponent } from './file-upload-submission.component';
import { ArTEMiSComplaintsModule } from 'app/complaints';

const ENTITY_STATES = [...fileUploadSubmissionRoute];

@NgModule({
    declarations: [FileUploadSubmissionComponent],
    entryComponents: [FileUploadSubmissionComponent],
    imports: [ArTEMiSSharedModule, RouterModule.forChild(ENTITY_STATES), ArTEMiSResultModule, ArTEMiSComplaintsModule],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
})
export class ArTEMiSFileUploadSubmissionModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
