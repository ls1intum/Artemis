import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { MAX_FILE_SIZE, MAX_FILE_SIZE_COMMUNICATION } from 'app/shared/constants/input.constants';
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
        return this.uploadFile(file, '/api/markdown-file-upload', MAX_FILE_SIZE);
    }

    /**
     * Uploads a file for the markdown editor in the current Metis conversation.
     * @param file The file to upload
     * @param courseId The course ID
     * @param conversationId The conversation ID
     * @return A promise with the response from the server or an error
     */
    uploadMarkdownFileInCurrentMetisConversation(file: File, courseId: number | undefined, conversationId: number | undefined): Promise<FileUploadResponse> {
        if (!courseId || !conversationId) {
            return Promise.reject(new Error('No course or conversation available for the file upload.'));
        }
        const url = `/api/files/courses/${courseId}/conversations/${conversationId}`;
        return this.uploadFile(file, url, MAX_FILE_SIZE_COMMUNICATION);
    }

    /**
     * Validates the file extension.
     * @param file The file to validate
     * @return true if the file extension is valid, false otherwise
     */
    private validateFileExtension(file: File): boolean {
        const fileExtension = file.name.split('.').pop()!.toLocaleLowerCase();
        return this.acceptedMarkdownFileExtensions.includes(fileExtension);
    }

    /**
     * Uploads a file to the specified URL with size validation.
     * @param file The file to upload
     * @param url The URL to upload the file to
     * @param maxSize The maximum allowed file size
     * @return A promise with the response from the server or an error
     */
    private uploadFile(file: File, url: string, maxSize: number): Promise<FileUploadResponse> {
        if (!this.validateFileExtension(file)) {
            return Promise.reject(
                new Error(`Unsupported file type! Only the following file extensions are allowed: ${this.acceptedMarkdownFileExtensions.map((ext) => `.${ext}`).join(', ')}.`),
            );
        }

        if (file.size > maxSize) {
            return Promise.reject(new Error(`File is too big! Maximum allowed file size: ${maxSize / (1024 * 1024)} MB.`));
        }

        const formData = new FormData();
        formData.append('file', file, file.name);
        return lastValueFrom(this.http.post<FileUploadResponse>(url, formData));
    }
}
