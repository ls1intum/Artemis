import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { Subscription, concatMap, filter, tap } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { faEdit, faSave } from '@fortawesome/free-solid-svg-icons';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { AlertService } from 'app/core/util/alert.service';
import { getOS } from 'app/shared/util/os-detector.util';
import { UserSshPublicKey } from 'app/entities/programming/user-ssh-public-key.model';
import dayjs from 'dayjs/esm';
import { SshUserSettingsService } from 'app/shared/user-settings/ssh-settings/ssh-user-settings.service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { DocumentationLinkComponent } from 'app/shared/components/documentation-link/documentation-link.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DateTimePickerType } from 'app/shared/date-time-picker/date-time-picker.component';

@Component({
    selector: 'jhi-account-information',
    standalone: true,
    templateUrl: './ssh-user-settings-key-details.component.html',
    styleUrls: ['../../user-settings.scss', '../ssh-user-settings.component.scss'],
    imports: [TranslateDirective, FontAwesomeModule, ArtemisSharedModule, ArtemisSharedComponentModule, FormDateTimePickerModule, DocumentationLinkComponent],
})
export class SshUserSettingsKeyDetailsComponent implements OnInit, OnDestroy {
    private sshUserSettingsService = inject(SshUserSettingsService);
    readonly route = inject(ActivatedRoute);
    readonly router = inject(Router);
    readonly alertService = inject(AlertService);

    protected readonly documentationType: DocumentationType = 'SshSetup';
    protected readonly invalidKeyFormat = 'invalidKeyFormat';
    protected readonly keyAlreadyExists = 'keyAlreadyExists';
    protected readonly keyLabelTooLong = 'keyLabelTooLong';

    protected readonly faEdit = faEdit;
    protected readonly faSave = faSave;
    protected readonly ButtonType = ButtonType;

    protected readonly ButtonSize = ButtonSize;

    subscription: Subscription;
    // state change variables
    protected isCreateMode = signal<boolean>(false); // true when creating new key, false when viewing existing key

    isLoading = signal<boolean>(true);
    copyInstructions = signal<string>('');

    // Key details from input fields
    displayedKeyLabel = signal<string>('');
    displayedSshKey = signal<string>('');
    displayedKeyHash = signal<string>('');
    displayedExpiryDate = signal<dayjs.Dayjs | undefined>(undefined);
    isExpiryDateValid = signal<boolean>(false);
    protected displayCreationDate = signal<dayjs.Dayjs>(dayjs());
    protected displayedLastUsedDate = signal<dayjs.Dayjs | undefined>(undefined);
    protected currentDate = signal<dayjs.Dayjs>(dayjs());
    protected selectedOption = signal<string>('doNotUseExpiration');

    ngOnInit() {
        this.setMessageBasedOnOS(getOS());

        this.subscription = this.route.params
            .pipe(
                filter((params) => {
                    const keyId = Number(params['keyId']);
                    if (keyId) {
                        this.isCreateMode.set(false);
                        return true;
                    } else {
                        this.isLoading.set(false);
                        this.isCreateMode.set(true);
                        return false;
                    }
                }),
                concatMap((params) => {
                    return this.sshUserSettingsService.getSshPublicKey(Number(params['keyId']));
                }),
                tap((publicKey: UserSshPublicKey) => {
                    this.displayedSshKey.set(publicKey.publicKey);
                    this.displayedKeyLabel.set(publicKey.label);
                    this.displayedKeyHash.set(publicKey.keyHash);
                    this.displayCreationDate.set(publicKey.creationDate);
                    this.displayedExpiryDate.set(publicKey.expiryDate);
                    this.displayedLastUsedDate.set(publicKey.lastUsedDate);
                    this.isLoading.set(false);
                }),
            )
            .subscribe();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
    }

    saveSshKey() {
        const newUserSshKey = {
            label: this.displayedKeyLabel(),
            publicKey: this.displayedSshKey(),
            expiryDate: this.displayedExpiryDate(),
        } as UserSshPublicKey;
        this.sshUserSettingsService.addNewSshPublicKey(newUserSshKey).subscribe({
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
        this.router.navigate(['/user-settings/ssh']);
    }

    validateExpiryDate() {
        this.isExpiryDateValid.set(!!this.displayedExpiryDate()?.isValid());
    }

    private setMessageBasedOnOS(os: string): void {
        switch (os) {
            case 'Windows':
                this.copyInstructions.set('cat ~/.ssh/id_ed25519.pub | clip');
                break;
            case 'MacOS':
                this.copyInstructions.set('pbcopy < ~/.ssh/id_ed25519.pub');
                break;
            case 'Linux':
                this.copyInstructions.set('xclip -selection clipboard < ~/.ssh/id_ed25519.pub');
                break;
            case 'Android':
                this.copyInstructions.set('termux-clipboard-set < ~/.ssh/id_ed25519.pub');
                break;
            default:
                this.copyInstructions.set('Ctrl + C');
        }
    }

    protected readonly DateTimePickerType = DateTimePickerType;
}
