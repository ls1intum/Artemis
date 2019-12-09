import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { fileUploadAssessmentRoutes } from './file-upload-assessment.route';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisResultModule } from 'app/entities/result';
import { SortByModule } from 'app/components/pipes';
import { FileUploadAssessmentComponent } from './file-upload-assessment.component';
import { ArtemisTextAssessmentModule } from 'app/text-assessment';
import { ArtemisAssessmentSharedModule } from 'app/assessment-shared/assessment-shared.module';
import { ModelingAssessmentModule } from 'app/modeling-assessment/modeling-assessment.module';
import { FileUploadAssessmentDashboardComponent } from 'app/file-upload-assessment/file-upload-assessment-dashboard/file-upload-assessment-dashboard.component';

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
