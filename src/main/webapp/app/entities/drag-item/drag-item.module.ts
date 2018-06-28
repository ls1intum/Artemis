import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from '../../shared';
import {
    DragItemService,
    DragItemPopupService,
    DragItemComponent,
    DragItemDetailComponent,
    DragItemDialogComponent,
    DragItemPopupComponent,
    DragItemDeletePopupComponent,
    DragItemDeleteDialogComponent,
    dragItemRoute,
    dragItemPopupRoute,
} from './';

const ENTITY_STATES = [
    ...dragItemRoute,
    ...dragItemPopupRoute,
];

@NgModule({
    imports: [
        ArTeMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        DragItemComponent,
        DragItemDetailComponent,
        DragItemDialogComponent,
        DragItemDeleteDialogComponent,
        DragItemPopupComponent,
        DragItemDeletePopupComponent,
    ],
    entryComponents: [
        DragItemComponent,
        DragItemDialogComponent,
        DragItemPopupComponent,
        DragItemDeleteDialogComponent,
        DragItemDeletePopupComponent,
    ],
    providers: [
        DragItemService,
        DragItemPopupService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSDragItemModule {}
