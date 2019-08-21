import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArtemisSharedModule } from 'app/shared';
import {
    ExerciseHintComponent,
    ExerciseHintDetailComponent,
    ExerciseHintUpdateComponent,
    ExerciseHintDeletePopupComponent,
    ExerciseHintDeleteDialogComponent,
    exerciseHintRoute,
    exerciseHintPopupRoute,
    ExerciseHintStudentDialogComponent,
    ExerciseHintStudentComponent,
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
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],

    exports: [ExerciseHintStudentDialogComponent, ExerciseHintStudentComponent],
})
export class ArtemisExerciseHintModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
