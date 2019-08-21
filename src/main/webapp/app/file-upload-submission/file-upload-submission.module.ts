import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArtemisSharedModule } from 'app/shared';
import { ArtemisResultModule } from 'app/entities/result';
import { fileUploadSubmissionRoute } from './file-upload-submission.route';
import { FileUploadSubmissionComponent } from './file-upload-submission.component';
import { ArtemisComplaintsModule } from 'app/complaints';

const ENTITY_STATES = [...fileUploadSubmissionRoute];

@NgModule({
    declarations: [FileUploadSubmissionComponent],
    entryComponents: [FileUploadSubmissionComponent],
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), ArtemisResultModule, ArtemisComplaintsModule],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
})
export class ArtemisFileUploadSubmissionModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
