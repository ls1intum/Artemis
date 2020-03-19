import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { SystemNotificationService } from 'app/core/system-notification/system-notification.service';

@NgModule({
    imports: [ArtemisSharedModule],
    providers: [SystemNotificationService],
})
export class ArtemisSystemNotificationModule {}
