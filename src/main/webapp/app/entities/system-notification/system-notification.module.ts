import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { SystemNotificationService } from './';

@NgModule({
    imports: [ArTEMiSSharedModule],
    declarations: [],
    entryComponents: [],
    exports: [],
    providers: [SystemNotificationService],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})

export class ArTEMiSSystemNotificationModule {}
