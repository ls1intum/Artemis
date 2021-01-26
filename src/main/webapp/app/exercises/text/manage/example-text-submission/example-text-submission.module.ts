import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { exampleTextSubmissionRoute } from 'app/exercises/text/manage/example-text-submission/example-text-submission.route';
import { ExampleTextSubmissionComponent } from 'app/exercises/text/manage/example-text-submission/example-text-submission.component';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisTextSubmissionAssessmentModule } from 'app/exercises/text/assess/text-submission-assessment.module';
import { ResizableInstructionsComponent } from 'app/exercises/text/manage/example-text-submission/resizable-instructions/resizable-instructions.component';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';

const ENTITY_STATES = [...exampleTextSubmissionRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisResultModule,
        MomentModule,
        ClipboardModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisTextSubmissionAssessmentModule,
        ArtemisAssessmentSharedModule,
        AssessmentInstructionsModule,
    ],
    declarations: [ExampleTextSubmissionComponent, ResizableInstructionsComponent],
})
export class ArtemisExampleTextSubmissionModule {}
