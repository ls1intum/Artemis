import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from '../../shared';
import {
    MultipleChoiceQuestionService,
    MultipleChoiceQuestionPopupService,
    MultipleChoiceQuestionComponent,
    MultipleChoiceQuestionDetailComponent,
    MultipleChoiceQuestionDialogComponent,
    MultipleChoiceQuestionPopupComponent,
    MultipleChoiceQuestionDeletePopupComponent,
    MultipleChoiceQuestionDeleteDialogComponent,
    multipleChoiceQuestionRoute,
    multipleChoiceQuestionPopupRoute,
} from './';

const ENTITY_STATES = [
    ...multipleChoiceQuestionRoute,
    ...multipleChoiceQuestionPopupRoute,
];

@NgModule({
    imports: [
        ArTeMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        MultipleChoiceQuestionComponent,
        MultipleChoiceQuestionDetailComponent,
        MultipleChoiceQuestionDialogComponent,
        MultipleChoiceQuestionDeleteDialogComponent,
        MultipleChoiceQuestionPopupComponent,
        MultipleChoiceQuestionDeletePopupComponent,
    ],
    entryComponents: [
        MultipleChoiceQuestionComponent,
        MultipleChoiceQuestionDialogComponent,
        MultipleChoiceQuestionPopupComponent,
        MultipleChoiceQuestionDeleteDialogComponent,
        MultipleChoiceQuestionDeletePopupComponent,
    ],
    providers: [
        MultipleChoiceQuestionService,
        MultipleChoiceQuestionPopupService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSMultipleChoiceQuestionModule {}
