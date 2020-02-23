import { NgModule } from '@angular/core';
import { ArtemisFileUploadAssessmentRoutingModule } from './file-upload-assessment.route';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FileUploadAssessmentComponent } from './file-upload-assessment.component';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { FileUploadAssessmentDashboardComponent } from 'app/exercises/file-upload/assess/file-upload-assessment-dashboard.component';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisTextExerciseAssessmnetModule } from 'app/exercises/text/assess/text-assessment.module';
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import { ModelingAssessmentModule } from 'app/exercises/modeling/assess/modeling-assessment.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisResultModule,
        ArtemisTextExerciseAssessmnetModule,
        ArtemisAssessmentSharedModule,
        ArtemisFileUploadAssessmentRoutingModule,
        ModelingAssessmentModule,
        SortByModule,
    ],
    declarations: [FileUploadAssessmentComponent, FileUploadAssessmentDashboardComponent],
})
export class ArtemisFileUploadAssessmentModule {}
