import { Component, OnInit, inject } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { DocumentationLinkComponent } from 'app/shared/components/documentation-link/documentation-link.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RouterLink } from '@angular/router';
import { SshUserSettingsFingerprintsService } from 'app/core/user/settings/ssh-settings/fingerprints/ssh-user-settings-fingerprints.service';
import { UserSettingsTitleBarTitleDirective } from 'app/core/user/settings/shared/user-settings-title-bar-title.directive';
import { UserSettingsTitleBarActionsDirective } from 'app/core/user/settings/shared/user-settings-title-bar-actions.directive';

@Component({
    selector: 'jhi-account-information',
    templateUrl: './ssh-user-settings-fingerprints.component.html',
    styleUrls: ['./ssh-user-settings-fingerprints.component.scss', '../ssh-user-settings.component.scss'],
    imports: [TranslateDirective, DocumentationLinkComponent, RouterLink, UserSettingsTitleBarTitleDirective, UserSettingsTitleBarActionsDirective],
})
export class SshUserSettingsFingerprintsComponent implements OnInit {
    readonly sshUserSettingsService = inject(SshUserSettingsFingerprintsService);

    protected sshFingerprints?: { [key: string]: string };

    readonly documentationType: DocumentationType = 'SshSetup';
    protected readonly ButtonType = ButtonType;

    protected readonly ButtonSize = ButtonSize;

    async ngOnInit() {
        this.sshFingerprints = await this.sshUserSettingsService.getSshFingerprints();
    }
}
