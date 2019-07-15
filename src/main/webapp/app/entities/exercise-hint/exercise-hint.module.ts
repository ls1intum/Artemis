import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from 'app/shared';
import {
    ExerciseHintComponent,
    ExerciseHintDetailComponent,
    ExerciseHintUpdateComponent,
    ExerciseHintDeletePopupComponent,
    ExerciseHintDeleteDialogComponent,
    exerciseHintRoute,
    exerciseHintPopupRoute,
} from './';

const ENTITY_STATES = [...exerciseHintRoute, ...exerciseHintPopupRoute];

@NgModule({
    imports: [ArTEMiSSharedModule, RouterModule.forChild(ENTITY_STATES), FormsModule, ReactiveFormsModule],
    declarations: [ExerciseHintComponent, ExerciseHintDetailComponent, ExerciseHintUpdateComponent, ExerciseHintDeleteDialogComponent, ExerciseHintDeletePopupComponent],
    entryComponents: [ExerciseHintComponent, ExerciseHintUpdateComponent, ExerciseHintDeleteDialogComponent, ExerciseHintDeletePopupComponent],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
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
