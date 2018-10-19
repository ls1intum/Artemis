import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    StatisticCounterComponent,
    StatisticCounterDetailComponent,
    StatisticCounterUpdateComponent,
    StatisticCounterDeletePopupComponent,
    StatisticCounterDeleteDialogComponent,
    statisticCounterRoute,
    statisticCounterPopupRoute
} from './';

const ENTITY_STATES = [...statisticCounterRoute, ...statisticCounterPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        StatisticCounterComponent,
        StatisticCounterDetailComponent,
        StatisticCounterUpdateComponent,
        StatisticCounterDeleteDialogComponent,
        StatisticCounterDeletePopupComponent
    ],
    entryComponents: [
        StatisticCounterComponent,
        StatisticCounterUpdateComponent,
        StatisticCounterDeleteDialogComponent,
        StatisticCounterDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSStatisticCounterModule {}
