import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { lastValueFrom } from 'rxjs';
import { UPLOAD_MARKDOWN_FILE_EXTENSIONS } from 'app/shared/constants/file-extensions.constants';

export interface FileUploadResponse {
    path?: string;
}

@Injectable({ providedIn: 'root' })
export class FileUploaderService {
    private readonly http = inject(HttpClient);
    readonly acceptedMarkdownFileExtensions = UPLOAD_MARKDOWN_FILE_EXTENSIONS;

    /**
     * Uploads a file for the markdown editor to the server.
     * @param file The file to upload
     * @return A promise with the response from the server or an error
     */
    uploadMarkdownFile(file: File): Promise<FileUploadResponse> {
        const fileExtension = file.name.split('.').pop()!.toLocaleLowerCase();
        if (!this.acceptedMarkdownFileExtensions.includes(fileExtension)) {
            return Promise.reject(
                new Error(
                    'Unsupported file type! Only the following file extensions are allowed: ' + this.acceptedMarkdownFileExtensions.map((extension) => `.${extension}`).join(', '),
                ),
            );
        }

        if (file.size > MAX_FILE_SIZE) {
            return Promise.reject(new Error('File is too big! Maximum allowed file size: ' + MAX_FILE_SIZE / (1024 * 1024) + ' MB.'));
        }

        const formData = new FormData();
        formData.append('file', file, file.name);
        return lastValueFrom(this.http.post<FileUploadResponse>(`/api/markdown-file-upload?keepFileName=${false}`, formData));
    }

    uploadMarkdownFileInCurrentMetisConversation(file: File, courseId: number | undefined, conversationId: number | undefined): Promise<FileUploadResponse> {
        if (!courseId || !conversationId) {
            return Promise.reject(new Error(`No course or conversation available for the file upload.`));
        }

        // TODO refactor
        const fileExtension = (file as File).name.split('.').pop()!.toLocaleLowerCase();
        if (!this.acceptedMarkdownFileExtensions.includes(fileExtension)) {
            return Promise.reject(
                new Error(
                    'Unsupported file type! Only the following file extensions are allowed: ' + this.acceptedMarkdownFileExtensions.map((extension) => `.${extension}`).join(', '),
                ),
            );
        }

        // TODO: adjust file size
        if (file.size > MAX_FILE_SIZE) {
            return Promise.reject(new Error('File is too big! Maximum allowed file size: ' + MAX_FILE_SIZE / (1024 * 1024) + ' MB.'));
        }

        const formData = new FormData();
        formData.append('file', file, file.name);
        return lastValueFrom(this.http.post<FileUploadResponse>(`/api/files/courses/${courseId}/conversations/${conversationId}`, formData));
    }
}
