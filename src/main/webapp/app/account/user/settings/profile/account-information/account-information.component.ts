import { Component, DestroyRef, ElementRef, OnInit, Signal, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { User } from 'app/account/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { ImageComponent, ImageLoadingStatus } from 'app/shared-ui/image/image.component';
import { faPencil, faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ImageCropperModalComponent } from 'app/course/manage/image-cropper-modal/image-cropper-modal.component';
import { base64StringToBlob } from 'app/foundation/util/blob-util';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService, AlertType } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { addPublicFilePrefix } from 'app/app.constants';
import { UserSettingsService } from 'app/account/user/settings/directive/user-settings.service';
import {
    TumAetUiButtonComponent,
    TumAetUiConfirmDialogComponent,
    TumAetUiConfirmationService,
    TumAetUiDialogComponent,
    TumAetUiListComponent,
    TumAetUiListItemDirective,
    TumAetUiMessageComponent,
} from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AccountSettingsDialogComponent } from 'app/account/settings/account-settings-dialog.component';
import { TranslateService } from '@ngx-translate/core';
import { MODULE_FEATURE_SAML2 } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { PendingEmailChangeDTO } from 'app/account/user/shared/dto/pending-email-change.dto';

/** Error keys of the server that are shown as their own message. All other failures show a generic message. */
const EMAIL_CHANGE_ERROR_KEYS = ['emailInvalid', 'emailChangeUnavailable', 'emailChangeRequestedTooRecently', 'noPendingEmailChange'];
const GENERIC_ERROR_MESSAGE_KEY = 'settings.messages.error.fail';

@Component({
    selector: 'jhi-account-information',
    templateUrl: './account-information.component.html',
    styleUrls: ['../../user-settings.scss'],
    providers: [TumAetUiConfirmationService],
    imports: [
        TranslateDirective,
        ImageComponent,
        ArtemisDatePipe,
        TumAetUiButtonComponent,
        TumAetUiConfirmDialogComponent,
        TumAetUiListComponent,
        TumAetUiListItemDirective,
        TumAetUiDialogComponent,
        TumAetUiMessageComponent,
        ImageCropperModalComponent,
        ArtemisTranslatePipe,
        AccountSettingsDialogComponent,
    ],
})
export class AccountInformationComponent implements OnInit {
    private readonly accountService = inject(AccountService);
    private readonly userSettingsService = inject(UserSettingsService);
    private readonly alertService = inject(AlertService);
    private readonly confirmationService = inject(TumAetUiConfirmationService);
    private readonly translateService = inject(TranslateService);
    private readonly profileService = inject(ProfileService);
    private readonly destroyRef = inject(DestroyRef);

    protected readonly faPen = faPencil;
    protected readonly faTrash = faTrash;
    protected readonly faPlus = faPlus;
    protected readonly addPublicFilePrefix = addPublicFilePrefix;
    protected readonly deleteProfilePictureConfirmationKey = 'deleteProfilePictureConfirmation';

    readonly currentUser: Signal<User | undefined> = this.accountService.userIdentity;
    readonly imageLoadFailed = signal(false);
    readonly accountSettingsDialogVisible = signal(false);
    /** The request to change the email address that waits for confirmation, undefined when there is none */
    readonly pendingEmailChange = signal<PendingEmailChangeDTO | undefined>(undefined);
    /** Indicates a save created or replaced an email change request, so confirmation mails were sent */
    readonly emailChangeRequested = signal(false);
    /** Indicates the confirmation mails of the pending email change were sent again */
    readonly confirmationMailsResent = signal(false);
    /** Indicates the pending email change was cancelled */
    readonly emailChangeCancelled = signal(false);
    /** Indicates a resend or cancel request of the pending email change is running */
    readonly pendingChangeRequestRunning = signal(false);
    /** Translation key of the error of the last resend or cancel request, undefined when there is none */
    readonly errorMessageKey = signal<string | undefined>(undefined);
    /** Whether the server can send emails. Without it an email change cannot be confirmed. */
    readonly mailConfigured = this.profileService.getProfileInfo().mailConfigured ?? true;

    private readonly fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInput');

    ngOnInit(): void {
        // Email changes are confirmed by mail, so only internal accounts that are not synchronized through SAML2 can have a pending request
        void this.accountService.identity().then((user) => {
            if (user?.internal && !this.profileService.isModuleFeatureActive(MODULE_FEATURE_SAML2)) {
                this.accountService
                    .getPendingEmailChange()
                    .pipe(takeUntilDestroyed(this.destroyRef))
                    .subscribe((pendingEmailChange) => this.pendingEmailChange.set(pendingEmailChange));
            }
        });
    }

    /**
     * Shows the request that a save of the account settings created or replaced.
     * @param pendingEmailChange the pending request returned by the server
     */
    onEmailChangeRequested(pendingEmailChange: PendingEmailChangeDTO): void {
        this.resetMessages();
        this.pendingEmailChange.set(pendingEmailChange);
        this.emailChangeRequested.set(true);
    }

    /**
     * Sends the confirmation links of the pending email change to both addresses again.
     * Earlier confirmations are reset and the validity of the request restarts.
     */
    resendConfirmationMails(): void {
        this.resetMessages();
        this.pendingChangeRequestRunning.set(true);
        this.accountService
            .resendPendingEmailChange()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (pendingEmailChange) => {
                    this.pendingChangeRequestRunning.set(false);
                    this.pendingEmailChange.set(pendingEmailChange);
                    this.confirmationMailsResent.set(true);
                },
                error: (response) => {
                    this.pendingChangeRequestRunning.set(false);
                    const errorKey = response.error?.errorKey;
                    const known = response.status === 400 && errorKey && EMAIL_CHANGE_ERROR_KEYS.includes(errorKey);
                    this.errorMessageKey.set(known ? `settings.messages.error.${errorKey}` : GENERIC_ERROR_MESSAGE_KEY);
                },
            });
    }

    /**
     * Cancels the pending email change. The email address of the account was never changed, so nothing else is reverted.
     */
    cancelEmailChange(): void {
        this.resetMessages();
        this.pendingChangeRequestRunning.set(true);
        this.accountService
            .cancelPendingEmailChange()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => {
                    this.pendingChangeRequestRunning.set(false);
                    this.pendingEmailChange.set(undefined);
                    this.emailChangeCancelled.set(true);
                },
                error: () => {
                    this.pendingChangeRequestRunning.set(false);
                    this.errorMessageKey.set(GENERIC_ERROR_MESSAGE_KEY);
                },
            });
    }

    /** Clears feedback from the previous email-change action before starting the next one. */
    private resetMessages(): void {
        this.errorMessageKey.set(undefined);
        this.emailChangeRequested.set(false);
        this.confirmationMailsResent.set(false);
        this.emailChangeCancelled.set(false);
    }

    onImageLoadingStatus(status: ImageLoadingStatus): void {
        this.imageLoadFailed.set(status === ImageLoadingStatus.ERROR);
    }

    /** File the cropper dialog is currently working on; the dialog is shown while it is set. */
    readonly imageToCrop = signal<File | undefined>(undefined);

    setUserImage(event: Event): void {
        const element = event.currentTarget as HTMLInputElement;
        if (element.files && element.files.length > 0) {
            this.imageToCrop.set(element.files[0]);
        }
        element.value = '';
    }

    /** The cropper hands back a data URL; the picture is uploaded from its payload. */
    onImageCropped(croppedImage: string): void {
        this.imageToCrop.set(undefined);
        // Use 'image/jpeg' since the cropper outputs JPEG format regardless of input
        const mimeType = 'image/jpeg';
        const base64Data = croppedImage.replace('data:image/jpeg;base64,', '');
        this.updateProfilePicture(base64StringToBlob(base64Data, mimeType));
    }

    confirmDeleteUserImage(): void {
        this.confirmationService.confirm({
            key: this.deleteProfilePictureConfirmationKey,
            header: this.translateService.instant('artemisApp.userSettings.accountInformationPage.deleteProfilePictureTitle'),
            message: this.translateService.instant('artemisApp.userSettings.accountInformationPage.deleteProfilePictureQuestion'),
            acceptLabel: this.translateService.instant('entity.action.delete'),
            rejectLabel: this.translateService.instant('entity.action.cancel'),
            acceptSeverity: 'danger',
            accept: () => this.deleteUserImage(),
        });
    }

    private deleteUserImage(): void {
        this.userSettingsService
            .removeProfilePicture()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => this.accountService.setImageUrl(undefined),
                error: (error: HttpErrorResponse) => this.showErrorAlert(error),
            });
    }

    triggerUserImageFileInput(): void {
        this.fileInput().nativeElement.click();
    }

    private updateProfilePicture(file: Blob): void {
        this.userSettingsService
            .updateProfilePicture(file)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (response: HttpResponse<User>) => {
                    const user = response.body;
                    if (user?.imageUrl !== undefined) {
                        this.imageLoadFailed.set(false);
                        this.accountService.setImageUrl(user.imageUrl);
                    }
                },
                error: (error: HttpErrorResponse) => this.showErrorAlert(error),
            });
    }

    private showErrorAlert(error: HttpErrorResponse): void {
        const errorMessage = error.error?.title ?? error.headers?.get('x-artemisapp-alert');
        if (errorMessage) {
            this.alertService.addAlert({
                type: AlertType.DANGER,
                message: errorMessage,
                disableTranslation: true,
            });
        }
    }
}
