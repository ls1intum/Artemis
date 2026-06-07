import { Component, OnInit, inject } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { DocumentationType } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { DocumentationLinkComponent } from 'app/shared-ui/components/documentation-link/documentation-link.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { RouterLink } from '@angular/router';
import { SshUserSettingsFingerprintsService } from 'app/account/user/settings/ssh-settings/fingerprints/ssh-user-settings-fingerprints.service';

@Component({
    selector: 'jhi-account-information',
    templateUrl: './ssh-user-settings-fingerprints.component.html',
    styleUrls: ['./ssh-user-settings-fingerprints.component.scss', '../ssh-user-settings.component.scss'],
    imports: [TranslateDirective, DocumentationLinkComponent, RouterLink],
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
