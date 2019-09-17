import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared';
import {
    ExerciseHintComponent,
    ExerciseHintDeleteDialogComponent,
    ExerciseHintDeletePopupComponent,
    ExerciseHintDetailComponent,
    exerciseHintPopupRoute,
    exerciseHintRoute,
    ExerciseHintStudentComponent,
    ExerciseHintStudentDialogComponent,
    ExerciseHintUpdateComponent,
} from './';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor';

const ENTITY_STATES = [...exerciseHintRoute, ...exerciseHintPopupRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), FormsModule, ReactiveFormsModule, ArtemisMarkdownEditorModule],
    declarations: [
        ExerciseHintComponent,
        ExerciseHintDetailComponent,
        ExerciseHintUpdateComponent,
        ExerciseHintDeleteDialogComponent,
        ExerciseHintDeletePopupComponent,
        ExerciseHintStudentDialogComponent,
        ExerciseHintStudentComponent,
    ],
    entryComponents: [ExerciseHintComponent, ExerciseHintUpdateComponent, ExerciseHintDeleteDialogComponent, ExerciseHintDeletePopupComponent, ExerciseHintStudentDialogComponent],
    exports: [ExerciseHintStudentDialogComponent, ExerciseHintStudentComponent],
})
export class ArtemisExerciseHintModule {}
