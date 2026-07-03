import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { DocumentationLinkComponent } from 'app/shared-ui/components/documentation-link/documentation-link.component';
import { DateTimePickerType, FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { Subject, Subscription, concatMap, filter, tap } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { faEdit, faSave } from '@fortawesome/free-solid-svg-icons';
import { DocumentationType } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { AlertService } from 'app/foundation/service/alert.service';
import { getOS } from 'app/foundation/util/os-detector.util';
import { UserSshPublicKey } from 'app/programming/shared/entities/user-ssh-public-key.model';
import dayjs from 'dayjs/esm';
import { FormsModule } from '@angular/forms';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { SshUserSettingsService } from 'app/account/user/settings/ssh-settings/ssh-user-settings.service';

@Component({
    selector: 'jhi-account-information',
    templateUrl: './ssh-user-settings-key-details.component.html',
    styleUrls: ['../../user-settings.scss', '../ssh-user-settings.component.scss'],
    imports: [TranslateDirective, DocumentationLinkComponent, FormsModule, FormDateTimePickerComponent, ButtonComponent, ArtemisDatePipe, ArtemisTranslatePipe],
})
export class SshUserSettingsKeyDetailsComponent implements OnInit, OnDestroy {
    private sshUserSettingsService = inject(SshUserSettingsService);
    readonly route = inject(ActivatedRoute);
    readonly router = inject(Router);
    readonly alertService = inject(AlertService);

    readonly documentationType: DocumentationType = 'SshSetup';
    readonly invalidKeyFormat = 'invalidKeyFormat';
    readonly keyAlreadyExists = 'keyAlreadyExists';
    readonly keyLabelTooLong = 'keyLabelTooLong';

    protected readonly faEdit = faEdit;
    protected readonly faSave = faSave;
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;

    subscription: Subscription;

    // state change variables
    readonly isCreateMode = signal(false); // true when creating new key, false when viewing existing key
    readonly isLoading = signal(true);

    readonly copyInstructions = signal<string>('');
    selectedOption: string = 'doNotUseExpiration';

    // Key details from input fields
    displayedKeyLabel = '';
    displayedSshKey = '';
    displayedKeyHash = '';
    readonly hasExpired = signal<boolean | undefined>(false);
    displayedExpiryDate?: dayjs.Dayjs;
    readonly isExpiryDateValid = signal<boolean>(false);
    readonly displayCreationDate = signal<dayjs.Dayjs | undefined>(undefined);
    readonly displayedLastUsedDate = signal<dayjs.Dayjs | undefined>(undefined);
    readonly currentDate = signal<dayjs.Dayjs>(undefined!);

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    ngOnInit() {
        this.setMessageBasedOnOS(getOS());
        this.currentDate.set(dayjs());

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
                    this.displayedSshKey = publicKey.publicKey;
                    this.displayedKeyLabel = publicKey.label;
                    this.displayedKeyHash = publicKey.keyHash;
                    this.displayCreationDate.set(publicKey.creationDate);
                    this.displayedExpiryDate = publicKey.expiryDate;
                    this.displayedLastUsedDate.set(publicKey.lastUsedDate);
                    this.hasExpired.set(publicKey.expiryDate && dayjs().isAfter(dayjs(publicKey.expiryDate)));
                    this.isLoading.set(false);
                }),
            )
            .subscribe();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
    }

    saveSshKey() {
        const newUserSshKey: Partial<UserSshPublicKey> = {
            label: this.displayedKeyLabel,
            publicKey: this.displayedSshKey,
            expiryDate: this.displayedExpiryDate,
        };
        this.sshUserSettingsService.addNewSshPublicKey(newUserSshKey as UserSshPublicKey).subscribe({
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
        this.isExpiryDateValid.set(!!this.displayedExpiryDate?.isValid());
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
