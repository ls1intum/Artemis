import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { lastValueFrom } from 'rxjs';
import { FILE_EXTENSIONS, MARKDOWN_FILE_EXTENSIONS } from 'app/shared/constants/file-extensions.constants';

export interface FileUploadResponse {
    path?: string;
}

type Options = {
    keepFileName: boolean;
};

@Injectable({ providedIn: 'root' })
export class FileUploaderService {
    readonly acceptedMarkdownFileExtensions = MARKDOWN_FILE_EXTENSIONS;
    readonly acceptedFileExtensions = FILE_EXTENSIONS;

    constructor(private http: HttpClient) {}

    /**
     * Uploads a generic file to the server.
     */
    uploadFile(file: Blob | File, fileName?: string, options?: Options): Promise<FileUploadResponse> {
        return this.handleFileUpload('/api/fileUpload', this.acceptedFileExtensions, file, fileName, options);
    }

    /**
     * Uploads a file for the markdown editor to the server.
     */
    uploadMarkdownFile(file: Blob | File, fileName?: string, options?: Options): Promise<FileUploadResponse> {
        return this.handleFileUpload('/api/markdown-file-upload', this.acceptedMarkdownFileExtensions, file, fileName, options);
    }

    /**
     * Uploads a file to the server. It verifies the file extensions and file size.
     * @param endpoint The API endpoint to upload the file to
     * @param allowedExtensions The allowed extensions for the file
     * @param file The file to upload
     * @param fileName The name of the file
     * @param options The options dictionary (e.g, { keepFileName: true })
     * @return A promise with the response from the server or an error
     * @private
     */
    private handleFileUpload(endpoint: string, allowedExtensions: string[], file: Blob | File, fileName?: string, options?: Options): Promise<FileUploadResponse> {
        const fileExtension = fileName ? fileName.split('.').pop()!.toLocaleLowerCase() : (file as File).name.split('.').pop()!.toLocaleLowerCase();
        if (!allowedExtensions.includes(fileExtension)) {
            return Promise.reject(
                new Error('Unsupported file type! Only the following file extensions are allowed: ' + allowedExtensions.map((extension) => `.${extension}`).join(', ')),
            );
        }

        if (file.size > MAX_FILE_SIZE) {
            return Promise.reject(new Error('File is too big! Maximum allowed file size: ' + MAX_FILE_SIZE / (1024 * 1024) + ' MB.'));
        }

        const keepFileName: boolean = !!options?.keepFileName;
        const formData = new FormData();
        formData.append('file', file, fileName);
        return lastValueFrom(this.http.post<FileUploadResponse>(endpoint + `?keepFileName=${keepFileName}`, formData));
    }

    /**
     * Duplicates file in the server.
     * @param filePath Path of the file which needs to be duplicated
     */
    async duplicateFile(filePath: string): Promise<FileUploadResponse> {
        // Get file from the server using filePath,
        const file = await lastValueFrom(this.http.get(filePath, { responseType: 'blob' }));
        // Generate a temp file name with extension. File extension is necessary as server stores only specific kind of files,
        const tempFilename = 'temp' + filePath.split('/').pop()!.split('#')[0].split('?')[0];
        const formData = new FormData();
        formData.append('file', file, tempFilename);
        // Upload the file to server. This will make a new file in the server in the temp folder
        // and will return path of the file,
        return await lastValueFrom(this.http.post<FileUploadResponse>(`/api/fileUpload?keepFileName=${false}`, formData));
    }
}
