import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared';
import { ConnectionNotificationComponent } from './';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ConnectionNotificationComponent],
    entryComponents: [],
    exports: [ConnectionNotificationComponent],
})
export class ArtemisConnectionNotificationModule {}
