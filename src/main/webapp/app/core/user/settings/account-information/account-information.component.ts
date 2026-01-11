import { Component, ElementRef, Signal, inject, viewChild } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { ImageComponent } from 'app/shared/image/image.component';
import { faPencil, faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ImageCropperModalComponent } from 'app/core/course/manage/image-cropper-modal/image-cropper-modal.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { base64StringToBlob } from 'app/shared/util/blob-util';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { addPublicFilePrefix } from 'app/app.constants';
import { UserSettingsService } from 'app/core/user/settings/directive/user-settings.service';
import { RouterLink } from '@angular/router';

@Component({
    selector: 'jhi-account-information',
    templateUrl: './account-information.component.html',
    styleUrls: ['../user-settings.scss'],
    imports: [TranslateDirective, ImageComponent, FaIconComponent, ArtemisDatePipe, RouterLink],
})
export class AccountInformationComponent {
    protected readonly faPen = faPencil;
    protected readonly faTrash = faTrash;
    protected readonly faPlus = faPlus;
    protected readonly addPublicFilePrefix = addPublicFilePrefix;

    private readonly accountService = inject(AccountService);
    private readonly modalService = inject(NgbModal);
    private readonly userSettingsService = inject(UserSettingsService);
    private readonly alertService = inject(AlertService);

    readonly currentUser: Signal<User | undefined> = this.accountService.userIdentity;

    private readonly fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInput');

    setUserImage(event: Event): void {
        const element = event.currentTarget as HTMLInputElement;
        if (element.files && element.files.length > 0) {
            const modalRef = this.modalService.open(ImageCropperModalComponent, { size: 'm' });
            modalRef.componentInstance.roundCropper = false;
            modalRef.componentInstance.fileFormat = 'jpeg';
            modalRef.componentInstance.uploadFile = element.files[0];
            const mimeType = element.files[0].type;
            modalRef.result.then((result: string) => {
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
        this.userSettingsService.removeProfilePicture().subscribe({
            next: () => this.accountService.setImageUrl(undefined),
            error: (error: HttpErrorResponse) => this.showErrorAlert(error),
        });
    }

    triggerUserImageFileInput(): void {
        this.fileInput().nativeElement.click();
    }

    private updateProfilePicture(file: Blob): void {
        this.userSettingsService.updateProfilePicture(file).subscribe({
            next: (response: HttpResponse<User>) => {
                const user = response.body;
                if (user?.imageUrl !== undefined) {
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
