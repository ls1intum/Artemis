import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { NotificationService, } from './';

@NgModule({
    imports: [ArTEMiSSharedModule],
    declarations: [],
    entryComponents: [],
    exports: [],
    providers: [NotificationService],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})

export class ArTEMiSNotificationModule {}
