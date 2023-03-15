import { NgModule } from '@angular/core';

import { AdminSystemNotificationService } from 'app/shared/notification/system-notification/admin-system-notification.service';
import { SystemNotificationService } from 'app/shared/notification/system-notification/system-notification.service';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule],
    providers: [SystemNotificationService, AdminSystemNotificationService],
})
export class ArtemisSystemNotificationModule {}
