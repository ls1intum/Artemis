import { NgModule } from '@angular/core';
import { MomentModule } from 'ngx-moment';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ModelingSubmissionExamComponent } from 'app/exam/participate/exercises/modeling/modeling-submission-exam.component';
import { TextEditorExamComponent } from 'app/exam/participate/exercises/text/text-editor-exam.component';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { ArtemisFullscreenModule } from 'app/shared/fullscreen/fullscreen.module';

@NgModule({
    imports: [MomentModule, ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisModelingEditorModule, ArtemisFullscreenModule],
    declarations: [TextEditorExamComponent, ModelingSubmissionExamComponent],
    exports: [
        TextEditorExamComponent,
        ModelingSubmissionExamComponent
    ]
})
export class ArtemisExamExercisesParticipationModule {}
