import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../shared';

import { accountState, ActivateComponent, ActivateService, Register, RegisterComponent, SettingsComponent } from './';

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        RouterModule.forChild(accountState)
    ],
    declarations: [
        ActivateComponent,
        RegisterComponent,
        SettingsComponent
    ],
    providers: [
        Register,
        ActivateService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSAccountModule {}
