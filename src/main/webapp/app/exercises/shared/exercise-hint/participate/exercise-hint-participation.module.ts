import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ExerciseHintStudentComponent, ExerciseHintStudentDialogComponent } from 'app/exercises/shared/exercise-hint/participate/exercise-hint-student-dialog.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisExerciseHintManagementModule } from 'app/exercises/shared/exercise-hint/manage/exercise-hint-management.module';
import { ArtemisExerciseHintSharedModule } from 'app/exercises/shared/exercise-hint/shared/exercise-hint-shared.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisMarkdownModule, ArtemisExerciseHintManagementModule, ArtemisExerciseHintSharedModule],
    declarations: [ExerciseHintStudentDialogComponent, ExerciseHintStudentComponent],
    entryComponents: [ExerciseHintStudentDialogComponent],
    exports: [ExerciseHintStudentDialogComponent, ExerciseHintStudentComponent],
})
export class ArtemisExerciseHintParticipationModule {}
