import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { NotificationComponent } from 'app/overview/notification/notification.component';
import { notificationRoute } from 'app/overview/notification/notification.route';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(notificationRoute)],
    declarations: [NotificationComponent],
})
export class ArtemisNotificationModule {}
