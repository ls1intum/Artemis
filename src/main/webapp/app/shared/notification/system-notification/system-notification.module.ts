import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { SystemNotificationService } from 'app/shared/notification/system-notification/system-notification.service';
import { MetisService } from 'app/shared/metis/metis.service';

@NgModule({
    imports: [ArtemisSharedModule],
    providers: [SystemNotificationService, MetisService],
})
export class ArtemisSystemNotificationModule {}
