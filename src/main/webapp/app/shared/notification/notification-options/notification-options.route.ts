import { Routes } from '@angular/router';
import { NotificationOptionsComponent } from 'app/shared/notification/notification-options/notification-options.component';

export const notificationOptionsState: Routes = [
    {
        path: 'notification-settings',
        component: NotificationOptionsComponent,
    },
];
