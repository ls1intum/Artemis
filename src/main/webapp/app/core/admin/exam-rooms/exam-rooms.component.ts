import { Component } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpEvent, HttpEventType } from '@angular/common/http';

@Component({
    selector: 'app-exam-room-repository',
    templateUrl: './exam-rooms.component.html',
})
export class ExamRoomsComponent {
    selectedFile: File | null = null;
    uploading = false;
    uploadSuccess = false;
    uploadError: string | null = null;

    constructor(private http: HttpClient) {}

    onFileSelected(event: Event): void {
        const input = event.target as HTMLInputElement;
        if (input.files && input.files.length > 0) {
            const file = input.files[0];
            if (file.name.endsWith('.zip')) {
                this.selectedFile = file;
                this.uploadError = null;
            } else {
                this.uploadError = 'Please select a .zip file.';
                this.selectedFile = null;
            }
        }
    }

    upload(): void {
        if (!this.selectedFile) return;

        const formData = new FormData();
        formData.append('file', this.selectedFile);

        this.uploading = true;
        this.uploadSuccess = false;
        this.uploadError = null;

        this.http
            .post('/api/admin/exam-rooms/upload', formData, {
                reportProgress: true,
                observe: 'events',
            })
            .subscribe({
                next: (event: HttpEvent<any>) => {
                    if (event.type === HttpEventType.Response) {
                        this.uploadSuccess = true;
                        this.selectedFile = null;
                    }
                },
                error: (error: HttpErrorResponse) => {
                    this.uploading = false;
                    this.uploadSuccess = false;
                    this.uploadError = error.message;
                },
                complete: () => {
                    this.uploading = false;
                },
            });
    }
}
