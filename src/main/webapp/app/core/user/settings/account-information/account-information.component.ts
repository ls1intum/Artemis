import { Component, DestroyRef, ElementRef, Signal, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { ImageComponent, ImageLoadingStatus } from 'app/shared/image/image.component';
import { faPencil, faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ImageCropperModalComponent } from 'app/core/course/manage/image-cropper-modal/image-cropper-modal.component';
import { DialogService } from 'primeng/dynamicdialog';
import { base64StringToBlob } from 'app/shared/util/blob-util';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { addPublicFilePrefix } from 'app/app.constants';
import { UserSettingsService } from 'app/core/user/settings/directive/user-settings.service';
import { RouterLink } from '@angular/router';
import { UserSettingsTitleBarTitleDirective } from 'app/core/user/settings/shared/user-settings-title-bar-title.directive';
import { UserSettingsTitleBarActionsDirective } from 'app/core/user/settings/shared/user-settings-title-bar-actions.directive';

@Component({
    selector: 'jhi-account-information',
    templateUrl: './account-information.component.html',
    styleUrls: ['../user-settings.scss'],
    imports: [TranslateDirective, ImageComponent, FaIconComponent, ArtemisDatePipe, RouterLink, UserSettingsTitleBarTitleDirective, UserSettingsTitleBarActionsDirective],
})
export class AccountInformationComponent {
    protected readonly faPen = faPencil;
    protected readonly faTrash = faTrash;
    protected readonly faPlus = faPlus;
    protected readonly addPublicFilePrefix = addPublicFilePrefix;

    private readonly accountService = inject(AccountService);
    private readonly dialogService = inject(DialogService);
    private readonly userSettingsService = inject(UserSettingsService);
    private readonly alertService = inject(AlertService);
    private readonly destroyRef = inject(DestroyRef);

    readonly currentUser: Signal<User | undefined> = this.accountService.userIdentity;
    readonly imageLoadFailed = signal(false);

    private readonly fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInput');

    onImageLoadingStatus(status: ImageLoadingStatus): void {
        this.imageLoadFailed.set(status === ImageLoadingStatus.ERROR);
    }

    setUserImage(event: Event): void {
        const element = event.currentTarget as HTMLInputElement;
        if (element.files && element.files.length > 0) {
            const dialogRef = this.dialogService.open(ImageCropperModalComponent, {
                header: '',
                width: '500px',
                data: {
                    uploadFile: element.files[0],
                    roundCropper: false,
                    fileFormat: 'jpeg',
                },
            });
            // Use 'image/jpeg' since the cropper outputs JPEG format regardless of input
            const mimeType = 'image/jpeg';
            dialogRef?.onClose.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((result: string | undefined) => {
                if (result) {
                    const base64Data = result.replace('data:image/jpeg;base64,', '');
                    const fileToUpload = base64StringToBlob(base64Data, mimeType);
                    this.updateProfilePicture(fileToUpload);
                }
            });
        }
        element.value = '';
    }

    deleteUserImage(): void {
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
