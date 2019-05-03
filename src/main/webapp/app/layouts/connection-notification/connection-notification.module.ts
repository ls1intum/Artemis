import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { ConnectionNotificationComponent } from './';

@NgModule({
    imports: [ArTEMiSSharedModule],
    declarations: [ConnectionNotificationComponent],
    entryComponents: [],
    exports: [ConnectionNotificationComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArTEMiSConnectionNotificationModule {}
