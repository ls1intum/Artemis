import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ConnectionNotificationComponent } from 'app/shared/layouts/connection-notification/connection-notification.component';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ConnectionNotificationComponent],
    exports: [ConnectionNotificationComponent],
})
export class ArtemisConnectionNotificationModule {}
