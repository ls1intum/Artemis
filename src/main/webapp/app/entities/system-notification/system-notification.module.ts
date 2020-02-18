import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { SystemNotificationService } from 'app/entities/system-notification/system-notification.service';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [],
    entryComponents: [],
    exports: [],
    providers: [SystemNotificationService],
})
export class ArtemisSystemNotificationModule {}
