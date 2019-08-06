/* angular */
import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

/* 3rd party */
import { JhiLanguageService } from 'ng-jhipster';

/* application */
import { JhiLanguageHelper } from 'app/core';
import { fileUploadAssessmentRoutes } from './file-upload-assessment.route';
import { ArTEMiSSharedModule } from 'app/shared';
import { ArTEMiSResultModule } from 'app/entities/result';
import { SortByModule } from 'app/components/pipes';
import { ArTEMiSComplaintsForTutorModule } from 'app/complaints-for-tutor';
import { FileUploadAssessmentComponent } from './file-upload-assessment.component';

const ENTITY_STATES = [...fileUploadAssessmentRoutes];
@NgModule({
    imports: [CommonModule, SortByModule, RouterModule.forChild(ENTITY_STATES), ArTEMiSSharedModule, ArTEMiSResultModule, ArTEMiSComplaintsForTutorModule],
    declarations: [FileUploadAssessmentComponent],
    exports: [FileUploadAssessmentComponent],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArTEMiSFileUploadAssessmentModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
