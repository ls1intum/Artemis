import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from '../shared';

import { HOME_ROUTES, HomeComponent } from './';

const ENTITY_STATES = [...HOME_ROUTES];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [HomeComponent],
    entryComponents: [],
    providers: [],
})
export class ArtemisHomeModule {}
