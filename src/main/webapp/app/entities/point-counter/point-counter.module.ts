import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    PointCounterComponent,
    PointCounterDetailComponent,
    PointCounterUpdateComponent,
    PointCounterDeletePopupComponent,
    PointCounterDeleteDialogComponent,
    pointCounterRoute,
    pointCounterPopupRoute
} from './';

const ENTITY_STATES = [...pointCounterRoute, ...pointCounterPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        PointCounterComponent,
        PointCounterDetailComponent,
        PointCounterUpdateComponent,
        PointCounterDeleteDialogComponent,
        PointCounterDeletePopupComponent
    ],
    entryComponents: [
        PointCounterComponent,
        PointCounterUpdateComponent,
        PointCounterDeleteDialogComponent,
        PointCounterDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSPointCounterModule {}
