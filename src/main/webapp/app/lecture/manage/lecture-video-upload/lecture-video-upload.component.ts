import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { AlertService } from 'app/shared/service/alert.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { LectureVideo } from 'app/lecture/shared/entities/lecture-video.model';
import { faFilm, faSpinner, faTrash, faUpload } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'jhi-lecture-video-upload',
    templateUrl: './lecture-video-upload.component.html',
    styleUrls: ['./lecture-video-upload.component.scss'],
    standalone: true,
    imports: [FaIconComponent, ArtemisDatePipe, CommonModule],
})
export class LectureVideoUploadComponent {
    @Input() lectureId: number;
    @Input() existingVideo?: LectureVideo | null;
    @Output() videoUploaded = new EventEmitter<LectureVideo>();
    @Output() videoDeleted = new EventEmitter<void>();

    selectedFile = signal<File | null>(null);
    uploading = signal<boolean>(false);
    uploadProgress = signal<number>(0);
    deleting = signal<boolean>(false);

    // Icons
    faUpload = faUpload;
    faTrash = faTrash;
    faFilm = faFilm;
    faSpinner = faSpinner;

    readonly maxFileSize = 500 * 1024 * 1024; // 500 MB
    readonly acceptedFormats = '.mp4,video/mp4';

    private lectureService = inject(LectureService);
    private alertService = inject(AlertService);

    /**
     * Handles file selection from input
     */
    onFileSelected(event: Event): void {
        const input = event.target as HTMLInputElement;
        if (input.files && input.files.length > 0) {
            const file = input.files[0];

            // Validate file size
            if (file.size > this.maxFileSize) {
                this.alertService.error('artemisApp.lecture.video.fileTooLarge', { size: '500 MB' });
                this.selectedFile.set(null);
                return;
            }

            // Validate file type
            if (!this.isValidVideoFile(file)) {
                this.alertService.error('artemisApp.lecture.video.invalidFormat');
                this.selectedFile.set(null);
                return;
            }

            this.selectedFile.set(file);
        }
    }

    /**
     * Validates if the file is a valid video file
     */
    private isValidVideoFile(file: File): boolean {
        // Only MP4 allowed
        return file.type === 'video/mp4' || file.name.toLowerCase().endsWith('.mp4');
    }

    /**
     * Uploads the selected video file
     */
    uploadVideo(): void {
        const file = this.selectedFile();
        if (!file || this.uploading()) {
            return;
        }

        this.uploading.set(true);
        this.uploadProgress.set(0);

        this.lectureService.uploadVideo(this.lectureId, file).subscribe({
            next: (response: HttpResponse<LectureVideo>) => {
                if (response.body) {
                    this.alertService.success('artemisApp.lecture.video.uploadSuccess');
                    this.videoUploaded.emit(response.body);
                    this.selectedFile.set(null);
                    // Reset file input
                    const fileInput = document.getElementById('video-file-input') as HTMLInputElement;
                    if (fileInput) {
                        fileInput.value = '';
                    }
                }
                this.uploading.set(false);
            },
            error: (error: HttpErrorResponse) => {
                this.uploading.set(false);
                this.alertService.error(error?.error?.message || 'artemisApp.lecture.video.uploadError');
            },
        });
    }

    /**
     * Deletes the existing video
     */
    deleteVideo(): void {
        if (!confirm('Are you sure you want to delete this video? This action cannot be undone.')) {
            return;
        }

        this.deleting.set(true);

        this.lectureService.deleteVideo(this.lectureId).subscribe({
            next: () => {
                this.alertService.success('artemisApp.lecture.video.deleteSuccess');
                this.videoDeleted.emit();
                this.deleting.set(false);
            },
            error: (error: HttpErrorResponse) => {
                this.deleting.set(false);
                this.alertService.error(error?.error?.message || 'artemisApp.lecture.video.deleteError');
            },
        });
    }

    /**
     * Formats bytes to human readable string
     */
    formatBytes(bytes?: number): string {
        if (!bytes) return '0 Bytes';

        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));

        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    /**
     * Formats duration in seconds to HH:MM:SS
     */
    formatDuration(seconds?: number): string {
        if (!seconds) return '00:00';

        const hours = Math.floor(seconds / 3600);
        const minutes = Math.floor((seconds % 3600) / 60);
        const secs = Math.floor(seconds % 60);

        if (hours > 0) {
            return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
        }
        return `${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
    }
}
