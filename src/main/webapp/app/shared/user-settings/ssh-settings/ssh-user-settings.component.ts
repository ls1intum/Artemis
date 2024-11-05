import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Subject, tap } from 'rxjs';
import { faEdit, faEllipsis, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { AlertService } from 'app/core/util/alert.service';
import { UserSshPublicKey } from 'app/entities/programming/user-ssh-public-key.model';
import dayjs from 'dayjs/esm';
import { SshUserSettingsService } from 'app/shared/user-settings/ssh-settings/ssh-user-settings.service';

@Component({
    selector: 'jhi-account-information',
    templateUrl: './ssh-user-settings.component.html',
    styleUrls: ['../user-settings.scss', './ssh-user-settings.component.scss'],
})
export class SshUserSettingsComponent implements OnInit, OnDestroy {
    private sshUserSettingsService = inject(SshUserSettingsService);
    private alertService = inject(AlertService);

    readonly documentationType: DocumentationType = 'SshSetup';

    readonly faEdit = faEdit;
    readonly faSave = faSave;
    readonly faTrash = faTrash;
    readonly faEllipsis = faEllipsis;
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;
    private dialogErrorSource = new Subject<string>();

    sshPublicKeys: UserSshPublicKey[] = [];
    keyCount = 0;
    isLoading = true;

    currentDate: dayjs.Dayjs;
    dialogError$ = this.dialogErrorSource.asObservable();

    ngOnInit() {
        this.currentDate = dayjs();
        this.refreshSshKeys();
    }

    ngOnDestroy() {
        this.dialogErrorSource.complete();
    }

    deleteSshKey(key: UserSshPublicKey) {
        this.sshUserSettingsService.deleteSshPublicKey(key.id).subscribe({
            next: () => {
                this.alertService.success('artemisApp.userSettings.sshSettingsPage.deleteSuccess');
                this.refreshSshKeys();
            },
            error: () => {
                this.alertService.error('artemisApp.userSettings.sshSettingsPage.deleteFailure');
            },
        });
        this.dialogErrorSource.next('');
    }

    private refreshSshKeys() {
        this.sshUserSettingsService
            .getSshPublicKeys()
            .pipe(
                tap((publicKeys: UserSshPublicKey[]) => {
                    this.sshPublicKeys = publicKeys;
                    this.sshUserSettingsService.sshKeys = publicKeys;
                    this.sshPublicKeys = this.sshPublicKeys.map((key) => ({
                        ...key,
                        hasExpired: key.expiryDate && dayjs().isAfter(dayjs(key.expiryDate)),
                    }));
                    this.keyCount = publicKeys.length;
                    this.isLoading = false;
                }),
            )
            .subscribe({
                error: () => {
                    this.isLoading = false;
                    this.alertService.error('artemisApp.userSettings.sshSettingsPage.loadKeyFailure');
                },
            });
    }
}
