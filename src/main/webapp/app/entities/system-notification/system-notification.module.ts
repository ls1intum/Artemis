import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared';
import { SystemNotificationService } from './';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [],
    entryComponents: [],
    exports: [],
    providers: [SystemNotificationService],
})
export class ArtemisSystemNotificationModule {}
