import { NgModule } from '@angular/core';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ExamExerciseUpdateHighlighterComponent } from 'app/exam/participate/exercises/exam-exercise-update-highlighter/exam-exercise-update-highlighter.component';

@NgModule({
    declarations: [ExamExerciseUpdateHighlighterComponent],
    imports: [ArtemisSharedCommonModule],
    exports: [ExamExerciseUpdateHighlighterComponent],
})
export class ExamExerciseUpdateHighlighterModule {}
