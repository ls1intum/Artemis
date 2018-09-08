import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    QuizSubmissionComponent,
    QuizSubmissionDetailComponent,
    QuizSubmissionUpdateComponent,
    QuizSubmissionDeletePopupComponent,
    QuizSubmissionDeleteDialogComponent,
    quizSubmissionRoute,
    quizSubmissionPopupRoute
} from './';

const ENTITY_STATES = [...quizSubmissionRoute, ...quizSubmissionPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        QuizSubmissionComponent,
        QuizSubmissionDetailComponent,
        QuizSubmissionUpdateComponent,
        QuizSubmissionDeleteDialogComponent,
        QuizSubmissionDeletePopupComponent
    ],
    entryComponents: [
        QuizSubmissionComponent,
        QuizSubmissionUpdateComponent,
        QuizSubmissionDeleteDialogComponent,
        QuizSubmissionDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSQuizSubmissionModule {}
