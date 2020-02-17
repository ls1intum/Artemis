import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { exerciseHintRoute } from 'app/entities/exercise-hint/exercise-hint.route';
import { ExerciseHintDetailComponent } from 'app/entities/exercise-hint/exercise-hint-detail.component';
import { ExerciseHintUpdateComponent } from 'app/entities/exercise-hint/exercise-hint-update.component';
import { ExerciseHintStudentComponent, ExerciseHintStudentDialogComponent } from 'app/entities/exercise-hint/exercise-hint-student-dialog.component';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor/markdown-editor.module';
import { ExerciseHintComponent } from 'app/entities/exercise-hint/exercise-hint.component';

const ENTITY_STATES = [...exerciseHintRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), FormsModule, ReactiveFormsModule, ArtemisMarkdownEditorModule],
    declarations: [ExerciseHintComponent, ExerciseHintDetailComponent, ExerciseHintUpdateComponent, ExerciseHintStudentDialogComponent, ExerciseHintStudentComponent],
    entryComponents: [ExerciseHintComponent, ExerciseHintUpdateComponent, ExerciseHintStudentDialogComponent],
    exports: [ExerciseHintStudentDialogComponent, ExerciseHintStudentComponent],
})
export class ArtemisExerciseHintModule {}
