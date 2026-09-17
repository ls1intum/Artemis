import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { Subject, tap } from 'rxjs';
import { faEllipsis, faPlus } from '@fortawesome/free-solid-svg-icons';
import { DocumentationType } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { AlertService } from 'app/foundation/service/alert.service';
import { UserSshPublicKey } from 'app/programming/shared/entities/user-ssh-public-key.model';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { RouterLink } from '@angular/router';
import {
    TumUiButtonDirective,
    TumUiListComponent,
    TumUiListItemDirective,
    TumUiMenuComponent,
    TumUiMenuItemDirective,
    TumUiMenuTriggerDirective,
    TumUiTableDirective,
} from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { DocumentationLinkComponent } from 'app/shared-ui/components/documentation-link/documentation-link.component';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { SshUserSettingsService } from 'app/account/user/settings/ssh-settings/ssh-user-settings.service';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

@Component({
    selector: 'jhi-account-information',
    templateUrl: './ssh-user-settings.component.html',
    styleUrls: ['../user-settings.scss', './ssh-user-settings.component.scss'],
    imports: [
        TranslateDirective,
        DocumentationLinkComponent,
        RouterLink,
        FaIconComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        TumUiButtonDirective,
        TumUiListComponent,
        TumUiListItemDirective,
        TumUiMenuComponent,
        TumUiMenuItemDirective,
        TumUiMenuTriggerDirective,
        TumUiTableDirective,
    ],
})
export class SshUserSettingsComponent implements OnInit, OnDestroy {
    private sshUserSettingsService = inject(SshUserSettingsService);
    private alertService = inject(AlertService);
    private deleteDialogService = inject(DeleteDialogService);

    readonly documentationType: DocumentationType = 'SshSetup';

    readonly faEllipsis = faEllipsis;
    readonly faPlus = faPlus;
    private dialogErrorSource = new Subject<string>();

    readonly sshPublicKeys = signal<UserSshPublicKey[]>([]);
    readonly keyCount = signal(0);
    readonly isLoading = signal(true);

    currentDate = dayjs();
    dialogError$ = this.dialogErrorSource.asObservable();

    ngOnInit() {
        this.currentDate = dayjs();
        this.refreshSshKeys();
    }

    ngOnDestroy() {
        this.dialogErrorSource.complete();
    }

    /**
     * Asks for confirmation before removing a key. The dialog is opened from here rather than from a
     * `jhiDeleteButton` in the row menu, because activating a menu item destroys the menu content: the
     * directive - and with it the handler the dialog calls on confirm - would be gone before the user answers.
     */
    confirmDeleteSshKey(key: UserSshPublicKey) {
        this.deleteDialogService.openDeleteDialog({
            deleteQuestion: 'artemisApp.userSettings.sshSettingsPage.deleteSshKeyQuestion',
            translateValues: {},
            actionType: ActionType.Delete,
            buttonType: ButtonType.ERROR,
            delete: () => this.deleteSshKey(key),
            dialogError: this.dialogError$,
            requireConfirmationOnlyForAdditionalChecks: false,
        });
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
                    this.sshPublicKeys.set(publicKeys.map((key) => cloneWith(key, { hasExpired: key.expiryDate && dayjs().isAfter(dayjs(key.expiryDate)) })));
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
