import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from '../shared';
import { HOME_ROUTE } from './home.route';
import { HomeComponent } from './';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild([HOME_ROUTE])],
    declarations: [HomeComponent],
})
export class ArtemisHomeModule {}
