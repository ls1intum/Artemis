import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ExamExerciseUpdateHighlighterComponent } from 'app/exam/participate/exercises/exam-exercise-update-highlighter/exam-exercise-update-highlighter.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    declarations: [ExamExerciseUpdateHighlighterComponent],
    imports: [CommonModule, ArtemisSharedModule],
    exports: [ExamExerciseUpdateHighlighterComponent],
})
export class ArtemisExamExerciseUpdateHighlighterModule {}
