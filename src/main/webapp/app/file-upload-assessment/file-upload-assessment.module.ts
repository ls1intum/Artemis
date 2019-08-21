/* angular */
import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

/* 3rd party */
import { JhiLanguageService } from 'ng-jhipster';

/* application */
import { JhiLanguageHelper } from 'app/core';
import { fileUploadAssessmentRoutes } from './file-upload-assessment.route';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisResultModule } from 'app/entities/result';
import { SortByModule } from 'app/components/pipes';
import { ArtemisComplaintsForTutorModule } from 'app/complaints-for-tutor';
import { FileUploadAssessmentComponent } from './file-upload-assessment.component';
import { ArtemisTextAssessmentModule } from 'app/text-assessment';
import { FileUploadAssessmentDetailComponent } from 'app/file-upload-assessment/file-upload-assessment-detail/file-upload-assessment-detail.component';

const ENTITY_STATES = [...fileUploadAssessmentRoutes];
@NgModule({
    imports: [
        CommonModule,
        SortByModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisSharedModule,
        ArtemisResultModule,
        ArtemisComplaintsForTutorModule,
        ArtemisTextAssessmentModule,
    ],
    declarations: [FileUploadAssessmentComponent, FileUploadAssessmentDetailComponent],
    exports: [FileUploadAssessmentComponent],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArtemisFileUploadAssessmentModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
