import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TextHintStudentComponent, TextHintStudentDialogComponent } from 'app/exercises/shared/exercise-hint/participate/exercise-hint-student-dialog.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisMarkdownModule],
    declarations: [TextHintStudentDialogComponent, TextHintStudentComponent],
    entryComponents: [TextHintStudentDialogComponent],
    exports: [TextHintStudentDialogComponent, TextHintStudentComponent],
})
export class ArtemisTextHintParticipationModule {}
