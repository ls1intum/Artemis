import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { fileUploadAssessmentRoutes } from './file-upload-assessment.route';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FileUploadAssessmentComponent } from './file-upload-assessment.component';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { FileUploadAssessmentDashboardComponent } from 'app/exercises/file-upload/assess/file-upload-assessment-dashboard/file-upload-assessment-dashboard.component';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisTextAssessmentModule } from 'app/exercises/text/assess/text-assessment/text-assessment.module';
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import { ModelingAssessmentModule } from 'app/exercises/modeling/assess/modeling-assessment/modeling-assessment.module';

const ENTITY_STATES = [...fileUploadAssessmentRoutes];
@NgModule({
    imports: [
        SortByModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisSharedModule,
        ArtemisResultModule,
        ArtemisTextAssessmentModule,
        ArtemisAssessmentSharedModule,
        ModelingAssessmentModule,
    ],
    declarations: [FileUploadAssessmentComponent, FileUploadAssessmentDashboardComponent],
    exports: [FileUploadAssessmentComponent],
})
export class ArtemisFileUploadAssessmentModule {}
