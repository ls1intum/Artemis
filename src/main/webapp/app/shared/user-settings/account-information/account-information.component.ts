import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Observable, Subscription, tap } from 'rxjs';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { faPencil, faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ImageCropperModalComponent } from 'app/course/manage/image-cropper-modal.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { base64StringToBlob } from 'app/utils/blob-util';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { AlertService, AlertType } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-account-information',
    templateUrl: './account-information.component.html',
    styleUrls: ['../user-settings.scss'],
})
export class AccountInformationComponent implements OnInit {
    currentUser?: User;
    croppedImage?: string;
    private authStateSubscription: Subscription;
    @ViewChild('fileInput', { static: false }) fileInput: ElementRef<HTMLInputElement>;

    // Icons
    faPen = faPencil;
    faTrash = faTrash;
    faPlus = faPlus;

    constructor(
        private accountService: AccountService,
        private modalService: NgbModal,
        private userSettingsService: UserSettingsService,
        private alertService: AlertService,
    ) {}

    ngOnInit() {
        this.authStateSubscription = this.accountService
            .getAuthenticationState()
            .pipe(tap((user: User) => (this.currentUser = user)))
            .subscribe();
    }

    setUserImage(event: Event) {
        const element = event.currentTarget as HTMLInputElement;
        if (element.files && element.files.length > 0) {
            const modalRef = this.modalService.open(ImageCropperModalComponent, { size: 'm' });
            modalRef.componentInstance.roundCropper = false;
            modalRef.componentInstance.fileFormat = 'jpeg';
            modalRef.componentInstance.uploadFile = element.files[0];
            const mimeType = element.files[0].type;
            modalRef.result.then((result: any) => {
                if (result) {
                    const base64Data = result.replace('data:image/jpeg;base64,', '');
                    const fileToUpload = base64StringToBlob(base64Data, mimeType);
                    this.subscribeToUpdateProfilePictureResponse(this.userSettingsService.updateProfilePicture(fileToUpload));
                }
            });
        }
        element.value = '';
    }

    deleteUserImage() {
        this.subscribeToRemoveProfilePictureResponse(this.userSettingsService.removeProfilePicture());
    }

    triggerUserImageFileInput() {
        this.fileInput.nativeElement.click();
    }

    private subscribeToUpdateProfilePictureResponse(result: Observable<HttpResponse<User>>) {
        result.subscribe({
            next: (response: HttpResponse<User>) => this.onProfilePictureUploadSuccess(response.body),
            error: (res: HttpErrorResponse) => this.onProfilePictureUploadError(res),
        });
    }

    private subscribeToRemoveProfilePictureResponse(result: Observable<HttpResponse<User>>) {
        result.subscribe({
            next: () => this.onProfilePictureRemoveSuccess(),
            error: (res: HttpErrorResponse) => this.onProfilePictureRemoveError(res),
        });
    }

    private onProfilePictureUploadSuccess(user: User | null) {
        if (user !== null && user.imageUrl !== undefined) {
            this.currentUser!.imageUrl = user.imageUrl;
            this.accountService.setImageUrl(user.imageUrl);
        }
    }

    private onProfilePictureUploadError(error: HttpErrorResponse) {
        const errorMessage = error.error ? error.error.title : error.headers?.get('x-artemisapp-alert');
        if (errorMessage) {
            this.alertService.addAlert({
                type: AlertType.DANGER,
                message: errorMessage,
                disableTranslation: true,
            });
        }
    }

    private onProfilePictureRemoveSuccess() {
        this.currentUser!.imageUrl = undefined;
        this.accountService.setImageUrl(undefined);
    }

    private onProfilePictureRemoveError(error: HttpErrorResponse) {
        const errorMessage = error.error ? error.error.title : error.headers?.get('x-artemisapp-alert');
        if (errorMessage) {
            this.alertService.addAlert({
                type: AlertType.DANGER,
                message: errorMessage,
                disableTranslation: true,
            });
        }
    }

    protected readonly CachingStrategy = CachingStrategy;
}
