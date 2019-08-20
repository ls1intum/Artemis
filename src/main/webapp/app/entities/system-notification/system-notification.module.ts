import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared';
import { SystemNotificationService } from './';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [],
    entryComponents: [],
    exports: [],
    providers: [SystemNotificationService],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArtemisSystemNotificationModule {}
