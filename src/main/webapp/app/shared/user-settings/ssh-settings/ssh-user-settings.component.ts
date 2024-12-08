import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { Subject, tap } from 'rxjs';
import { faEdit, faEllipsis, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { AlertService } from 'app/core/util/alert.service';
import { UserSshPublicKey } from 'app/entities/programming/user-ssh-public-key.model';
import dayjs from 'dayjs/esm';
import { SshUserSettingsService } from 'app/shared/user-settings/ssh-settings/ssh-user-settings.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RouterModule } from '@angular/router';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { DocumentationLinkComponent } from 'app/shared/components/documentation-link/documentation-link.component';

@Component({
    selector: 'jhi-account-information',
    templateUrl: './ssh-user-settings.component.html',
    standalone: true,
    styleUrls: ['../user-settings.scss', './ssh-user-settings.component.scss'],
    imports: [TranslateDirective, RouterModule, FontAwesomeModule, ArtemisSharedModule, FormDateTimePickerModule, DocumentationLinkComponent],
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

    sshPublicKeys = signal<UserSshPublicKey[]>([]);
    keyCount = signal<number>(0);
    isLoading = signal<boolean>(true);
    currentDate = signal<dayjs.Dayjs>(dayjs());

    dialogError$ = this.dialogErrorSource.asObservable();

    ngOnInit() {
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
                    this.sshPublicKeys.set(publicKeys);
                    this.sshUserSettingsService.sshKeys = publicKeys;
                    this.sshPublicKeys.set(
                        this.sshPublicKeys().map((key) => ({
                            ...key,
                            hasExpired: key.expiryDate && dayjs().isAfter(dayjs(key.expiryDate)),
                        })),
                    );
                    this.keyCount.set(publicKeys.length);
                    this.isLoading.set(false);
                }),
            )
            .subscribe({
                error: () => {
                    this.isLoading.set(false);
                    this.alertService.error('artemisApp.userSettings.sshSettingsPage.loadKeyFailure');
                },
            });
    }
}
