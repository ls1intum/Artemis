import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    DragItemComponent,
    DragItemDetailComponent,
    DragItemUpdateComponent,
    DragItemDeletePopupComponent,
    DragItemDeleteDialogComponent,
    dragItemRoute,
    dragItemPopupRoute
} from './';

const ENTITY_STATES = [...dragItemRoute, ...dragItemPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        DragItemComponent,
        DragItemDetailComponent,
        DragItemUpdateComponent,
        DragItemDeleteDialogComponent,
        DragItemDeletePopupComponent
    ],
    entryComponents: [DragItemComponent, DragItemUpdateComponent, DragItemDeleteDialogComponent, DragItemDeletePopupComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSDragItemModule {}
