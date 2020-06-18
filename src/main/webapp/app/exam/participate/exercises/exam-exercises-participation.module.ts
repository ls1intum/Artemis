import { NgModule } from '@angular/core';
import { MomentModule } from 'ngx-moment';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ModelingSubmissionExamComponent } from 'app/exam/participate/exercises/modeling/modeling-submission-exam.component';
import { TextEditorExamComponent } from 'app/exam/participate/exercises/text/text-editor-exam.component';


@NgModule({
    imports: [
        MomentModule,
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
    ],
    declarations: [TextEditorExamComponent, ModelingSubmissionExamComponent],
})
export class ArtemisExamExercisesParticipationModule {}
