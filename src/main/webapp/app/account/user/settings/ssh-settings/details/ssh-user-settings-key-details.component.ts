import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { DocumentationLinkComponent } from 'app/shared-ui/components/documentation-link/documentation-link.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { Subject, Subscription, concatMap, filter, tap } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { faEdit, faSave } from '@fortawesome/free-solid-svg-icons';
import { DocumentationType } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import {
    TumAetUiButtonComponent,
    TumAetUiDatePickerComponent,
    TumAetUiInputDirective,
    TumAetUiListComponent,
    TumAetUiListItemDirective,
    TumAetUiRadioButtonComponent,
} from '@tumaet/ui-angular';
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
    imports: [
        TranslateDirective,
        DocumentationLinkComponent,
        FormsModule,
        TumAetUiDatePickerComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        TumAetUiButtonComponent,
        TumAetUiInputDirective,
        TumAetUiListComponent,
        TumAetUiListItemDirective,
        TumAetUiRadioButtonComponent,
    ],
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

    subscription!: Subscription; // assigned in ngOnInit(), before ngOnDestroy() unsubscribes

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
    readonly displayedExpiryDate = signal<dayjs.Dayjs | undefined>(undefined);
    readonly isExpiryDateValid = signal<boolean>(false);
    /** The picker keeps its last valid date while the typed text is not a date, so the text validity is tracked separately. */
    private readonly expiryTextValid = signal(true);
    /** The expiry date is required and must not lie in the past; while the text is not a date, the picker explains that itself. */
    protected readonly expiryDateMissingOrInvalid = computed(() => this.expiryTextValid() && !this.isExpiryDateValid());
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
                    this.displayedExpiryDate.set(publicKey.expiryDate);
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
            expiryDate: this.displayedExpiryDate(),
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
        void this.router.navigate(['/user-settings/ssh']);
    }

    onExpiryDateChange(date: dayjs.Dayjs | undefined) {
        this.displayedExpiryDate.set(date);
        this.validateExpiryDate();
    }

    onExpiryTextValidityChange(isValid: boolean) {
        this.expiryTextValid.set(isValid);
        this.validateExpiryDate();
    }

    /**
     * A valid expiry date is a date that is not before today. The picker has no minimum of its own, so a date in the past is rejected here.
     */
    validateExpiryDate() {
        const date = this.displayedExpiryDate();
        this.isExpiryDateValid.set(this.expiryTextValid() && !!date?.isValid() && !date.isBefore(this.currentDate(), 'day'));
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
}
