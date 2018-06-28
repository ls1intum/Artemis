import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from '../../shared';
import {
    DragAndDropQuestionService,
    DragAndDropQuestionPopupService,
    DragAndDropQuestionComponent,
    DragAndDropQuestionDetailComponent,
    DragAndDropQuestionDialogComponent,
    DragAndDropQuestionPopupComponent,
    DragAndDropQuestionDeletePopupComponent,
    DragAndDropQuestionDeleteDialogComponent,
    dragAndDropQuestionRoute,
    dragAndDropQuestionPopupRoute,
} from './';

const ENTITY_STATES = [
    ...dragAndDropQuestionRoute,
    ...dragAndDropQuestionPopupRoute,
];

@NgModule({
    imports: [
        ArTeMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        DragAndDropQuestionComponent,
        DragAndDropQuestionDetailComponent,
        DragAndDropQuestionDialogComponent,
        DragAndDropQuestionDeleteDialogComponent,
        DragAndDropQuestionPopupComponent,
        DragAndDropQuestionDeletePopupComponent,
    ],
    entryComponents: [
        DragAndDropQuestionComponent,
        DragAndDropQuestionDialogComponent,
        DragAndDropQuestionPopupComponent,
        DragAndDropQuestionDeleteDialogComponent,
        DragAndDropQuestionDeletePopupComponent,
    ],
    providers: [
        DragAndDropQuestionService,
        DragAndDropQuestionPopupService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSDragAndDropQuestionModule {}
