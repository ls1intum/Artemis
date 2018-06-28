import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from '../../shared';
import {
    MultipleChoiceSubmittedAnswerService,
    MultipleChoiceSubmittedAnswerPopupService,
    MultipleChoiceSubmittedAnswerComponent,
    MultipleChoiceSubmittedAnswerDetailComponent,
    MultipleChoiceSubmittedAnswerDialogComponent,
    MultipleChoiceSubmittedAnswerPopupComponent,
    MultipleChoiceSubmittedAnswerDeletePopupComponent,
    MultipleChoiceSubmittedAnswerDeleteDialogComponent,
    multipleChoiceSubmittedAnswerRoute,
    multipleChoiceSubmittedAnswerPopupRoute,
} from './';

const ENTITY_STATES = [
    ...multipleChoiceSubmittedAnswerRoute,
    ...multipleChoiceSubmittedAnswerPopupRoute,
];

@NgModule({
    imports: [
        ArTeMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        MultipleChoiceSubmittedAnswerComponent,
        MultipleChoiceSubmittedAnswerDetailComponent,
        MultipleChoiceSubmittedAnswerDialogComponent,
        MultipleChoiceSubmittedAnswerDeleteDialogComponent,
        MultipleChoiceSubmittedAnswerPopupComponent,
        MultipleChoiceSubmittedAnswerDeletePopupComponent,
    ],
    entryComponents: [
        MultipleChoiceSubmittedAnswerComponent,
        MultipleChoiceSubmittedAnswerDialogComponent,
        MultipleChoiceSubmittedAnswerPopupComponent,
        MultipleChoiceSubmittedAnswerDeleteDialogComponent,
        MultipleChoiceSubmittedAnswerDeletePopupComponent,
    ],
    providers: [
        MultipleChoiceSubmittedAnswerService,
        MultipleChoiceSubmittedAnswerPopupService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSMultipleChoiceSubmittedAnswerModule {}
