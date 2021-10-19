import { Component } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { AccountInformationComponent } from 'app/shared/user-settings/account-information/account-information.component';

/**
 * UserSettingsContainerComponent serves as the common ground for different settings
 */
@Component({
    selector: 'jhi-user-settings',
    templateUrl: 'user-settings-container.component.html',
    styleUrls: ['user-settings-container.component.scss'],
})
export class UserSettingsContainerComponent extends AccountInformationComponent {
    constructor(accountService: AccountService) {
        super(accountService);
    }
}
