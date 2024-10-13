import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { lastValueFrom } from 'rxjs';
import { UPLOAD_MARKDOWN_FILE_EXTENSIONS } from 'app/shared/constants/file-extensions.constants';

export interface FileUploadResponse {
    path?: string;
}

type Options = {
    keepFileName: boolean;
};

@Injectable({ providedIn: 'root' })
export class FileUploaderService {
    private http = inject(HttpClient);

    readonly acceptedMarkdownFileExtensions = UPLOAD_MARKDOWN_FILE_EXTENSIONS;

    /**
     * Uploads a file for the markdown editor to the server.
     * @param file The file to upload
     * @param fileName The name of the file
     * @param options The options dictionary (e.g, { keepFileName: true })
     * @return A promise with the response from the server or an error
     */
    uploadMarkdownFile(file: Blob | File, fileName?: string, options?: Options): Promise<FileUploadResponse> {
        const fileExtension = fileName ? fileName.split('.').pop()!.toLocaleLowerCase() : (file as File).name.split('.').pop()!.toLocaleLowerCase();
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

        const keepFileName = !!options?.keepFileName;
        const formData = new FormData();
        formData.append('file', file, fileName);
        return lastValueFrom(this.http.post<FileUploadResponse>(`/api/markdown-file-upload?keepFileName=${keepFileName}`, formData));
    }
}
