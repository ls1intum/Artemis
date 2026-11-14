import { Component, DestroyRef, ElementRef, Signal, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { User } from 'app/account/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { ImageComponent, ImageLoadingStatus } from 'app/shared-ui/image/image.component';
import { faPencil, faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ImageCropperModalComponent } from 'app/course/manage/image-cropper-modal/image-cropper-modal.component';
import { base64StringToBlob } from 'app/foundation/util/blob-util';
import { HttpErrorResponse } from '@angular/common/http';
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
} from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AccountSettingsDialogComponent } from 'app/account/settings/account-settings-dialog.component';
import { TranslateService } from '@ngx-translate/core';

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
        ImageCropperModalComponent,
        ArtemisTranslatePipe,
        AccountSettingsDialogComponent,
    ],
})
export class AccountInformationComponent {
    private readonly accountService = inject(AccountService);
    private readonly userSettingsService = inject(UserSettingsService);
    private readonly alertService = inject(AlertService);
    private readonly confirmationService = inject(TumAetUiConfirmationService);
    private readonly translateService = inject(TranslateService);
    private readonly destroyRef = inject(DestroyRef);

    protected readonly faPen = faPencil;
    protected readonly faTrash = faTrash;
    protected readonly faPlus = faPlus;
    protected readonly addPublicFilePrefix = addPublicFilePrefix;
    protected readonly deleteProfilePictureConfirmationKey = 'deleteProfilePictureConfirmation';

    readonly currentUser: Signal<User | undefined> = this.accountService.userIdentity;
    readonly imageLoadFailed = signal(false);
    readonly accountSettingsDialogVisible = signal(false);

    private readonly fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInput');

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
                next: (user) => {
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
