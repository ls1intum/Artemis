import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from '../../shared';
import {
    QuizSubmissionService,
    QuizSubmissionPopupService,
    QuizSubmissionComponent,
    QuizSubmissionDetailComponent,
    QuizSubmissionDialogComponent,
    QuizSubmissionPopupComponent,
    QuizSubmissionDeletePopupComponent,
    QuizSubmissionDeleteDialogComponent,
    quizSubmissionRoute,
    quizSubmissionPopupRoute,
} from './';

const ENTITY_STATES = [
    ...quizSubmissionRoute,
    ...quizSubmissionPopupRoute,
];

@NgModule({
    imports: [
        ArTeMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        QuizSubmissionComponent,
        QuizSubmissionDetailComponent,
        QuizSubmissionDialogComponent,
        QuizSubmissionDeleteDialogComponent,
        QuizSubmissionPopupComponent,
        QuizSubmissionDeletePopupComponent,
    ],
    entryComponents: [
        QuizSubmissionComponent,
        QuizSubmissionDialogComponent,
        QuizSubmissionPopupComponent,
        QuizSubmissionDeleteDialogComponent,
        QuizSubmissionDeletePopupComponent,
    ],
    providers: [
        QuizSubmissionService,
        QuizSubmissionPopupService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSQuizSubmissionModule {}
