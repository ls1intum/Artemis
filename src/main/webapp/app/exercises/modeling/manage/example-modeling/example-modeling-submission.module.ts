import { NgModule } from '@angular/core';
import { ArtemisExampleModelingSubmissionRoutingModule } from 'app/exercises/modeling/manage/example-modeling/example-modeling-submission.route';
import { ExampleModelingSubmissionComponent } from 'app/exercises/modeling/manage/example-modeling/example-modeling-submission.component';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ModelingAssessmentModule } from 'app/exercises/modeling/assess/modeling-assessment.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

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
