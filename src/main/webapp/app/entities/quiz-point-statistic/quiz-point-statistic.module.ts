import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    QuizPointStatisticComponent,
    QuizPointStatisticDetailComponent,
    QuizPointStatisticUpdateComponent,
    QuizPointStatisticDeletePopupComponent,
    QuizPointStatisticDeleteDialogComponent,
    quizPointStatisticRoute,
    quizPointStatisticPopupRoute
} from './';

const ENTITY_STATES = [...quizPointStatisticRoute, ...quizPointStatisticPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        QuizPointStatisticComponent,
        QuizPointStatisticDetailComponent,
        QuizPointStatisticUpdateComponent,
        QuizPointStatisticDeleteDialogComponent,
        QuizPointStatisticDeletePopupComponent
    ],
    entryComponents: [
        QuizPointStatisticComponent,
        QuizPointStatisticUpdateComponent,
        QuizPointStatisticDeleteDialogComponent,
        QuizPointStatisticDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSQuizPointStatisticModule {}
