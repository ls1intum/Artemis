import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { fileUploadAssessmentRoutes } from './file-upload-assessment.route';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisResultModule } from 'app/entities/result';
import { SortByModule } from 'app/components/pipes';
import { FileUploadAssessmentComponent } from './file-upload-assessment.component';
import { ArtemisTextAssessmentModule } from 'app/text-assessment';
import { FileUploadAssessmentDetailComponent } from 'app/file-upload-assessment/file-upload-assessment-detail/file-upload-assessment-detail.component';
import { ArtemisAssessmentSharedModule } from 'app/assessment-shared';

const ENTITY_STATES = [...fileUploadAssessmentRoutes];
@NgModule({
    imports: [SortByModule, RouterModule.forChild(ENTITY_STATES), ArtemisSharedModule, ArtemisResultModule, ArtemisTextAssessmentModule, ArtemisAssessmentSharedModule],
    declarations: [FileUploadAssessmentComponent, FileUploadAssessmentDetailComponent],
    exports: [FileUploadAssessmentComponent],
    providers: [],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArtemisFileUploadAssessmentModule {}
