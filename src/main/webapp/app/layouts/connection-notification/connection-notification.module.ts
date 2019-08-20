import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared';
import { ConnectionNotificationComponent } from './';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ConnectionNotificationComponent],
    entryComponents: [],
    exports: [ConnectionNotificationComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArtemisConnectionNotificationModule {}
