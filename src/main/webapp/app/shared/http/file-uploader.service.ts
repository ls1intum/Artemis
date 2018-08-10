import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';

export interface FileUploadResponse {
    path: string;
}

@Injectable()
export class FileUploaderService {
    constructor(private http: HttpClient) { }

    uploadFile(file: Blob | File, fileName?: string): Promise<FileUploadResponse> {
        const formData = new FormData();
        formData.append('file', file, fileName);
        return this.http.post<FileUploadResponse>('/api/fileUpload', formData).toPromise();
    }

    /**
     * Duplicates file in the backend.
     * @param filePath Path of the file which needs to be duplicated
     */
    async duplicateFile(filePath: string): Promise<FileUploadResponse> {
        // Get file from the backend using filePath,
        const file = await this.http.get(filePath, { responseType: 'blob' }).toPromise();
        // Generate a temp file name with extension. File extension is necessary as backend stores only specific kind of files,
        const tempFilename = 'temp' + filePath.split('/').pop().split('#')[0].split('?')[0];
        const formData = new FormData();
        formData.append('file', file, tempFilename);
        // Upload the file to backend. This will make a new file in the backend in the temp folder
        // and will return path of the file,
        return await this.http.post<FileUploadResponse>('/api/fileUpload', formData).toPromise();
    }
}
