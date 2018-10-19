import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    StatisticComponent,
    StatisticDetailComponent,
    StatisticUpdateComponent,
    StatisticDeletePopupComponent,
    StatisticDeleteDialogComponent,
    statisticRoute,
    statisticPopupRoute
} from './';

const ENTITY_STATES = [...statisticRoute, ...statisticPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        StatisticComponent,
        StatisticDetailComponent,
        StatisticUpdateComponent,
        StatisticDeleteDialogComponent,
        StatisticDeletePopupComponent
    ],
    entryComponents: [StatisticComponent, StatisticUpdateComponent, StatisticDeleteDialogComponent, StatisticDeletePopupComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSStatisticModule {}
