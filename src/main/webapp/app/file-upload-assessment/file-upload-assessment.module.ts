import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { fileUploadAssessmentRoutes } from './file-upload-assessment.route';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FileUploadAssessmentComponent } from './file-upload-assessment.component';
import { ArtemisAssessmentSharedModule } from 'app/assessment-shared/assessment-shared.module';
import { ModelingAssessmentModule } from 'app/modeling-assessment/modeling-assessment.module';
import { FileUploadAssessmentDashboardComponent } from 'app/file-upload-assessment/file-upload-assessment-dashboard/file-upload-assessment-dashboard.component';
import { ArtemisResultModule } from 'app/entities/result/result.module';
import { ArtemisTextAssessmentModule } from 'app/text-assessment/text-assessment.module';
import { SortByModule } from 'app/components/pipes/sort-by.module';

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
