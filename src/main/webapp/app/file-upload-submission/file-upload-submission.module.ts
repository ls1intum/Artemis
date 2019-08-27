/* angular */
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

/* application */
import { JhiLanguageHelper } from 'app/core';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisResultModule } from 'app/entities/result';
import { fileUploadSubmissionRoute } from './file-upload-submission.route';
import { FileUploadSubmissionComponent } from './file-upload-submission.component';
import { ArtemisComplaintsModule } from 'app/complaints';
import { FileUploadResultComponent } from 'app/file-upload-submission/file-upload-result/file-upload-result.component';

/* 3rd party */
import { JhiLanguageService } from 'ng-jhipster';

const ENTITY_STATES = [...fileUploadSubmissionRoute];

@NgModule({
    declarations: [FileUploadSubmissionComponent, FileUploadResultComponent],
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
