import { Routes } from '@angular/router';
import { UserSettingsComponent } from 'app/shared/user-settings/user-settings.component';

export const userSettingsState: Routes = [
    {
        path: 'user-settings',
        component: UserSettingsComponent,
    },
];
