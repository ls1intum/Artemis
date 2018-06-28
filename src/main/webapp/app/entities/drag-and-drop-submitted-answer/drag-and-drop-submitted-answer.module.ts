import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from '../../shared';
import {
    DragAndDropSubmittedAnswerService,
    DragAndDropSubmittedAnswerPopupService,
    DragAndDropSubmittedAnswerComponent,
    DragAndDropSubmittedAnswerDetailComponent,
    DragAndDropSubmittedAnswerDialogComponent,
    DragAndDropSubmittedAnswerPopupComponent,
    DragAndDropSubmittedAnswerDeletePopupComponent,
    DragAndDropSubmittedAnswerDeleteDialogComponent,
    dragAndDropSubmittedAnswerRoute,
    dragAndDropSubmittedAnswerPopupRoute,
} from './';

const ENTITY_STATES = [
    ...dragAndDropSubmittedAnswerRoute,
    ...dragAndDropSubmittedAnswerPopupRoute,
];

@NgModule({
    imports: [
        ArTeMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        DragAndDropSubmittedAnswerComponent,
        DragAndDropSubmittedAnswerDetailComponent,
        DragAndDropSubmittedAnswerDialogComponent,
        DragAndDropSubmittedAnswerDeleteDialogComponent,
        DragAndDropSubmittedAnswerPopupComponent,
        DragAndDropSubmittedAnswerDeletePopupComponent,
    ],
    entryComponents: [
        DragAndDropSubmittedAnswerComponent,
        DragAndDropSubmittedAnswerDialogComponent,
        DragAndDropSubmittedAnswerPopupComponent,
        DragAndDropSubmittedAnswerDeleteDialogComponent,
        DragAndDropSubmittedAnswerDeletePopupComponent,
    ],
    providers: [
        DragAndDropSubmittedAnswerService,
        DragAndDropSubmittedAnswerPopupService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSDragAndDropSubmittedAnswerModule {}
