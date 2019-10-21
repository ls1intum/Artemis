import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared';
import {
    ExerciseHintComponent,
    ExerciseHintDetailComponent,
    exerciseHintRoute,
    ExerciseHintStudentComponent,
    ExerciseHintStudentDialogComponent,
    ExerciseHintUpdateComponent,
} from './';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor';

const ENTITY_STATES = [...exerciseHintRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), FormsModule, ReactiveFormsModule, ArtemisMarkdownEditorModule],
    declarations: [ExerciseHintComponent, ExerciseHintDetailComponent, ExerciseHintUpdateComponent, ExerciseHintStudentDialogComponent, ExerciseHintStudentComponent],
    entryComponents: [ExerciseHintComponent, ExerciseHintUpdateComponent, ExerciseHintStudentDialogComponent],
    exports: [ExerciseHintStudentDialogComponent, ExerciseHintStudentComponent],
})
export class ArtemisExerciseHintModule {}
