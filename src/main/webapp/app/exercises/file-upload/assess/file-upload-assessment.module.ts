import { NgModule } from '@angular/core';

import { FileUploadAssessmentComponent } from './file-upload-assessment.component';
import { ArtemisFileUploadAssessmentRoutingModule } from './file-upload-assessment.route';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { FileUploadAssessmentDashboardComponent } from 'app/exercises/file-upload/assess/file-upload-assessment-dashboard.component';
import { ModelingAssessmentModule } from 'app/exercises/modeling/assess/modeling-assessment.module';
import { ArtemisAssessmentProgressLabelModule } from 'app/exercises/shared/assessment-progress-label/assessment-progress-label.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisResultModule,
        ArtemisAssessmentSharedModule,
        ArtemisFileUploadAssessmentRoutingModule,
        ModelingAssessmentModule,
        AssessmentInstructionsModule,
        ArtemisMarkdownModule,
        SubmissionResultStatusModule,
        ArtemisAssessmentProgressLabelModule,
    ],
    declarations: [FileUploadAssessmentComponent, FileUploadAssessmentDashboardComponent],
})
export class ArtemisFileUploadAssessmentModule {}
