import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { Subject, Subscription, concatMap, filter, tap } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { faEdit, faSave } from '@fortawesome/free-solid-svg-icons';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { AlertService } from 'app/core/util/alert.service';
import { getOS } from 'app/shared/util/os-detector.util';
import { UserSshPublicKey } from 'app/entities/programming/user-ssh-public-key.model';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-account-information',
    templateUrl: './ssh-user-settings-key-details.component.html',
    styleUrls: ['../../user-settings.scss', '../ssh-user-settings.component.scss'],
})
export class SshUserSettingsKeyDetailsComponent implements OnInit, OnDestroy {
    readonly accountService = inject(AccountService);
    readonly route = inject(ActivatedRoute);
    readonly router = inject(Router);
    readonly alertService = inject(AlertService);

    readonly documentationType: DocumentationType = 'SshSetup';
    readonly invalidKeyFormat = 'invalidKeyFormat';
    readonly keyAlreadyExists = 'keyAlreadyExists';
    readonly keyLabelTooLong = 'keyLabelTooLong';

    readonly faEdit = faEdit;
    readonly faSave = faSave;
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;

    subscription: Subscription;

    // state change variables
    isCreateMode = false; // true when creating new key, false when viewing existing key
    isLoading = true;

    copyInstructions = '';
    selectedOption: string = 'doNotUseExpiration';

    // Key details from input fields
    displayedKeyLabel = '';
    displayedSshKey = '';
    displayedKeyHash = '';
    displayedExpiryDate?: dayjs.Dayjs;
    isExpiryDateValid = false;
    displayCreationDate: dayjs.Dayjs;
    displayedLastUsedDate?: dayjs.Dayjs;
    currentDate: dayjs.Dayjs;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    ngOnInit() {
        this.setMessageBasedOnOS(getOS());
        this.currentDate = dayjs();

        this.subscription = this.route.params
            .pipe(
                filter((params) => {
                    const keyId = Number(params['keyId']);
                    if (keyId) {
                        this.isCreateMode = false;
                        return true;
                    } else {
                        this.isLoading = false;
                        this.isCreateMode = true;
                        return false;
                    }
                }),
                concatMap((params) => {
                    return this.accountService.getSshPublicKey(Number(params['keyId']));
                }),
                tap((publicKey: UserSshPublicKey) => {
                    this.displayedSshKey = publicKey.publicKey;
                    this.displayedKeyLabel = publicKey.label;
                    this.displayedKeyHash = publicKey.keyHash;
                    this.displayCreationDate = publicKey.creationDate;
                    this.displayedExpiryDate = publicKey.expiryDate;
                    this.displayedLastUsedDate = publicKey.lastUsedDate;
                    this.isLoading = false;
                }),
            )
            .subscribe();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
    }

    saveSshKey() {
        const newUserSshKey = {
            label: this.displayedKeyLabel,
            publicKey: this.displayedSshKey,
            expiryDate: this.displayedExpiryDate,
        } as UserSshPublicKey;
        this.accountService.addNewSshPublicKey(newUserSshKey).subscribe({
            next: () => {
                this.alertService.success('artemisApp.userSettings.sshSettingsPage.saveSuccess');
                this.goBack();
            },
            error: (error) => {
                const errorKey = error.error.errorKey;
                if ([this.invalidKeyFormat, this.keyAlreadyExists, this.keyLabelTooLong].indexOf(errorKey) > -1) {
                    this.alertService.error(`artemisApp.userSettings.sshSettingsPage.${errorKey}`);
                } else {
                    this.alertService.error('artemisApp.userSettings.sshSettingsPage.saveFailure');
                }
            },
        });
    }

    goBack() {
        if (this.isCreateMode) {
            this.router.navigate(['../'], { relativeTo: this.route });
        } else {
            this.router.navigate(['../../'], { relativeTo: this.route });
        }
    }

    validateExpiryDate() {
        this.isExpiryDateValid = !!this.displayedExpiryDate?.isValid();
    }

    private setMessageBasedOnOS(os: string): void {
        switch (os) {
            case 'Windows':
                this.copyInstructions = 'cat ~/.ssh/id_ed25519.pub | clip';
                break;
            case 'MacOS':
                this.copyInstructions = 'pbcopy < ~/.ssh/id_ed25519.pub';
                break;
            case 'Linux':
                this.copyInstructions = 'xclip -selection clipboard < ~/.ssh/id_ed25519.pub';
                break;
            case 'Android':
                this.copyInstructions = 'termux-clipboard-set < ~/.ssh/id_ed25519.pub';
                break;
            default:
                this.copyInstructions = 'Ctrl + C';
        }
    }
}
