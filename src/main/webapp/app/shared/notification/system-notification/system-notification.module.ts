import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { SystemNotificationService } from 'app/shared/notification/system-notification/system-notification.service';
import { AdminSystemNotificationService } from 'app/shared/notification/system-notification/admin-system-notification.service';

@NgModule({
    imports: [ArtemisSharedModule],
    providers: [SystemNotificationService, AdminSystemNotificationService],
})
export class ArtemisSystemNotificationModule {}
