import { NgModule } from '@angular/core';

import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ModelingAssessmentModule } from 'app/exercises/modeling/assess/modeling-assessment.module';
import { ExampleModelingSubmissionComponent } from 'app/exercises/modeling/manage/example-modeling/example-modeling-submission.component';
import { ArtemisExampleModelingSubmissionRoutingModule } from 'app/exercises/modeling/manage/example-modeling/example-modeling-submission.route';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisResultModule,
        ArtemisModelingEditorModule,
        ArtemisExampleModelingSubmissionRoutingModule,
        ModelingAssessmentModule,
        ArtemisAssessmentSharedModule,
        AssessmentInstructionsModule,
        ArtemisSharedComponentModule,
    ],
    declarations: [ExampleModelingSubmissionComponent],
})
export class ArtemisExampleModelingSubmissionModule {}
