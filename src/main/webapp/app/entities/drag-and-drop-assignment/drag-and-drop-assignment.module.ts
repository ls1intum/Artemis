import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from '../../shared';
import {
    DragAndDropAssignmentService,
    DragAndDropAssignmentPopupService,
    DragAndDropAssignmentComponent,
    DragAndDropAssignmentDetailComponent,
    DragAndDropAssignmentDialogComponent,
    DragAndDropAssignmentPopupComponent,
    DragAndDropAssignmentDeletePopupComponent,
    DragAndDropAssignmentDeleteDialogComponent,
    dragAndDropAssignmentRoute,
    dragAndDropAssignmentPopupRoute,
} from './';

const ENTITY_STATES = [
    ...dragAndDropAssignmentRoute,
    ...dragAndDropAssignmentPopupRoute,
];

@NgModule({
    imports: [
        ArTeMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        DragAndDropAssignmentComponent,
        DragAndDropAssignmentDetailComponent,
        DragAndDropAssignmentDialogComponent,
        DragAndDropAssignmentDeleteDialogComponent,
        DragAndDropAssignmentPopupComponent,
        DragAndDropAssignmentDeletePopupComponent,
    ],
    entryComponents: [
        DragAndDropAssignmentComponent,
        DragAndDropAssignmentDialogComponent,
        DragAndDropAssignmentPopupComponent,
        DragAndDropAssignmentDeleteDialogComponent,
        DragAndDropAssignmentDeletePopupComponent,
    ],
    providers: [
        DragAndDropAssignmentService,
        DragAndDropAssignmentPopupService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSDragAndDropAssignmentModule {}
