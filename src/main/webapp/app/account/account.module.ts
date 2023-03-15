import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { accountState } from './account.route';
import { ActivateComponent } from './activate/activate.component';
import { PasswordStrengthBarComponent } from './password/password-strength-bar.component';
import { PasswordComponent } from './password/password.component';
import { PasswordResetFinishComponent } from './password-reset/finish/password-reset-finish.component';
import { PasswordResetInitComponent } from './password-reset/init/password-reset-init.component';
import { RegisterComponent } from './register/register.component';
import { SettingsComponent } from './settings/settings.component';
import { ExternalUserPasswordResetModalComponent } from 'app/account/password-reset/external/external-user-password-reset-modal.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(accountState)],
    declarations: [
        ActivateComponent,
        RegisterComponent,
        PasswordComponent,
        PasswordStrengthBarComponent,
        PasswordResetInitComponent,
        PasswordResetFinishComponent,
        ExternalUserPasswordResetModalComponent,
        SettingsComponent,
    ],
})
export class ArtemisAccountModule {}
