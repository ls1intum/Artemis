import { Component, OnInit, inject, signal } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { DocumentationType } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { DocumentationLinkComponent } from 'app/shared-ui/components/documentation-link/documentation-link.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { RouterLink } from '@angular/router';
import { SshUserSettingsFingerprintsService } from 'app/account/user/settings/ssh-settings/fingerprints/ssh-user-settings-fingerprints.service';
import { TumUiButtonDirective, TumUiListComponent, TumUiListItemDirective } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-account-information',
    templateUrl: './ssh-user-settings-fingerprints.component.html',
    styleUrls: ['../ssh-user-settings.component.scss'],
    imports: [TranslateDirective, DocumentationLinkComponent, RouterLink, TumUiButtonDirective, TumUiListComponent, TumUiListItemDirective, ArtemisTranslatePipe],
})
export class SshUserSettingsFingerprintsComponent implements OnInit {
    readonly sshUserSettingsService = inject(SshUserSettingsFingerprintsService);

    protected readonly sshFingerprints = signal<{ [key: string]: string } | undefined>(undefined);

    readonly documentationType: DocumentationType = 'SshSetup';
    protected readonly ButtonType = ButtonType;

    protected readonly ButtonSize = ButtonSize;

    async ngOnInit() {
        this.sshFingerprints.set(await this.sshUserSettingsService.getSshFingerprints());
    }
}
