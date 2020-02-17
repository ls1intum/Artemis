import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { NotificationComponent } from 'app/entities/notification/notification.component';
import { notificationRoute } from 'app/entities/notification/notification.route';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(notificationRoute)],
    declarations: [NotificationComponent],
    entryComponents: [NotificationComponent],
    exports: [],
})
export class ArtemisNotificationModule {}
