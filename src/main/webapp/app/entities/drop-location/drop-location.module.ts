import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from '../../shared';
import {
    DropLocationService,
    DropLocationPopupService,
    DropLocationComponent,
    DropLocationDetailComponent,
    DropLocationDialogComponent,
    DropLocationPopupComponent,
    DropLocationDeletePopupComponent,
    DropLocationDeleteDialogComponent,
    dropLocationRoute,
    dropLocationPopupRoute,
} from './';

const ENTITY_STATES = [
    ...dropLocationRoute,
    ...dropLocationPopupRoute,
];

@NgModule({
    imports: [
        ArTeMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        DropLocationComponent,
        DropLocationDetailComponent,
        DropLocationDialogComponent,
        DropLocationDeleteDialogComponent,
        DropLocationPopupComponent,
        DropLocationDeletePopupComponent,
    ],
    entryComponents: [
        DropLocationComponent,
        DropLocationDialogComponent,
        DropLocationPopupComponent,
        DropLocationDeleteDialogComponent,
        DropLocationDeletePopupComponent,
    ],
    providers: [
        DropLocationService,
        DropLocationPopupService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSDropLocationModule {}
