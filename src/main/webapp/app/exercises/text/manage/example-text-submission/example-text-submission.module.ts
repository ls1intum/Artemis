import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisTextSubmissionAssessmentModule } from 'app/exercises/text/assess/text-submission-assessment.module';
import { ExampleTextSubmissionComponent } from 'app/exercises/text/manage/example-text-submission/example-text-submission.component';
import { exampleTextSubmissionRoute } from 'app/exercises/text/manage/example-text-submission/example-text-submission.route';
import { ResizableInstructionsComponent } from 'app/exercises/text/manage/example-text-submission/resizable-instructions/resizable-instructions.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

const ENTITY_STATES = [...exampleTextSubmissionRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisResultModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisTextSubmissionAssessmentModule,
        ArtemisAssessmentSharedModule,
        AssessmentInstructionsModule,
        ArtemisMarkdownModule,
        ArtemisSharedComponentModule,
    ],
    declarations: [ExampleTextSubmissionComponent, ResizableInstructionsComponent],
})
export class ArtemisExampleTextSubmissionModule {}
