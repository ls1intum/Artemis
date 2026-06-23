import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { Subject, tap } from 'rxjs';
import { faEdit, faEllipsis, faPlus, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import { DocumentationType } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { AlertService } from 'app/foundation/service/alert.service';
import { UserSshPublicKey } from 'app/programming/shared/entities/user-ssh-public-key.model';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { RouterLink } from '@angular/router';
import { NgbDropdown, NgbDropdownButtonItem, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { DocumentationLinkComponent } from 'app/shared-ui/components/documentation-link/documentation-link.component';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { SshUserSettingsService } from 'app/account/user/settings/ssh-settings/ssh-user-settings.service';

@Component({
    selector: 'jhi-account-information',
    templateUrl: './ssh-user-settings.component.html',
    styleUrls: ['../user-settings.scss', './ssh-user-settings.component.scss'],
    imports: [
        TranslateDirective,
        DocumentationLinkComponent,
        RouterLink,
        NgbDropdown,
        NgbDropdownToggle,
        FaIconComponent,
        NgbDropdownMenu,
        NgbDropdownItem,
        NgbDropdownButtonItem,
        DeleteButtonDirective,
        ArtemisDatePipe,
    ],
})
export class SshUserSettingsComponent implements OnInit, OnDestroy {
    private sshUserSettingsService = inject(SshUserSettingsService);
    private alertService = inject(AlertService);

    readonly documentationType: DocumentationType = 'SshSetup';

    readonly faEdit = faEdit;
    readonly faSave = faSave;
    readonly faTrash = faTrash;
    readonly faEllipsis = faEllipsis;
    readonly faPlus = faPlus;
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;
    private dialogErrorSource = new Subject<string>();

    readonly sshPublicKeys = signal<UserSshPublicKey[]>([]);
    readonly keyCount = signal(0);
    readonly isLoading = signal(true);

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
                    this.sshUserSettingsService.sshKeys = publicKeys;
                    this.sshPublicKeys.set(
                        publicKeys.map((key) => ({
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
