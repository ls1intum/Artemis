import { Component, inject, signal } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBan, faCheck, faCircleNotch, faCloudUploadAlt, faSignInAlt } from '@fortawesome/free-solid-svg-icons';
import { TumLiveAuthResponse, TumLiveCourse, TumLiveUploadService } from '../services/tum-live-upload.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { DecimalPipe } from '@angular/common';

/**
 * Modal dialog for uploading videos to TUM Live.
 * Uses SSO-based authentication - no password required!
 */
@Component({
    selector: 'jhi-tum-live-upload-dialog',
    templateUrl: './tum-live-upload-dialog.component.html',
    styleUrls: ['./tum-live-upload-dialog.component.scss'],
    imports: [FormsModule, TranslateDirective, FaIconComponent, ArtemisTranslatePipe, DecimalPipe],
})
export class TumLiveUploadDialogComponent {
    protected readonly faBan = faBan;
    protected readonly faCheck = faCheck;
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faCloudUploadAlt = faCloudUploadAlt;
    protected readonly faSignInAlt = faSignInAlt;

    private readonly activeModal = inject(NgbActiveModal);
    private readonly tumLiveUploadService = inject(TumLiveUploadService);
    private readonly alertService = inject(AlertService);

    // Authentication state
    isAuthenticating = signal(true); // Start authenticating immediately
    isAuthenticated = signal(false);
    authToken = signal<string | undefined>(undefined);
    availableCourses = signal<TumLiveCourse[]>([]);
    authError = signal<string | undefined>(undefined);

    // Upload state
    selectedCourseId = signal<number | undefined>(undefined);
    videoFile = signal<File | undefined>(undefined);
    videoTitle = '';
    videoDescription = '';
    isUploading = signal(false);
    uploadComplete = signal(false);
    uploadError = signal<string | undefined>(undefined);

    constructor() {
        // Automatically authenticate using SSO when dialog opens
        this.authenticate();
    }

    /**
     * Authenticate with TUM Live using SSO (Single Sign-On).
     * No credentials needed - user is already authenticated in Artemis!
     */
    authenticate(): void {
        this.isAuthenticating.set(true);
        this.authError.set(undefined);

        this.tumLiveUploadService.authenticate().subscribe({
            next: (response: TumLiveAuthResponse) => {
                this.isAuthenticating.set(false);
                if (response.success && response.token) {
                    this.isAuthenticated.set(true);
                    this.authToken.set(response.token);
                    this.availableCourses.set(response.courses ?? []);

                    // Auto-select first course if only one available
                    if (response.courses && response.courses.length === 1) {
                        this.selectedCourseId.set(response.courses[0].id);
                    }
                } else {
                    this.authError.set(response.error ?? 'Authentication failed');
                }
            },
            error: (error) => {
                this.isAuthenticating.set(false);
                this.authError.set(error?.error?.error ?? 'Authentication failed. Please try again.');
            },
        });
    }

    /**
     * Handle file selection from input.
     */
    onFileSelect(event: Event): void {
        const input = event.target as HTMLInputElement;
        if (input.files && input.files.length > 0) {
            const file = input.files[0];
            this.videoFile.set(file);

            // Auto-fill title from filename if not set
            if (!this.videoTitle) {
                this.videoTitle = file.name.replace(/\.[^/.]+$/, '');
            }
        }
    }

    /**
     * Upload the selected video to TUM Live.
     */
    uploadVideo(): void {
        const token = this.authToken();
        const courseId = this.selectedCourseId();
        const file = this.videoFile();

        if (!token || !courseId || !file || !this.videoTitle) {
            return;
        }

        this.isUploading.set(true);
        this.uploadError.set(undefined);

        this.tumLiveUploadService.uploadVideo(token, courseId, file, this.videoTitle, this.videoDescription).subscribe({
            next: (response) => {
                this.isUploading.set(false);
                if (response.success) {
                    this.uploadComplete.set(true);
                    this.alertService.success('artemisApp.tumLiveUpload.uploadSuccess');
                } else {
                    this.uploadError.set(response.error ?? 'Upload failed');
                }
            },
            error: (error) => {
                this.isUploading.set(false);
                this.uploadError.set(error?.error?.error ?? 'Upload failed. Please try again.');
            },
        });
    }

    /**
     * Check if upload form is valid.
     */
    isUploadFormValid(): boolean {
        return !!this.selectedCourseId() && !!this.videoFile() && !!this.videoTitle && !this.isUploading();
    }

    /**
     * Close the modal dialog.
     */
    close(): void {
        this.activeModal.dismiss();
    }

    /**
     * Close the modal with success result.
     */
    finish(): void {
        this.activeModal.close(true);
    }

    /**
     * Retry authentication.
     */
    retryAuth(): void {
        this.authError.set(undefined);
        this.authenticate();
    }
}
